package br.pucrs.pulsepass.iam;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SessionStoreIT {
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.6-alpine")).withExposedPorts(6379);
    static LettuceConnectionFactory connection;
    static StringRedisTemplate template;
    static SessionStore store;
    @BeforeAll static void setup() {
        connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379)); connection.afterPropertiesSet();
        template = new StringRedisTemplate(connection);
        store = new SessionStore(template, JsonMapper.builder().addModule(new JavaTimeModule()).build());
    }
    @AfterAll static void close() { if (connection != null) connection.destroy(); }
    @Test void loginStateIsAtomicallyConsumedOnlyOnce() throws Exception {
        String id = SessionController.random();
        store.saveLogin(id, new SessionStore.Login("verifier", "nonce", "browser", "/"));
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, 12).mapToObj(i -> pool.submit(() -> store.consumeLogin(id) != null)).toList();
            int accepted = 0; for (var result : results) if (result.get()) accepted++;
            assertEquals(1, accepted);
        }
    }
    @Test void storesOnlyEncryptedRefreshWithTtlAndRevokes() {
        TokenCipher cipher = new TokenCipher(SessionSecurityTest.properties());
        String id = SessionController.random();
        var session = new SessionStore.Session("access", cipher.encrypt("private-refresh-token"), Instant.now().plusSeconds(60), Instant.now().plusSeconds(300), "csrf", new SessionStore.User("owner", "Cliente", List.of("Cliente")));
        store.save(id, session);
        assertEquals("private-refresh-token", cipher.decrypt(store.get(id).encryptedRefreshToken()));
        assertFalse(template.opsForValue().get("iam:session:" + id).contains("private-refresh-token"));
        assertTrue(template.getExpire("iam:session:" + id) > 0);
        String lease = store.tryLock(id); assertNotNull(lease); assertNull(store.tryLock(id));
        store.unlock(id, "incorrect-owner"); assertNull(store.tryLock(id));
        store.unlock(id, lease); assertNotNull(store.tryLock(id));
        store.delete(id); assertNull(store.get(id));
        assertFalse(store.replaceIfPresent(id, session));
        assertNull(store.get(id));
    }
    @Test void expiredSessionCannotBeResurrected() {
        String id = SessionController.random();
        store.save(id, new SessionStore.Session("a", "r", Instant.now(), Instant.now().minusSeconds(1), "c", new SessionStore.User("u", "U", List.of())));
        assertNull(store.get(id));
    }
    @Test void limitsLoginAttempts() { String ip = SessionController.random(); for(int i = 0; i < 30; i++) assertTrue(store.permitLogin(ip)); assertFalse(store.permitLogin(ip)); }
}
