package br.pucrs.pulsepass.gateway.infrastructure.persistence;

import br.pucrs.pulsepass.gateway.application.GatewayStore;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGatewayStore implements GatewayStore {
  private final JdbcTemplate db;

  public JdbcGatewayStore(JdbcTemplate db) {
    this.db = db;
  }

  public void insert(UUID id, String outcome) {
    db.update(
        "INSERT INTO gateway_attempt(attempt_id,outcome) VALUES (?,?) ON CONFLICT DO NOTHING",
        id,
        outcome);
  }

  public Map<String, Object> attempt(UUID id) {
    return db.queryForMap(
        "SELECT *,clock_timestamp() AS now FROM gateway_attempt WHERE attempt_id=?", id);
  }

  public List<Map<String, Object>> find(UUID id) {
    return db.queryForList(
        "SELECT *,clock_timestamp() AS now FROM gateway_attempt WHERE attempt_id=?", id);
  }
}
