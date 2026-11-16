package br.pucrs.pulsepass.orders.application;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import br.pucrs.pulsepass.common.messaging.EventMessage;
import br.pucrs.pulsepass.common.web.ApiException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    classes = br.pucrs.pulsepass.orders.OrdersApplication.class,
    properties = {"pulsepass.orders.workers=false", "pulsepass.service-name=orders"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class OrderWorkflowIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17.6");

  @Container
  static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired org.springframework.test.web.servlet.MockMvc http;
  @Autowired OrderService orders;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired KafkaTemplate<String, String> producer;
  String owner;

  @BeforeEach
  void owner() {
    owner = UUID.randomUUID().toString();
  }

  @Test
  void concurrentRetryHasOneOrderAndChangedBodyConflicts() throws Exception {
    UUID reservation = UUID.randomUUID();
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<UUID>> results = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        results.add(pool.submit(() -> orders.create(owner, "stable", reservation)));
      Set<UUID> ids = new HashSet<>();
      for (var result : results) ids.add(result.get(10, TimeUnit.SECONDS));
      assertEquals(1, ids.size());
      assertEquals(
          1,
          db.queryForObject(
              "SELECT count(*) FROM customer_order WHERE reservation_id=?",
              Integer.class,
              reservation));
      assertEquals(
          409,
          assertThrows(ApiException.class, () -> orders.create(owner, "stable", UUID.randomUUID()))
              .status());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void invalidOwnerIntentCannotOccupyReservationForever() {
    UUID reservation = UUID.randomUUID();
    UUID rejected = orders.create("wrong-owner", "bad", reservation);
    UUID eventId = UUID.randomUUID();
    orders.consume(
        new EventMessage(
            eventId,
            "reservation.created",
            1,
            "reservation",
            reservation,
            1,
            Instant.now(),
            "inventory",
            eventId,
            null,
            json.createObjectNode()
                .put("reservationId", reservation.toString())
                .put("customerId", owner)
                .put("status", "ACTIVE")));
    UUID legitimate = orders.create(owner, "good", reservation);
    assertNotEquals(rejected, legitimate);
    assertThrows(ApiException.class, () -> orders.get("wrong-owner", rejected));
    assertEquals(legitimate, orders.create(owner, "good", reservation));
  }

  @Test
  void httpRequiresClienteAndProtectsOtherCustomers() throws Exception {
    UUID order = orders.create(owner, "http", UUID.randomUUID());
    http.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/orders/" + order))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    http.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/orders/" + order)
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject(owner))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_Operador"))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
    http.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/orders/" + order)
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject("another-customer"))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_Cliente"))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isNotFound());
  }

  @Test
  void paymentIsDurableAndOnlyInventoryConfirmationPublishesPaid() {
    UUID order = orders.create(owner, "create", UUID.randomUUID());
    orders.snapshot(order, snapshot(order, "ACTIVE", 1));
    orders.pay(owner, order, "pay", "TIMEOUT");
    orders.pay(owner, order, "pay", "TIMEOUT");
    UUID attempt =
        db.queryForObject("SELECT id FROM payment_attempt WHERE order_id=?", UUID.class, order);
    orders.paymentResult(attempt, "PENDING");
    assertEquals("AWAITING_PAYMENT", orders.get(owner, order).get("status"));
    orders.paymentResult(attempt, "APPROVED");
    assertEquals("AWAITING_PAYMENT", orders.get(owner, order).get("status"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM outbox WHERE aggregate_id=? AND payload::jsonb->>'eventType'='reservation.confirm.requested'",
            Integer.class,
            orders.get(owner, order).get("reservationId")));
    orders.snapshot(order, snapshot(order, "CONFIRMED", 2));
    orders.snapshot(order, snapshot(order, "CONFIRMED", 2));
    assertEquals("PAID", orders.get(owner, order).get("status"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM outbox WHERE aggregate_id=? AND payload::jsonb->>'eventType'='order.paid'",
            Integer.class,
            order));
    JsonNode paid =
        orders.parse(
            db.queryForObject(
                "SELECT payload FROM outbox WHERE aggregate_id=? AND payload::jsonb->>'eventType'='order.paid'",
                String.class,
                order));
    assertTrue(paid.path("payload").path("items").get(0).hasNonNull("orderItemId"));
  }

  @Test
  void ownerIsEnforcedAndCancellationWaitsForAuthority() {
    UUID order = orders.create(owner, "create", UUID.randomUUID());
    orders.snapshot(order, snapshot(order, "ACTIVE", 1));
    assertThrows(ApiException.class, () -> orders.get("someone-else", order));
    assertThrows(ApiException.class, () -> orders.pay("someone-else", order, "pay", "APPROVE"));
    orders.cancel(owner, order, "cancel");
    orders.cancel(owner, order, "cancel");
    assertEquals("RESERVED", orders.get(owner, order).get("status"));
    orders.snapshot(order, snapshot(order, "CANCELLED", 2));
    assertEquals("CANCELLED", orders.get(owner, order).get("status"));
  }

  @Test
  void declineAllowsFreshAttemptAndExpiryVoidsApproval() {
    UUID order = orders.create(owner, "create", UUID.randomUUID());
    orders.snapshot(order, snapshot(order, "ACTIVE", 1));
    orders.pay(owner, order, "first", "DECLINE");
    UUID attempt =
        db.queryForObject("SELECT id FROM payment_attempt WHERE order_id=?", UUID.class, order);
    orders.paymentResult(attempt, "DECLINED");
    assertEquals("RESERVED", orders.get(owner, order).get("status"));
    orders.pay(owner, order, "second", "APPROVE");
    UUID second =
        db.queryForObject(
            "SELECT id FROM payment_attempt WHERE order_id=? AND status='PENDING'",
            UUID.class,
            order);
    orders.snapshot(order, snapshot(order, "EXPIRED", 2));
    orders.paymentResult(second, "APPROVED");
    assertEquals("EXPIRED", orders.get(owner, order).get("status"));
    assertEquals("VOIDED", orders.get(owner, order).get("paymentStatus"));
  }

  @Test
  void realKafkaDuplicateIsAppliedOnceAndEarlyResultSurvives() throws Exception {
    UUID reservation = UUID.randomUUID(), eventId = UUID.randomUUID();
    ObjectNode payload =
        json.createObjectNode()
            .put("reservationId", reservation.toString())
            .put("customerId", owner)
            .put("status", "EXPIRED")
            .put("version", 2);
    EventMessage event =
        new EventMessage(
            eventId,
            "reservation.expired",
            1,
            "reservation",
            reservation,
            2,
            Instant.now(),
            "inventory",
            eventId,
            null,
            payload);
    String raw = json.writeValueAsString(event);
    producer
        .send("pulsepass.inventory.reservations.v1", reservation.toString(), raw)
        .get(10, TimeUnit.SECONDS);
    producer
        .send("pulsepass.inventory.reservations.v1", reservation.toString(), raw)
        .get(10, TimeUnit.SECONDS);
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertEquals(
                    1,
                    db.queryForObject(
                        "SELECT count(*) FROM inbox WHERE consumer='orders-reservations-v1' AND event_id=?",
                        Integer.class,
                        eventId)));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM reservation_observation WHERE reservation_id=?",
            Integer.class,
            reservation));
    UUID order = orders.create(owner, "after-event", reservation);
    orders.snapshot(order, snapshot(order, "EXPIRED", 2));
    assertEquals("EXPIRED", orders.get(owner, order).get("status"));
  }

  private ObjectNode snapshot(UUID order, String status, long version) {
    var row = db.queryForMap("SELECT * FROM customer_order WHERE id=?", order);
    ObjectNode s = json.createObjectNode();
    s.put("reservationId", row.get("reservation_id").toString())
        .put("orderId", order.toString())
        .put("customerId", owner)
        .put("status", status)
        .put("version", version)
        .put("expiresAt", Instant.now().plusSeconds(600).toString())
        .put("eventId", UUID.randomUUID().toString())
        .put("eventName", "Show")
        .put("eventStartsAt", Instant.now().plusSeconds(86400).toString())
        .put("venue", "Arena")
        .put("currency", "BRL")
        .put("totalCents", 12000);
    s.putArray("items")
        .addObject()
        .put("reservationItemId", UUID.nameUUIDFromBytes(order.toString().getBytes()).toString())
        .put("seatId", UUID.randomUUID().toString())
        .put("sector", "A")
        .put("row", "1")
        .put("number", "10")
        .put("priceCents", 12000);
    return s;
  }
}
