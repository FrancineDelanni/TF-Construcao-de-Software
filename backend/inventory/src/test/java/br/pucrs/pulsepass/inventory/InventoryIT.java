package br.pucrs.pulsepass.inventory;

import static org.junit.jupiter.api.Assertions.*;

import br.pucrs.pulsepass.common.messaging.EventMessage;
import br.pucrs.pulsepass.common.web.ApiException;
import br.pucrs.pulsepass.inventory.application.InventoryCommands;
import br.pucrs.pulsepass.inventory.application.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(
    classes = InventoryApplication.class,
    properties = {
      "spring.kafka.listener.auto-startup=false",
      "pulsepass.outbox.enabled=false",
      "pulsepass.inventory.expiry.enabled=false",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081/realms/pulsepass",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/pulsepass/protocol/openid-connect/certs"
    })
class InventoryIT {
  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired InventoryService inventory;
  @Autowired InventoryCommands commands;
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.transaction.PlatformTransactionManager manager;
  UUID event;
  List<UUID> seats;
  final String owner = UUID.randomUUID().toString();

  @BeforeEach
  void clean() {
    jdbc.update("delete from inbox");
    jdbc.update("delete from outbox");
    jdbc.update("delete from inventory_idempotency");
    jdbc.update("update inventory_seats set reservation_id=null,status='AVAILABLE'");
    jdbc.update("delete from inventory_reservation_items");
    jdbc.update("delete from inventory_reservations");
    event = jdbc.queryForObject("select id from inventory_events order by id limit 1", UUID.class);
    seats =
        jdbc.queryForList(
            "select id from inventory_seats where event_id=? order by id limit 3",
            UUID.class,
            event);
  }

  @Test
  void concurrentReservationHasExactlyOneWinnerAndOneOutboxEvent() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    inventory.reserve(
                        event, owner, UUID.randomUUID().toString(), List.of(seats.getFirst()));
                    return true;
                  } catch (ApiException e) {
                    return false;
                  }
                }));
      start.countDown();
      assertEquals(
          1,
          futures.stream()
              .map(
                  f -> {
                    try {
                      return f.get();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .filter(Boolean::booleanValue)
              .count());
    }
    assertEquals(
        1, jdbc.queryForObject("select count(*) from inventory_reservations", Integer.class));
    assertEquals(1, jdbc.queryForObject("select count(*) from outbox", Integer.class));
  }

  @Test
  void unavailableSeatRollsBackEntireReservation() {
    inventory.reserve(event, owner, "first", List.of(seats.getFirst()));
    assertThrows(ApiException.class, () -> inventory.reserve(event, owner, "second", seats));
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from inventory_seats where id in (?,?) and status='AVAILABLE'",
            Integer.class,
            seats.get(1),
            seats.get(2)));
    assertEquals(
        1, jdbc.queryForObject("select count(*) from inventory_reservations", Integer.class));
    assertEquals(1, jdbc.queryForObject("select count(*) from outbox", Integer.class));
  }

  @Test
  void idempotencyIsScopedAndRejectsChangedPayload() {
    var first = inventory.reserve(event, owner, "same", List.of(seats.getFirst()));
    assertEquals(
        first.reservationId(),
        inventory.reserve(event, owner, "same", List.of(seats.getFirst())).reservationId());
    assertEquals(
        409,
        assertThrows(
                ApiException.class,
                () -> inventory.reserve(event, owner, "same", List.of(seats.get(1))))
            .status());
    assertNotEquals(
        first.reservationId(),
        inventory
            .reserve(event, UUID.randomUUID().toString(), "same", List.of(seats.get(1)))
            .reservationId());
  }

  @Test
  void bindIsIdempotentAndDoesNotRenewAndChecksCustomer() {
    var reservation = inventory.reserve(event, owner, "a", List.of(seats.getFirst()));
    var order = UUID.randomUUID();
    var bound = inventory.bind(reservation.reservationId(), order, owner);
    assertEquals(reservation.expiresAt(), bound.expiresAt());
    assertEquals(
        bound.version(), inventory.bind(reservation.reservationId(), order, owner).version());
    assertThrows(
        ApiException.class,
        () -> inventory.bind(reservation.reservationId(), UUID.randomUUID(), owner));
    assertThrows(
        ApiException.class, () -> inventory.get(reservation.reservationId(), "another-customer"));
  }

  @Test
  void afterDeadlineConfirmRejectsAndExpiredReleaseCannotEraseNewReservation() {
    var old = inventory.reserve(event, owner, "old", List.of(seats.getFirst()));
    var order = UUID.randomUUID();
    inventory.bind(old.reservationId(), order, owner);
    jdbc.update(
        "update inventory_reservations set expires_at=clock_timestamp()-interval '1 second' where id=?",
        old.reservationId());
    var replacement = inventory.reserve(event, owner, "new", List.of(seats.getFirst()));
    commands.handle(command("reservation.confirm.requested", old.reservationId(), order));
    commands.handle(command("reservation.cancel.requested", old.reservationId(), order));
    assertEquals("EXPIRED", inventory.get(old.reservationId(), owner).status());
    assertEquals(
        replacement.reservationId(),
        jdbc.queryForObject(
            "select reservation_id from inventory_seats where id=?", UUID.class, seats.getFirst()));
  }

  @Test
  void duplicateCommandIsAtomicWithInboxAndResultAndConfirmedCannotCancel() {
    var reservation = inventory.reserve(event, owner, "dup", seats);
    var order = UUID.randomUUID();
    inventory.bind(reservation.reservationId(), order, owner);
    var confirm = command("reservation.confirm.requested", reservation.reservationId(), order);
    commands.handle(confirm);
    commands.handle(confirm);
    assertEquals(1, jdbc.queryForObject("select count(*) from inbox", Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from outbox where payload::jsonb->>'eventType'='reservation.confirmed'",
            Integer.class));
    commands.handle(command("reservation.cancel.requested", reservation.reservationId(), order));
    assertEquals("CONFIRMED", inventory.get(reservation.reservationId(), owner).status());
    assertEquals(
        3,
        jdbc.queryForObject(
            "select count(*) from inventory_seats where status='SOLD'", Integer.class));
  }

  @Test
  void concurrentConfirmCancelAndExpiryConvergeOnOneTerminalState() throws Exception {
    var reservation = inventory.reserve(event, owner, "race", seats);
    var order = UUID.randomUUID();
    inventory.bind(reservation.reservationId(), order, owner);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                commands.handle(
                    command("reservation.confirm.requested", reservation.reservationId(), order));
                return null;
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                commands.handle(
                    command("reservation.cancel.requested", reservation.reservationId(), order));
                return null;
              });
      var c =
          pool.submit(
              () -> {
                start.await();
                inventory.expire(reservation.reservationId());
                return null;
              });
      start.countDown();
      a.get();
      b.get();
      c.get();
    }
    var current = inventory.get(reservation.reservationId(), owner);
    assertTrue(Set.of("CONFIRMED", "CANCELLED").contains(current.status()));
    assertEquals(
        current.status().equals("CONFIRMED") ? 3 : 0,
        jdbc.queryForObject(
            "select count(*) from inventory_seats where status='SOLD'", Integer.class));
  }

  @Test
  void databaseClockIsReadAfterWaitingForLocks() throws Exception {
    var reservation = inventory.reserve(event, owner, "clock", seats);
    var order = UUID.randomUUID();
    inventory.bind(reservation.reservationId(), order, owner);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var lock =
          pool.submit(
              () ->
                  new TransactionTemplate(manager)
                      .execute(
                          s -> {
                            jdbc.queryForObject(
                                "select id from inventory_reservations where id=? for update",
                                UUID.class,
                                reservation.reservationId());
                            jdbc.update(
                                "update inventory_reservations set expires_at=clock_timestamp()+interval '300 milliseconds' where id=?",
                                reservation.reservationId());
                            locked.countDown();
                            try {
                              release.await();
                            } catch (InterruptedException e) {
                              throw new RuntimeException(e);
                            }
                            return null;
                          }));
      locked.await();
      var confirmation =
          pool.submit(
              () ->
                  commands.handle(
                      command(
                          "reservation.confirm.requested", reservation.reservationId(), order)));
      Thread.sleep(500);
      release.countDown();
      lock.get();
      confirmation.get();
    }
    assertEquals("EXPIRED", inventory.get(reservation.reservationId(), owner).status());
  }

  @Test
  void sameIdempotencyKeyConcurrentRequestsReturnSameSnapshot() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return inventory.reserve(event, owner, "identical", seats);
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return inventory.reserve(event, owner, "identical", seats);
              });
      start.countDown();
      assertEquals(a.get(), b.get());
    }
    assertEquals(1, jdbc.queryForObject("select count(*) from outbox", Integer.class));
  }

  @Test
  void expiredBindPersistsExpiryBeforeReturningGone() {
    var r = inventory.reserve(event, owner, "expired-bind", seats);
    jdbc.update(
        "update inventory_reservations set expires_at=clock_timestamp() where id=?",
        r.reservationId());
    assertEquals(
        410,
        assertThrows(
                ApiException.class,
                () -> inventory.bind(r.reservationId(), UUID.randomUUID(), owner))
            .status());
    assertEquals(
        "EXPIRED",
        jdbc.queryForObject(
            "select status from inventory_reservations where id=?",
            String.class,
            r.reservationId()));
    assertEquals(
        3,
        jdbc.queryForObject(
            "select count(*) from inventory_seats where id in (?,?,?) and status='AVAILABLE'",
            Integer.class,
            seats.toArray()));
  }

  @Test
  void invalidCommandRollsBackInboxAndHasNoResult() {
    var r = inventory.reserve(event, owner, "invalid", seats);
    var order = UUID.randomUUID();
    inventory.bind(r.reservationId(), order, owner);
    var invalid = command("reservation.confirm.requested", r.reservationId(), order);
    ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.payload()).remove("paymentAttemptId");
    assertThrows(IllegalArgumentException.class, () -> commands.handle(invalid));
    assertEquals(0, jdbc.queryForObject("select count(*) from inbox", Integer.class));
    assertEquals("ACTIVE", inventory.get(r.reservationId(), owner).status());
  }

  @Test
  void wrongOrderCannotConfirmOrCancelReservation() {
    var r = inventory.reserve(event, owner, "wrong-order", seats);
    inventory.bind(r.reservationId(), UUID.randomUUID(), owner);
    commands.handle(command("reservation.confirm.requested", r.reservationId(), UUID.randomUUID()));
    commands.handle(command("reservation.cancel.requested", r.reservationId(), UUID.randomUUID()));
    assertEquals("ACTIVE", inventory.get(r.reservationId(), owner).status());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from outbox where payload::jsonb->>'eventType' like '%-rejected'",
            Integer.class));
  }

  @Test
  void unavailableSeatCannotLeakAnIdempotencyOperation() {
    inventory.reserve(event, owner, "occupied", List.of(seats.getFirst()));
    assertThrows(ApiException.class, () -> inventory.reserve(event, owner, "retry", seats));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from inventory_idempotency where idempotency_key='retry'",
            Integer.class));
  }

  @Test
  void concurrentBindingOfOneOrderToTwoReservationsHasOneWinner() throws Exception {
    var a = inventory.reserve(event, owner, "bind-a", List.of(seats.getFirst()));
    var b = inventory.reserve(event, owner, "bind-b", List.of(seats.get(1)));
    var order = UUID.randomUUID();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                start.await();
                try {
                  inventory.bind(a.reservationId(), order, owner);
                  return true;
                } catch (ApiException ex) {
                  assertEquals(409, ex.status());
                  return false;
                }
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                try {
                  inventory.bind(b.reservationId(), order, owner);
                  return true;
                } catch (ApiException ex) {
                  assertEquals(409, ex.status());
                  return false;
                }
              });
      start.countDown();
      assertNotEquals(first.get(), second.get());
    }
  }

  @Test
  void httpEnforcesOwnerAndInternalServiceRoleAndReturnsSeatSnapshot() throws Exception {
    var customer =
        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .jwt()
            .jwt(j -> j.subject(owner))
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_Cliente"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/inventory/events/" + event)
                .with(customer))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.seats.length()")
                .value(24));
    var r = inventory.reserve(event, owner, "http", seats);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/inventory/reservations/" + r.reservationId())
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject("someone-else"))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_Cliente"))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isNotFound());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/inventory/internal/reservations/" + r.reservationId())
                .with(customer))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/inventory/internal/reservations/" + r.reservationId())
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ServiceOrders"))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.customerId")
                .value(owner));
  }

  @Test
  void cancelledEventRejectsNewConfirmationUnderItsEventLock() {
    var r = inventory.reserve(event, owner, "cancelled-event", seats);
    var order = UUID.randomUUID();
    inventory.bind(r.reservationId(), order, owner);
    jdbc.update("update inventory_events set status='CANCELLED' where id=?", event);
    try {
      commands.handle(command("reservation.confirm.requested", r.reservationId(), order));
      assertNotEquals("CONFIRMED", inventory.get(r.reservationId(), owner).status());
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from inventory_seats where status='SOLD'", Integer.class));
    } finally {
      jdbc.update("update inventory_events set status='PUBLISHED' where id=?", event);
    }
  }

  @Test
  void rejectedCommandCannotAttributeAnotherOrdersConfirmedSeatsToRequester() {
    var r = inventory.reserve(event, owner, "authority", seats);
    var actualOrder = UUID.randomUUID();
    inventory.bind(r.reservationId(), actualOrder, owner);
    commands.handle(command("reservation.confirm.requested", r.reservationId(), actualOrder));
    var wrong = command("reservation.confirm.requested", r.reservationId(), UUID.randomUUID());
    commands.handle(wrong);
    assertEquals(
        actualOrder.toString(),
        jdbc.queryForObject(
            "select payload::jsonb->'payload'->>'orderId' from outbox where payload::jsonb->'payload'->>'commandId'=?",
            String.class,
            wrong.eventId().toString()));
  }

  @Test
  void seededLotIsExposedAndFrozenWithReservationPriceSnapshot() throws Exception {
    var customer =
        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .jwt()
            .jwt(j -> j.subject(owner))
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_Cliente"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/inventory/events/" + event)
                .with(customer))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.seats[0].lotName")
                .value("Lote Demonstração"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.seats[0].lotId")
                .isNotEmpty());
    var r = inventory.reserve(event, owner, "lot-snapshot", seats);
    var original = json.valueToTree(r).path("items").get(0);
    assertEquals("Lote Demonstração", original.path("lotName").asText());
    jdbc.update("update inventory_lots set name='Lote Posterior' where event_id=?", event);
    jdbc.update(
        "update inventory_seats set price_cents=price_cents+12345 where id=?", seats.getFirst());
    try {
      var snapshot = json.valueToTree(inventory.get(r.reservationId(), owner)).path("items").get(0);
      assertEquals("Lote Demonstração", snapshot.path("lotName").asText());
      assertEquals(original.path("priceCents").asLong(), snapshot.path("priceCents").asLong());
    } finally {
      jdbc.update("update inventory_lots set name='Lote Demonstração' where event_id=?", event);
      jdbc.update(
          "update inventory_seats set price_cents=price_cents-12345 where id=?", seats.getFirst());
    }
  }

  @Test
  void reservationCapacityIsDeterminedByAvailabilityWithoutAnArbitraryRequestLimit() {
    var all =
        jdbc.queryForList(
            "select id from inventory_seats where event_id=? order by id", UUID.class, event);
    var extra = UUID.randomUUID();
    jdbc.update(
        "insert into inventory_seats(id,event_id,sector,seat_row,seat_number,price_cents,status,lot_id) select ?,event_id,sector,seat_row,'25',price_cents,'AVAILABLE',lot_id from inventory_seats where id=?",
        extra,
        seats.getFirst());
    try {
      all.add(extra);
      var r = inventory.reserve(event, owner, "twenty-five", all);
      assertEquals(25, r.items().size());
    } finally {
      jdbc.update(
          "update inventory_seats set reservation_id=null,status='AVAILABLE' where event_id=?",
          event);
      jdbc.update(
          "delete from inventory_reservation_items where reservation_id in (select id from inventory_reservations where event_id=?)",
          event);
      jdbc.update("delete from inventory_seats where id=?", extra);
    }
  }

  @Test
  void inactiveLotCannotAcceptNewReservation() {
    jdbc.update("update inventory_lots set active=false where event_id=?", event);
    try {
      assertEquals(
          409,
          assertThrows(
                  ApiException.class, () -> inventory.reserve(event, owner, "inactive-lot", seats))
              .status());
    } finally {
      jdbc.update("update inventory_lots set active=true where event_id=?", event);
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"negativeVersion", "missingProducer", "missingOccurredAt", "missingAggregateType"})
  void invalidEnvelopeNeverClaimsInboxOrMutatesReservation(String invalidField) {
    var r = inventory.reserve(event, owner, "envelope", seats);
    var order = UUID.randomUUID();
    inventory.bind(r.reservationId(), order, owner);
    var valid = command("reservation.confirm.requested", r.reservationId(), order);
    var invalid =
        new EventMessage(
            valid.eventId(),
            valid.eventType(),
            1,
            invalidField.equals("missingAggregateType") ? null : valid.aggregateType(),
            valid.aggregateId(),
            invalidField.equals("negativeVersion") ? -1 : valid.aggregateVersion(),
            invalidField.equals("missingOccurredAt") ? null : valid.occurredAt(),
            invalidField.equals("missingProducer") ? null : valid.producer(),
            null,
            null,
            valid.payload());
    assertThrows(IllegalArgumentException.class, () -> commands.handle(invalid));
    assertEquals(0, jdbc.queryForObject("select count(*) from inbox", Integer.class));
    assertEquals("ACTIVE", inventory.get(r.reservationId(), owner).status());
  }

  EventMessage command(String type, UUID reservation, UUID order) {
    return new EventMessage(
        UUID.randomUUID(),
        type,
        1,
        "reservation",
        reservation,
        1,
        Instant.now(),
        "orders",
        null,
        null,
        json.valueToTree(
            Map.of(
                "reservationId",
                reservation,
                "orderId",
                order,
                "customerId",
                owner,
                "paymentAttemptId",
                UUID.randomUUID())));
  }
}
