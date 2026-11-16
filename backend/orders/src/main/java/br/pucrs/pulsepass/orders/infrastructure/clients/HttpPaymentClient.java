package br.pucrs.pulsepass.orders.infrastructure.clients;

import br.pucrs.pulsepass.orders.application.PaymentClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class HttpPaymentClient implements PaymentClient {
  private final RestClient http;
  private final String gateway, key;

  public HttpPaymentClient(
      @Value("${pulsepass.gateway-url}") String gateway,
      @Value("${pulsepass.gateway-key}") String key) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.http = RestClient.builder().requestFactory(factory).build();
    this.gateway = gateway;
    this.key = key;
  }

  public JsonNode payment(UUID attempt, String outcome) {
    try {
      return http.get()
          .uri(gateway + "/payments/" + attempt)
          .header("X-Gateway-Key", key)
          .retrieve()
          .body(JsonNode.class);
    } catch (HttpClientErrorException.NotFound absent) {
      return http.post()
          .uri(gateway + "/payments")
          .header("X-Gateway-Key", key)
          .body(Map.of("attemptId", attempt, "outcome", outcome))
          .retrieve()
          .body(JsonNode.class);
    }
  }
}
