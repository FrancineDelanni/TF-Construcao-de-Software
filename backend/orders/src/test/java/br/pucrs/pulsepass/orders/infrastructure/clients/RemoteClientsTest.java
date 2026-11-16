package br.pucrs.pulsepass.orders.infrastructure.clients;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RemoteClientsTest {
  @Test
  void usesClientCredentialsAndReconcilesSameAttemptRatherThanCreatingAnother() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    UUID reservation = UUID.randomUUID(), order = UUID.randomUUID(), attempt = UUID.randomUUID();
    AtomicInteger tokens = new AtomicInteger(),
        posts = new AtomicInteger(),
        gets = new AtomicInteger();
    server.createContext(
        "/token",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          assertTrue(body.contains("grant_type=client_credentials"));
          assertTrue(body.contains("client_id=orders-service"));
          assertTrue(body.contains("client_secret=test-secret"));
          tokens.incrementAndGet();
          respond(exchange, 200, "{\"access_token\":\"service-token\",\"expires_in\":60}");
        });
    server.createContext(
        "/api/inventory/internal/reservations/" + reservation,
        exchange -> {
          assertEquals(
              "Bearer service-token", exchange.getRequestHeaders().getFirst("Authorization"));
          respond(exchange, 200, "{\"reservationId\":\"" + reservation + "\"}");
        });
    server.createContext(
        "/payments",
        exchange -> {
          assertEquals("test-gateway-key", exchange.getRequestHeaders().getFirst("X-Gateway-Key"));
          if (exchange.getRequestMethod().equals("POST")) {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains(attempt.toString()));
            posts.incrementAndGet();
            respond(exchange, 200, "{\"status\":\"PENDING\"}");
          } else {
            assertEquals("/payments/" + attempt, exchange.getRequestURI().getPath());
            respond(
                exchange,
                gets.incrementAndGet() == 1 ? 404 : 200,
                gets.get() == 1 ? "{}" : "{\"status\":\"APPROVED\"}");
          }
        });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort();
      var tokensClient = new ServiceTokenProvider(url + "/token", "test-secret");
      var inventoryClient = new HttpInventoryClient(url, tokensClient);
      var paymentClient = new HttpPaymentClient(url, "test-gateway-key");
      inventoryClient.bind(reservation, order, "owner");
      inventoryClient.reservation(reservation);
      assertEquals(1, tokens.get());
      assertEquals("PENDING", paymentClient.payment(attempt, "TIMEOUT").path("status").asText());
      assertEquals("APPROVED", paymentClient.payment(attempt, "TIMEOUT").path("status").asText());
      assertEquals(1, posts.get());
      assertEquals(2, gets.get());
    } finally {
      server.stop(0);
    }
  }

  private static void respond(HttpExchange exchange, int status, String content)
      throws java.io.IOException {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var stream = exchange.getResponseBody()) {
      stream.write(bytes);
    }
  }
}
