package br.pucrs.pulsepass.gateway.application;

import br.pucrs.pulsepass.common.web.ApiException;
import br.pucrs.pulsepass.gateway.domain.GatewayDecision;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class GatewayService {
  private final GatewayStore store;

  public GatewayService(GatewayStore store) {
    this.store = store;
  }

  @Transactional
  public Map<String, Object> create(UUID attemptId, String outcome) {
    if (attemptId == null
        || !Set.of("APPROVE", "DECLINE", "TIMEOUT").contains(Objects.toString(outcome, "")))
      throw new ApiException(400, "Tentativa e resultado válidos são obrigatórios");
    store.insert(attemptId, outcome);
    Map<String, Object> row = store.attempt(attemptId);
    if (!outcome.equals(row.get("outcome")))
      throw new ApiException(409, "Tentativa já utilizada com outro resultado");
    return response(row);
  }

  public Map<String, Object> get(UUID id) {
    var rows = store.find(id);
    if (rows.isEmpty()) throw new ApiException(404, "Tentativa não encontrada");
    return response(rows.getFirst());
  }

  private Map<String, Object> response(Map<String, Object> row) {
    return Map.of(
        "attemptId",
        row.get("attempt_id"),
        "status",
        GatewayDecision.status(
            (String) row.get("outcome"),
            ((java.sql.Timestamp) row.get("created_at")).toInstant(),
            ((java.sql.Timestamp) row.get("now")).toInstant()));
  }
}
