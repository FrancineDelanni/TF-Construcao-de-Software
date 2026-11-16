package br.pucrs.pulsepass.orders.infrastructure.clients;

import br.pucrs.pulsepass.orders.application.InventoryClient;
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
public class HttpInventoryClient implements InventoryClient {
  private final RestClient http;
  private final String inventory;
  private final ServiceTokenProvider tokens;

  public HttpInventoryClient(
      @Value("${pulsepass.inventory-url}") String inventory, ServiceTokenProvider tokens) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.http = RestClient.builder().requestFactory(factory).build();
    this.inventory = inventory;
    this.tokens = tokens;
  }

  public JsonNode bind(UUID reservation, UUID order, String owner) {
    return http.post()
        .uri(inventory + "/api/inventory/internal/reservations/" + reservation + "/bind")
        .headers(h -> h.setBearerAuth(tokens.token()))
        .body(Map.of("orderId", order, "customerId", owner))
        .retrieve()
        .body(JsonNode.class);
  }

  public JsonNode reservation(UUID reservation) {
    return http.get()
        .uri(inventory + "/api/inventory/internal/reservations/" + reservation)
        .headers(h -> h.setBearerAuth(tokens.token()))
        .retrieve()
        .body(JsonNode.class);
  }
}
