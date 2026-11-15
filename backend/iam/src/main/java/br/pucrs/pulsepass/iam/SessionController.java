package br.pucrs.pulsepass.iam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/iam")
public class SessionController {
    private static final String SESSION = "PULSEPASS_SESSION", LOGIN = "PULSEPASS_LOGIN";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IamProperties config;
    private final SessionStore store;
    private final TokenCipher cipher;
    private final OidcClient oidc;
    private final JwtDecoder decoder;
    public SessionController(IamProperties config, SessionStore store, TokenCipher cipher, OidcClient oidc, JwtDecoder decoder) {
        this.config = config; this.store = store; this.cipher = cipher; this.oidc = oidc; this.decoder = decoder;
    }
    @GetMapping("/login")
    public void login(@RequestParam(defaultValue="/") String returnTo, HttpServletRequest request, HttpServletResponse response) throws Exception {
        noCache(response);
        if (!store.permitLogin(request.getRemoteAddr())) throw new IamException(429, "Muitas tentativas. Aguarde cinco minutos.");
        String state = random(), verifier = random(), nonce = random(), browserSecret = random();
        store.saveLogin(state, new SessionStore.Login(verifier, nonce, browserSecret, safeReturnTo(returnTo)));
        cookie(response, LOGIN, browserSecret, Duration.ofMinutes(5));
        String url = UriComponentsBuilder.fromUriString(config.issuer() + "/protocol/openid-connect/auth")
            .queryParam("client_id", config.clientId()).queryParam("redirect_uri", config.callback())
            .queryParam("response_type", "code").queryParam("scope", "openid profile email")
            .queryParam("state", state).queryParam("nonce", nonce).queryParam("code_challenge", challenge(verifier))
            .queryParam("code_challenge_method", "S256").build().encode().toUriString();
        response.sendRedirect(url);
    }
    @GetMapping("/callback")
    public void callback(@RequestParam(required=false) String code, @RequestParam(required=false) String state,
                         @CookieValue(name=LOGIN, required=false) String browserSecret,
                         @CookieValue(name=SESSION, required=false) String oldSession,
                         HttpServletResponse response) throws Exception {
        noCache(response); cookie(response, LOGIN, "", Duration.ZERO);
        SessionStore.Login login = state == null || state.length() > 128 ? null : store.consumeLogin(state);
        if (login == null || !equal(login.browserSecret(), browserSecret) || code == null) {
            response.sendRedirect(config.publicUrl() + "/?login=failed"); return;
        }
        String stage = "code-exchange";
        try {
            OidcClient.Tokens tokens = oidc.exchange(code, login.verifier());
            stage = "identity-validation";
            Jwt identity = decoder.decode(tokens.id_token());
            if (!identity.getAudience().contains(config.clientId()) || !equal(login.nonce(), identity.getClaimAsString("nonce")))
                throw new IamException(401, "Identidade inválida.");
            stage = "access-validation";
            SessionStore.Session session = session(tokens, random());
            if (!equal(identity.getSubject(), session.user().sub())) throw new IamException(401, "Identidade inválida.");
            stage = "session-save";
            String id = random(); store.delete(oldSession); store.save(id, session);
            cookie(response, SESSION, id, Duration.between(Instant.now(), session.sessionExpiresAt()));
            response.sendRedirect(config.publicUrl() + login.returnTo());
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger(SessionController.class).warn("Callback OIDC recusado na etapa {} ({})", stage, error.getClass().getSimpleName());
            response.sendRedirect(config.publicUrl() + "/?login=failed");
        }
    }
    @GetMapping("/session")
    public Object current(@CookieValue(name=SESSION, required=false) String id, HttpServletResponse response) {
        noCache(response); SessionStore.Session session = store.get(id);
        if (session == null) return Map.of("authenticated", false);
        return view(session);
    }
    @PostMapping("/refresh")
    public Object refresh(@CookieValue(name=SESSION, required=false) String id, HttpServletRequest request, HttpServletResponse response) {
        noCache(response); protect(request, requireSession(id));
        String lease = store.tryLock(id);
        if (lease == null) throw new IamException(409, "Renovação em andamento. Consulte a sessão novamente.");
        try {
            SessionStore.Session previous = requireSession(id);
            OidcClient.Tokens tokens = oidc.refresh(cipher.decrypt(previous.encryptedRefreshToken()));
            SessionStore.Session updated = session(tokens, previous.csrfToken());
            if (!equal(previous.user().sub(), updated.user().sub())) throw new IamException(401, "Identidade inválida.");
            if (!store.replaceIfPresent(id, updated)) throw new IamException(401, "Sessão encerrada. Entre novamente.");
            cookie(response, SESSION, id, Duration.between(Instant.now(), updated.sessionExpiresAt()));
            return view(updated);
        } catch (IamException error) { store.delete(id); cookie(response, SESSION, "", Duration.ZERO); throw error; }
        finally { store.unlock(id, lease); }
    }
    @PostMapping("/logout")
    public Object logout(@CookieValue(name=SESSION, required=false) String id, HttpServletRequest request, HttpServletResponse response) {
        noCache(response); SessionStore.Session session = requireSession(id); protect(request, session);
        store.delete(id); cookie(response, SESSION, "", Duration.ZERO);
        oidc.logout(cipher.decrypt(session.encryptedRefreshToken()));
        return Map.of("authenticated", false);
    }
    private SessionStore.Session requireSession(String id) {
        SessionStore.Session session = store.get(id);
        if (session == null) throw new IamException(401, "Sua sessão terminou. Entre novamente.");
        return session;
    }
    void protect(HttpServletRequest request, SessionStore.Session session) {
        if (!config.publicUrl().equals(request.getHeader("Origin")) || !equal(session.csrfToken(), request.getHeader("X-CSRF-Token")))
            throw new IamException(403, "Origem ou proteção CSRF inválida.");
    }
    private SessionStore.Session session(OidcClient.Tokens tokens, String csrf) {
        Jwt access = decoder.decode(tokens.access_token());
        if (!access.getAudience().contains("pulsepass-api")) throw new IamException(401, "Token inválido.");
        Map<String,Object> realm = access.getClaim("realm_access");
        List<String> roles = realm != null && realm.get("roles") instanceof List<?> values ? values.stream().map(Object::toString).toList() : List.of();
        String name = access.getClaimAsString("name");
        if (name == null) name = access.getClaimAsString("preferred_username");
        Instant expires = access.getExpiresAt();
        if (expires == null || !expires.isAfter(Instant.now())) throw new IamException(401, "Token expirado.");
        long lifetime = tokens.refresh_expires_in() > 0 ? Math.min(tokens.refresh_expires_in(), 86400) : 1800;
        return new SessionStore.Session(tokens.access_token(), cipher.encrypt(tokens.refresh_token()), expires,
            Instant.now().plusSeconds(lifetime), csrf, new SessionStore.User(access.getSubject(), name, roles));
    }
    private Object view(SessionStore.Session session) {
        return Map.of("authenticated", true, "accessToken", session.accessToken(), "expiresAt", session.expiresAt(),
            "csrfToken", session.csrfToken(), "user", session.user());
    }
    private void cookie(HttpServletResponse response, String name, String value, Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true).secure(config.secureCookies())
            .sameSite("Lax").path("/api/iam").maxAge(age).build().toString());
    }
    private static void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache"); }
    static String safeReturnTo(String path) { return path.startsWith("/") && !path.startsWith("//") && !path.contains("\\") && !path.contains("\r") && !path.contains("\n") ? path : "/"; }
    static String random() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String challenge(String verifier) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    static boolean equal(String a, String b) { return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
}
