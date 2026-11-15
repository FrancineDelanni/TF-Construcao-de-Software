package br.pucrs.pulsepass.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {
    public record Login(String verifier, String nonce, String browserSecret, String returnTo) {}
    public record User(String sub, String name, List<String> roles) {}
    public record Session(String accessToken, String encryptedRefreshToken, Instant expiresAt,
                          Instant sessionExpiresAt, String csrfToken, User user) {}
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public SessionStore(StringRedisTemplate redis, ObjectMapper json) { this.redis = redis; this.json = json; }
    public void saveLogin(String state, Login login) { put("iam:login:" + state, login, Duration.ofMinutes(5)); }
    public Login consumeLogin(String state) { return read(redis.opsForValue().getAndDelete("iam:login:" + state), Login.class); }
    public void save(String id, Session session) {
        Duration ttl = Duration.between(Instant.now(), session.sessionExpiresAt());
        if (ttl.isNegative() || ttl.isZero()) { delete(id); return; }
        put("iam:session:" + id, session, ttl);
    }
    public boolean replaceIfPresent(String id, Session session) {
        long ttl = Duration.between(Instant.now(), session.sessionExpiresAt()).toSeconds();
        if (ttl <= 0) return false;
        try {
            var script = new DefaultRedisScript<Long>("if redis.call('EXISTS',KEYS[1]) == 1 then redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[2]); return 1 else return 0 end", Long.class);
            Long replaced = redis.execute(script, List.of("iam:session:" + id), json.writeValueAsString(session), Long.toString(ttl));
            return Long.valueOf(1).equals(replaced);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Session serialization failed"); }
    }
    public Session get(String id) { return id == null ? null : read(redis.opsForValue().get("iam:session:" + id), Session.class); }
    public void delete(String id) { if (id != null) redis.delete("iam:session:" + id); }
    public String tryLock(String id) {
        String lease = SessionController.random();
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("iam:refresh:" + id, lease, Duration.ofSeconds(60))) ? lease : null;
    }
    public void unlock(String id, String lease) {
        var script = new DefaultRedisScript<Long>("if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end", Long.class);
        redis.execute(script, List.of("iam:refresh:" + id), lease);
    }
    public boolean permitLogin(String ip) {
        String key = "iam:login-rate:" + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(5));
        return count != null && count <= 30;
    }
    private void put(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, json.writeValueAsString(value), ttl); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Session serialization failed"); }
    }
    private <T> T read(String value, Class<T> type) {
        if (value == null) return null;
        try { return json.readValue(value, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Session data invalid"); }
    }
}
