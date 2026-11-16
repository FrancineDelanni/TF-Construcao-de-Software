package br.pucrs.pulsepass.iam.api;

import static br.pucrs.pulsepass.iam.infrastructure.security.OidcSupport.*;

import br.pucrs.pulsepass.iam.application.IamException;
import br.pucrs.pulsepass.iam.application.SessionService;
import br.pucrs.pulsepass.iam.infrastructure.config.IamProperties;
import br.pucrs.pulsepass.iam.infrastructure.persistence.SessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/iam")
public class SessionController {
  private static final String SESSION = "PULSEPASS_SESSION", LOGIN = "PULSEPASS_LOGIN";
  private final IamProperties config;
  private final SessionStore store;
  private final SessionService sessions;

  public SessionController(IamProperties config, SessionStore store, SessionService sessions) {
    this.config = config;
    this.store = store;
    this.sessions = sessions;
  }

  @GetMapping("/login")
  public void login(
      @RequestParam(defaultValue = "/") String returnTo,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {
    noCache(response);
    if (!store.permitLogin(request.getRemoteAddr()))
      throw new IamException(429, "Muitas tentativas. Aguarde cinco minutos.");
    String state = random(), verifier = random(), nonce = random(), browserSecret = random();
    store.saveLogin(
        state, new SessionStore.Login(verifier, nonce, browserSecret, safeReturnTo(returnTo)));
    cookie(response, LOGIN, browserSecret, Duration.ofMinutes(5));
    String url =
        UriComponentsBuilder.fromUriString(config.issuer() + "/protocol/openid-connect/auth")
            .queryParam("client_id", config.clientId())
            .queryParam("redirect_uri", config.callback())
            .queryParam("response_type", "code")
            .queryParam("scope", "openid profile email")
            .queryParam("state", state)
            .queryParam("nonce", nonce)
            .queryParam("code_challenge", challenge(verifier))
            .queryParam("code_challenge_method", "S256")
            .build()
            .encode()
            .toUriString();
    response.sendRedirect(url);
  }

  @GetMapping("/callback")
  public void callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @CookieValue(name = LOGIN, required = false) String browserSecret,
      @CookieValue(name = SESSION, required = false) String oldSession,
      HttpServletResponse response)
      throws Exception {
    noCache(response);
    cookie(response, LOGIN, "", Duration.ZERO);
    SessionStore.Login login =
        state == null || state.length() > 128 ? null : store.consumeLogin(state);
    if (login == null || !equal(login.browserSecret(), browserSecret) || code == null) {
      response.sendRedirect(config.publicUrl() + "/?login=failed");
      return;
    }
    String stage = "code-exchange";
    try {
      SessionStore.Session session = sessions.authenticate(code, login);
      stage = "session-save";
      String id = random();
      store.delete(oldSession);
      store.save(id, session);
      cookie(response, SESSION, id, Duration.between(Instant.now(), session.sessionExpiresAt()));
      response.sendRedirect(config.publicUrl() + login.returnTo());
    } catch (Exception error) {
      org.slf4j.LoggerFactory.getLogger(SessionController.class)
          .warn("Callback OIDC recusado na etapa {} ({})", stage, error.getClass().getSimpleName());
      response.sendRedirect(config.publicUrl() + "/?login=failed");
    }
  }

  @GetMapping("/session")
  public Object current(
      @CookieValue(name = SESSION, required = false) String id, HttpServletResponse response) {
    noCache(response);
    SessionStore.Session session = store.get(id);
    if (session == null) return Map.of("authenticated", false);
    return view(session);
  }

  @PostMapping("/refresh")
  public Object refresh(
      @CookieValue(name = SESSION, required = false) String id,
      HttpServletRequest request,
      HttpServletResponse response) {
    noCache(response);
    protect(request, sessions.require(id));
    try {
      SessionStore.Session updated = sessions.refresh(id);
      cookie(response, SESSION, id, Duration.between(Instant.now(), updated.sessionExpiresAt()));
      return view(updated);
    } catch (IamException error) {
      if (error.status() == 401) cookie(response, SESSION, "", Duration.ZERO);
      throw error;
    }
  }

  @PostMapping("/logout")
  public Object logout(
      @CookieValue(name = SESSION, required = false) String id,
      HttpServletRequest request,
      HttpServletResponse response) {
    noCache(response);
    SessionStore.Session session = sessions.require(id);
    protect(request, session);
    store.delete(id);
    cookie(response, SESSION, "", Duration.ZERO);
    sessions.logout(session);
    return Map.of("authenticated", false);
  }

  void protect(HttpServletRequest request, SessionStore.Session session) {
    if (!config.publicUrl().equals(request.getHeader("Origin"))
        || !equal(session.csrfToken(), request.getHeader("X-CSRF-Token")))
      throw new IamException(403, "Origem ou proteção CSRF inválida.");
  }

  private Object view(SessionStore.Session session) {
    return Map.of(
        "authenticated",
        true,
        "accessToken",
        session.accessToken(),
        "expiresAt",
        session.expiresAt(),
        "csrfToken",
        session.csrfToken(),
        "user",
        session.user());
  }

  private void cookie(HttpServletResponse response, String name, String value, Duration age) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(config.secureCookies())
            .sameSite("Lax")
            .path("/api/iam")
            .maxAge(age)
            .build()
            .toString());
  }

  private static void noCache(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Pragma", "no-cache");
  }
}
