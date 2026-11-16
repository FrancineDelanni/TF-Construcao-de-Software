package br.pucrs.pulsepass.iam.application;

import static org.junit.jupiter.api.Assertions.*;

import br.pucrs.pulsepass.iam.infrastructure.clients.OidcClient;
import br.pucrs.pulsepass.iam.infrastructure.config.IamProperties;
import br.pucrs.pulsepass.iam.infrastructure.persistence.SessionStore;
import br.pucrs.pulsepass.iam.infrastructure.security.TokenCipher;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class SessionServiceTest {
  private final IamProperties config =
      new IamProperties(
          "http://localhost:8080",
          "http://localhost:8081",
          "http://keycloak:8080",
          "pulsepass-web",
          "test-secret",
          Base64.getEncoder().encodeToString(new byte[32]));
  private final TokenCipher cipher = new TokenCipher(config);
  private final OidcClient.Tokens tokens =
      new OidcClient.Tokens("access", "private-refresh", "identity", 60, 300);
  private final SessionStore.Login login =
      new SessionStore.Login("verifier", "nonce", "browser", "/");

  private Jwt identity(String audience, String nonce, String subject) {
    return Jwt.withTokenValue("identity")
        .header("alg", "RS256")
        .subject(subject)
        .audience(List.of(audience))
        .claim("nonce", nonce)
        .build();
  }

  private Jwt access(String audience, String subject, Instant expires) {
    return Jwt.withTokenValue("access")
        .header("alg", "RS256")
        .subject(subject)
        .audience(List.of(audience))
        .expiresAt(expires)
        .claim("preferred_username", "Cliente")
        .claim("realm_access", Map.of("roles", List.of("Cliente")))
        .build();
  }

  private OidcClient oidc() {
    return new OidcClient(config) {
      @Override
      public Tokens exchange(String code, String verifier) {
        assertEquals("code", code);
        assertEquals("verifier", verifier);
        return tokens;
      }

      @Override
      public Tokens refresh(String token) {
        assertEquals("private-refresh", token);
        return tokens;
      }
    };
  }

  private JwtDecoder decoder(Jwt identity, Jwt access) {
    return value -> "identity".equals(value) ? identity : access;
  }

  @Test
  void authenticatesTheSameIdentityAndKeepsRefreshEncrypted() {
    var access = access("pulsepass-api", "owner", Instant.now().plusSeconds(60));
    var service =
        new SessionService(
            config,
            null,
            cipher,
            oidc(),
            decoder(identity(config.clientId(), "nonce", "owner"), access));
    var session = service.authenticate("code", login);
    assertEquals("owner", session.user().sub());
    assertEquals("Cliente", session.user().name());
    assertEquals(List.of("Cliente"), session.user().roles());
    assertEquals("private-refresh", cipher.decrypt(session.encryptedRefreshToken()));
    assertNotEquals("private-refresh", session.encryptedRefreshToken());
    assertFalse(session.csrfToken().isBlank());
    assertTrue(session.sessionExpiresAt().isAfter(Instant.now().plusSeconds(290)));
  }

  @Test
  void rejectsAnIncorrectAudienceNonceOrSubject() {
    var access = access("pulsepass-api", "owner", Instant.now().plusSeconds(60));
    for (Jwt invalid :
        List.of(
            identity("other-client", "nonce", "owner"),
            identity(config.clientId(), "wrong-nonce", "owner"),
            identity(config.clientId(), "nonce", "other-owner"))) {
      var service = new SessionService(config, null, cipher, oidc(), decoder(invalid, access));
      assertEquals(
          401,
          assertThrows(IamException.class, () -> service.authenticate("code", login)).status());
    }
  }

  @Test
  void rejectsExpiredOrWrongAudienceAccessTokens() {
    for (Jwt invalid :
        List.of(
            access("other-api", "owner", Instant.now().plusSeconds(60)),
            access("pulsepass-api", "owner", Instant.now().minusSeconds(1)))) {
      var service = new SessionService(config, null, cipher, oidc(), value -> invalid);
      assertEquals(
          401, assertThrows(IamException.class, () -> service.create(tokens, "csrf")).status());
    }
  }

  @Test
  void refreshDoesNotResurrectASessionDeletedWhileCallingTheIdentityProvider() {
    var deleted = new AtomicBoolean();
    var unlocked = new AtomicBoolean();
    var previous =
        new SessionStore.Session(
            "access",
            cipher.encrypt("private-refresh"),
            Instant.now().plusSeconds(60),
            Instant.now().plusSeconds(300),
            "csrf",
            new SessionStore.User("owner", "Cliente", List.of("Cliente")));
    var store =
        new SessionStore(null, null) {
          @Override
          public String tryLock(String id) {
            return "lease";
          }

          @Override
          public Session get(String id) {
            return previous;
          }

          @Override
          public boolean replaceIfPresent(String id, Session value) {
            return false;
          }

          @Override
          public void delete(String id) {
            deleted.set(true);
          }

          @Override
          public void unlock(String id, String lease) {
            assertEquals("lease", lease);
            unlocked.set(true);
          }
        };
    var service =
        new SessionService(
            config,
            store,
            cipher,
            oidc(),
            value -> access("pulsepass-api", "owner", Instant.now().plusSeconds(60)));
    assertEquals(401, assertThrows(IamException.class, () -> service.refresh("session")).status());
    assertTrue(deleted.get());
    assertTrue(unlocked.get());
  }

  @Test
  void anExistingRefreshLeaseKeepsTheSessionIntact() {
    var store =
        new SessionStore(null, null) {
          @Override
          public String tryLock(String id) {
            return null;
          }

          @Override
          public void delete(String id) {
            fail("A refresh conflict must not delete the session");
          }
        };
    var service = new SessionService(config, store, cipher, oidc(), value -> null);
    assertEquals(409, assertThrows(IamException.class, () -> service.refresh("session")).status());
  }

  @Test
  void logoutDecryptsTheRefreshTokenForTheIdentityProvider() {
    var revoked = new ArrayList<String>();
    var client =
        new OidcClient(config) {
          @Override
          public void logout(String token) {
            revoked.add(token);
          }
        };
    var service =
        new SessionService(
            config,
            null,
            cipher,
            client,
            value -> access("pulsepass-api", "owner", Instant.now().plusSeconds(60)));
    var session = service.create(tokens, "csrf");
    service.logout(session);
    assertEquals(List.of("private-refresh"), revoked);
  }
}
