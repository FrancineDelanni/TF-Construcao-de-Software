package br.pucrs.pulsepass.common.messaging;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "pulsepass.messaging.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
    this.jdbc = jdbc;
    this.kafka = kafka;
  }

  // Single ordered publisher per database: stronger than per-aggregate ordering, intentionally
  // simple locally.
  @Transactional
  public boolean publishNext() {
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(7027301)", Boolean.class)))
      return false;
    var pending =
        jdbc.queryForList(
            "SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY seq LIMIT 1 FOR UPDATE");
    if (pending.isEmpty()) return false;
    var row = pending.getFirst();
    Boolean due =
        jdbc.queryForObject(
            "SELECT next_attempt_at<=clock_timestamp() FROM outbox WHERE seq=?",
            Boolean.class,
            row.get("seq"));
    if (!Boolean.TRUE.equals(due)) return false;
    try {
      kafka
          .send(
              row.get("topic").toString(),
              row.get("aggregate_id").toString(),
              row.get("payload").toString())
          .get(6, TimeUnit.SECONDS);
      jdbc.update(
          "UPDATE outbox SET sent_at=clock_timestamp(),attempts=attempts+1 WHERE seq=?",
          row.get("seq"));
      return true;
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      jdbc.update(
          "UPDATE outbox SET attempts=attempts+1,next_attempt_at=clock_timestamp()+interval '2 seconds' WHERE seq=?",
          row.get("seq"));
      LOG.warn("Outbox aguardando recuperação do broker; mensagem {}", row.get("event_id"));
      return false;
    }
  }
}
