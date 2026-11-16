package br.pucrs.pulsepass.orders.infrastructure.clients;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;

@Component
public class ServiceTokenProvider {
  private final RestClient http;
  private final String tokenUrl, secret;
  private String token;
  private Instant refreshAt = Instant.EPOCH;

  public ServiceTokenProvider(
      @Value("${pulsepass.token-url}") String tokenUrl,
      @Value("${pulsepass.orders-client-secret}") String secret) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.http = RestClient.builder().requestFactory(factory).build();
    this.tokenUrl = tokenUrl;
    this.secret = secret;
  }

  public synchronized String token() {
    if (token != null && Instant.now().isBefore(refreshAt)) return token;
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", "orders-service");
    form.add("client_secret", secret);
    JsonNode response =
        http.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);
    if (response == null || !response.hasNonNull("access_token"))
      throw new IllegalStateException("Token de serviço indisponível");
    token = response.path("access_token").asText();
    refreshAt = Instant.now().plusSeconds(Math.max(1, response.path("expires_in").asLong(60) - 15));
    return token;
  }
}
