package br.pucrs.pulsepass.common.messaging;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@SpringBootTest(
    classes = MessagingRecoveryIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.application.name=messaging-it",
      "spring.flyway.locations=classpath:db/common",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.properties.max.block.ms=3000",
      "spring.kafka.producer.properties.delivery.timeout.ms=3000",
      "spring.kafka.producer.properties.request.timeout.ms=2000",
      "spring.kafka.producer.properties.linger.ms=0"
    })
class MessagingRecoveryIT {
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({Outbox.class, OutboxPublisher.class, Inbox.class, Quarantine.class})
  static class App {}

  @Container static PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");
  @Container static KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
    r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Autowired Quarantine quarantine;
  @Autowired Outbox outbox;
  @Autowired Inbox inbox;
  @Autowired OutboxPublisher publisher;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    jdbc.execute("TRUNCATE outbox,inbox,consumer_quarantine RESTART IDENTITY");
  }

  @Test
  void atomicRollbackBrokerOutageRecoveryAndStableMessageId() throws Exception {
    String topic = "recovery-" + UUID.randomUUID();
    try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
      admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
    }
    var tx = new TransactionTemplate(manager);
    jdbc.execute("CREATE TABLE effect(id UUID PRIMARY KEY)");
    UUID aggregate = UUID.randomUUID();
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.execute(
                s -> {
                  jdbc.update("INSERT INTO effect VALUES (?)", aggregate);
                  outbox.add(
                      topic, "order.paid", "order", aggregate, 1, Map.of("orderId", aggregate));
                  throw new IllegalStateException("rollback");
                }));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM effect", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class));
    UUID eventId =
        tx.execute(
            s -> {
              jdbc.update("INSERT INTO effect VALUES (?)", aggregate);
              return outbox.add(
                  topic, "order.paid", "order", aggregate, 1, Map.of("orderId", aggregate));
            });
    KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    try {
      assertFalse(publisher.publishNext());
      assertNull(jdbc.queryForObject("SELECT sent_at FROM outbox", java.sql.Timestamp.class));
    } finally {
      KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }
    jdbc.update("UPDATE outbox SET next_attempt_at=clock_timestamp()");
    boolean sent = false;
    long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
    while (!sent && System.nanoTime() < deadline) {
      sent = publisher.publishNext();
      if (!sent) {
        Thread.sleep(200);
        jdbc.update("UPDATE outbox SET next_attempt_at=clock_timestamp()");
      }
    }
    assertTrue(sent, "outbox deve recuperar após broker voltar");
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of(
                "bootstrap.servers",
                KAFKA.getBootstrapServers(),
                "group.id",
                UUID.randomUUID().toString(),
                "auto.offset.reset",
                "earliest",
                "enable.auto.commit",
                false,
                "key.deserializer",
                StringDeserializer.class,
                "value.deserializer",
                StringDeserializer.class))) {
      consumer.subscribe(List.of(topic));
      List<EventMessage> received = new ArrayList<>();
      deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (received.isEmpty() && System.nanoTime() < deadline) {
        for (var record : consumer.poll(Duration.ofMillis(300)))
          received.add(json.readValue(record.value(), EventMessage.class));
      }
      assertFalse(received.isEmpty());
      assertTrue(received.stream().allMatch(e -> eventId.equals(e.eventId())));
    }
    UUID message = UUID.randomUUID();
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.execute(
                s -> {
                  inbox.claim("fixture", message);
                  throw new IllegalStateException();
                }));
    assertEquals(Boolean.TRUE, tx.execute(s -> inbox.claim("fixture", message)));
    assertEquals(Boolean.FALSE, tx.execute(s -> inbox.claim("fixture", message)));
  }

  @Test
  void quarantineBlocksLaterAggregateUntilSuccessfulReplayCommit() throws Exception {
    var tx = new TransactionTemplate(manager);
    UUID aggregate = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
    var initial =
        new EventMessage(
            first,
            "reservation.confirm.requested",
            1,
            "reservation",
            aggregate,
            1,
            java.time.Instant.now(),
            "orders",
            first,
            null,
            json.valueToTree(Map.of("reservationId", aggregate)));
    var later =
        new EventMessage(
            second,
            "reservation.cancel.requested",
            1,
            "reservation",
            aggregate,
            2,
            java.time.Instant.now(),
            "orders",
            second,
            null,
            initial.payload());
    var r1 =
        new ConsumerRecord<String, String>(
            "commands", 0, 1, aggregate.toString(), json.writeValueAsString(initial));
    var r2 =
        new ConsumerRecord<String, String>(
            "commands", 0, 2, aggregate.toString(), json.writeValueAsString(later));
    quarantine.recover("inventory-commands-v1", r1, new IllegalArgumentException("fixture"));
    quarantine.recover("inventory-commands-v1", r1, new IllegalArgumentException("fixture"));
    assertThrows(IllegalStateException.class, () -> quarantine.check("inventory-commands-v1", r2));
    assertDoesNotThrow(() -> quarantine.check("inventory-commands-v1", r1));
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.execute(
                s -> {
                  inbox.claim("inventory-commands-v1", first);
                  throw new IllegalStateException("rollback");
                }));
    assertThrows(IllegalStateException.class, () -> quarantine.check("inventory-commands-v1", r2));
    tx.execute(s -> inbox.claim("inventory-commands-v1", first));
    assertDoesNotThrow(() -> quarantine.check("inventory-commands-v1", r2));
  }
}
