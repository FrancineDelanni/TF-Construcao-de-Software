package br.pucrs.pulsepass.iam.application;

import static br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.equal;

import br.pucrs.pulsepass.iam.infrastructure.clients.OidcClient;
import br.pucrs.pulsepass.iam.infrastructure.config.IamProperties;
import br.pucrs.pulsepass.iam.infrastructure.persistence.SessionStore;
import br.pucrs.pulsepass.iam.infrastructure.security.TokenCipher;
import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  private final SessionStore store;
  private final TokenCipher cipher;
  private final OidcClient oidc;
  private final JwtDecoder decoder;
  private final IamProperties config;

  public SessionService(
      IamProperties config,
      SessionStore store,
      TokenCipher cipher,
      OidcClient oidc,
      JwtDecoder decoder) {
    this.config = config;
    this.store = store;
    this.cipher = cipher;
    this.oidc = oidc;
    this.decoder = decoder;
  }

  public SessionStore.Session authenticate(String code, SessionStore.Login login) {
    OidcClient.Tokens tokens = oidc.exchange(code, login.verifier());
    Jwt identity = decoder.decode(tokens.id_token());
    if (!identity.getAudience().contains(config.clientId())
        || !equal(login.nonce(), identity.getClaimAsString("nonce")))
      throw new IamException(401, "Identidade inválida.");
    SessionStore.Session session =
        create(tokens, br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.random());
    if (!equal(identity.getSubject(), session.user().sub()))
      throw new IamException(401, "Identidade inválida.");
    return session;
  }

  public SessionStore.Session refresh(String id) {
    String lease = store.tryLock(id);
    if (lease == null)
      throw new IamException(409, "Renovação em andamento. Consulte a sessão novamente.");
    try {
      SessionStore.Session previous = require(id);
      OidcClient.Tokens tokens = oidc.refresh(cipher.decrypt(previous.encryptedRefreshToken()));
      SessionStore.Session updated = create(tokens, previous.csrfToken());
      if (!equal(previous.user().sub(), updated.user().sub()))
        throw new IamException(401, "Identidade inválida.");
      if (!store.replaceIfPresent(id, updated))
        throw new IamException(401, "Sessão encerrada. Entre novamente.");
      return updated;
    } catch (IamException error) {
      store.delete(id);
      throw error;
    } finally {
      store.unlock(id, lease);
    }
  }

  public void logout(SessionStore.Session session) {
    oidc.logout(cipher.decrypt(session.encryptedRefreshToken()));
  }

  public SessionStore.Session require(String id) {
    SessionStore.Session session = store.get(id);
    if (session == null) throw new IamException(401, "Sua sessão terminou. Entre novamente.");
    return session;
  }

  public SessionStore.Session create(OidcClient.Tokens tokens, String csrf) {
    Jwt access = decoder.decode(tokens.access_token());
    if (!access.getAudience().contains("pulsepass-api"))
      throw new IamException(401, "Token inválido.");
    Map<String, Object> realm = access.getClaim("realm_access");
    List<String> roles =
        realm != null && realm.get("roles") instanceof List<?> values
            ? values.stream().map(Object::toString).toList()
            : List.of();
    String name = access.getClaimAsString("name");
    if (name == null) name = access.getClaimAsString("preferred_username");
    Instant expires = access.getExpiresAt();
    if (expires == null || !expires.isAfter(Instant.now()))
      throw new IamException(401, "Token expirado.");
    long lifetime =
        tokens.refresh_expires_in() > 0 ? Math.min(tokens.refresh_expires_in(), 86400) : 1800;
    return new SessionStore.Session(
        tokens.access_token(),
        cipher.encrypt(tokens.refresh_token()),
        expires,
        Instant.now().plusSeconds(lifetime),
        csrf,
        new SessionStore.User(access.getSubject(), name, roles));
  }
}
