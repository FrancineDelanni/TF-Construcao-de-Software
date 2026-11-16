package br.pucrs.pulsepass.iam.api;

import static org.junit.jupiter.api.Assertions.*;

import br.pucrs.pulsepass.iam.application.IamException;
import br.pucrs.pulsepass.iam.infrastructure.clients.OidcClient;
import br.pucrs.pulsepass.iam.infrastructure.config.IamProperties;
import br.pucrs.pulsepass.iam.infrastructure.persistence.SessionStore;
import br.pucrs.pulsepass.iam.infrastructure.security.TokenCipher;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

class SessionSecurityTest {
  static IamProperties properties() {
    return new IamProperties(
        "http://localhost:8080",
        "http://localhost:8081",
        "http://keycloak:8080",
        "pulsepass-web",
        "local-web-secret",
        Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Test
  void pkceUsesS256Rfc7636Vector() {
    assertEquals(
        "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
        br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.challenge(
            "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
  }

  @Test
  void returnPathPreventsOpenRedirects() {
    assertEquals(
        "/",
        br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.safeReturnTo(
            "//evil.example/path"));
    assertEquals(
        "/",
        br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.safeReturnTo("/\\evil.example"));
    assertEquals(
        "/",
        br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.safeReturnTo(
            "https://evil.example"));
    assertEquals(
        "/",
        br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.safeReturnTo(
            "/\r\nLocation: evil"));
    assertEquals(
        "/?order=123",
        br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.safeReturnTo("/?order=123"));
  }

  @Test
  void refreshEncryptionIsRandomAndAuthenticated() {
    TokenCipher cipher = new TokenCipher(properties());
    String first = cipher.encrypt("secret-refresh-token");
    assertNotEquals(first, cipher.encrypt("secret-refresh-token"));
    assertFalse(first.contains("secret-refresh-token"));
    assertEquals("secret-refresh-token", cipher.decrypt(first));
    byte[] altered = Base64.getDecoder().decode(first);
    altered[15] ^= 1;
    assertThrows(
        IllegalStateException.class,
        () -> cipher.decrypt(Base64.getEncoder().encodeToString(altered)));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt("AAAA"));
  }

  @Test
  void keyMustBe256Bits() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TokenCipher(new IamProperties("", "", "", "", "", "AAAA")));
  }

  @Test
  void refreshReadsRotatedTokenAfterAcquiringLock() {
    var config = properties();
    var cipher = new TokenCipher(config);
    var user = new SessionStore.User("owner", "Cliente", List.of("Cliente"));
    Instant now = Instant.now();
    var stale =
        new SessionStore.Session(
            "old-access",
            cipher.encrypt("old-refresh"),
            now.plusSeconds(60),
            now.plusSeconds(300),
            "csrf",
            user);
    var latest =
        new SessionStore.Session(
            "new-access",
            cipher.encrypt("rotated-refresh"),
            now.plusSeconds(60),
            now.plusSeconds(300),
            "csrf",
            user);
    var reads = new AtomicInteger();
    SessionStore store =
        new SessionStore(null, null) {
          @Override
          public Session get(String id) {
            return reads.getAndIncrement() == 0 ? stale : latest;
          }

          @Override
          public String tryLock(String id) {
            return "lease";
          }

          @Override
          public boolean replaceIfPresent(String id, Session value) {
            return true;
          }

          @Override
          public void unlock(String id, String lease) {
            assertEquals("lease", lease);
          }
        };
    OidcClient oidc =
        new OidcClient(config) {
          @Override
          public Tokens refresh(String token) {
            assertEquals("rotated-refresh", token);
            return new Tokens("valid-access", "next-refresh", null, 60, 300);
          }
        };
    Jwt jwt =
        Jwt.withTokenValue("valid-access")
            .header("alg", "RS256")
            .subject("owner")
            .audience(List.of("pulsepass-api"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .claim("name", "Cliente")
            .claim("realm_access", Map.of("roles", List.of("Cliente")))
            .build();
    var controller =
        new SessionController(
            config,
            store,
            new br.pucrs.pulsepass.iam.application.SessionService(
                config, store, cipher, oidc, token -> jwt));
    var request = new MockHttpServletRequest();
    request.addHeader("Origin", "http://localhost:8080");
    request.addHeader("X-CSRF-Token", "csrf");
    assertNotNull(controller.refresh("session", request, new MockHttpServletResponse()));
    assertEquals(2, reads.get());
  }

  @Test
  void csrfRequiresExactOriginAndSecret() {
    SessionController controller = new SessionController(properties(), null, null);
    var session =
        new SessionStore.Session(
            "access",
            "ciphertext",
            Instant.now(),
            Instant.now(),
            "csrf-secret",
            new SessionStore.User("u", "U", List.of("Cliente")));
    var request = new MockHttpServletRequest();
    request.addHeader("Origin", "http://localhost:8080");
    request.addHeader("X-CSRF-Token", "csrf-secret");
    assertDoesNotThrow(() -> controller.protect(request, session));
    request.removeHeader("Origin");
    request.addHeader("Origin", "https://malicious.example");
    assertEquals(
        403, assertThrows(IamException.class, () -> controller.protect(request, session)).status());
    request.removeHeader("Origin");
    request.addHeader("Origin", "http://localhost:8080");
    request.removeHeader("X-CSRF-Token");
    assertThrows(IamException.class, () -> controller.protect(request, session));
  }
}
