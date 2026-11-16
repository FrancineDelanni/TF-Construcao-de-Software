package br.pucrs.pulsepass.common.messaging;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "pulsepass.messaging.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class Inbox {
  private final JdbcTemplate jdbc;

  public Inbox(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean claim(String consumer, UUID eventId) {
    boolean fresh =
        jdbc.update(
                "INSERT INTO inbox(consumer,event_id) VALUES (?,?) ON CONFLICT DO NOTHING",
                consumer,
                eventId)
            == 1;
    jdbc.update(
        "UPDATE consumer_quarantine SET resolved_at=clock_timestamp() WHERE consumer=? AND event_id=? AND resolved_at IS NULL",
        consumer,
        eventId);
    return fresh;
  }
}
