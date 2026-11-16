package br.pucrs.pulsepass.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "pulsepass.messaging.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class Quarantine {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Outbox outbox;

  public Quarantine(JdbcTemplate jdbc, ObjectMapper json, Outbox outbox) {
    this.jdbc = jdbc;
    this.json = json;
    this.outbox = outbox;
  }

  public void check(String consumer, ConsumerRecord<String, String> record) {
    try {
      var message = json.readValue(record.value(), EventMessage.class);
      if (message.aggregateId() == null || message.eventId() == null) return;
      var first =
          jdbc.queryForList(
              "SELECT event_id FROM consumer_quarantine WHERE consumer=? AND aggregate_id=? AND resolved_at IS NULL ORDER BY created_at,offset_id LIMIT 1",
              consumer,
              message.aggregateId().toString());
      if (!first.isEmpty() && !first.getFirst().get("event_id").equals(message.eventId()))
        throw new IllegalStateException("Agregado bloqueado; reprocessar mensagem anterior");
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      /* listener validates malformed record */
    }
  }

  @Transactional
  public void recover(String consumer, ConsumerRecord<?, ?> record, Exception failure) {
    String payload = String.valueOf(record.value());
    UUID id =
        UUID.nameUUIDFromBytes(
            (record.topic() + ":" + record.partition() + ":" + record.offset())
                .getBytes(StandardCharsets.UTF_8));
    String aggregate = String.valueOf(record.key());
    try {
      var message = json.readValue(payload, EventMessage.class);
      if (message.eventId() != null) id = message.eventId();
      if (message.aggregateId() != null) aggregate = message.aggregateId().toString();
    } catch (Exception ignored) {
    }
    int inserted =
        jdbc.update(
            "INSERT INTO consumer_quarantine(consumer,event_id,aggregate_id,topic,partition_id,offset_id,payload) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
            consumer,
            id,
            aggregate,
            record.topic(),
            record.partition(),
            record.offset(),
            payload);
    if (inserted == 1)
      outbox.add(
          "pulsepass.dlq." + consumer,
          "consumer.quarantined",
          "quarantine",
          UUID.nameUUIDFromBytes(aggregate.getBytes(StandardCharsets.UTF_8)),
          0,
          Map.of(
              "consumer",
              consumer,
              "original",
              payload,
              "topic",
              record.topic(),
              "partition",
              record.partition(),
              "offset",
              record.offset(),
              "error",
              "Falha técnica; consulte logs sanitizados e corrija antes de reprocessar"));
  }
}
