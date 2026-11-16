package br.pucrs.pulsepass.iam.infrastructure.clients;

import br.pucrs.pulsepass.iam.application.IamException;
import br.pucrs.pulsepass.iam.infrastructure.config.IamProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class OidcClient {
  private final IamProperties config;
  private final RestClient http;

  public record Tokens(
      String access_token,
      String refresh_token,
      String id_token,
      long expires_in,
      long refresh_expires_in) {}

  public OidcClient(IamProperties config) {
    this.config = config;
    var factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(8));
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  public Tokens exchange(String code, String verifier) {
    return tokens(
        Map.of(
            "grant_type",
            "authorization_code",
            "code",
            code,
            "code_verifier",
            verifier,
            "redirect_uri",
            config.callback()));
  }

  public Tokens refresh(String token) {
    return tokens(Map.of("grant_type", "refresh_token", "refresh_token", token));
  }

  private Tokens tokens(Map<String, String> values) {
    try {
      Tokens tokens =
          http.post()
              .uri(config.internalOidc() + "/token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form(values))
              .retrieve()
              .body(Tokens.class);
      if (tokens == null || tokens.access_token() == null || tokens.refresh_token() == null)
        throw new IllegalStateException();
      return tokens;
    } catch (Exception exception) {
      throw new IamException(401, "Não foi possível validar a sessão. Entre novamente.");
    }
  }

  public void logout(String refreshToken) {
    try {
      http.post()
          .uri(config.internalOidc() + "/logout")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form(Map.of("refresh_token", refreshToken)))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception ignored) {
      /* Local session is invalidated even if identity provider is unavailable. */
    }
  }

  private LinkedMultiValueMap<String, String> form(Map<String, String> values) {
    var form = new LinkedMultiValueMap<String, String>();
    values.forEach(form::add);
    form.add("client_id", config.clientId());
    form.add("client_secret", config.clientSecret());
    return form;
  }
}
