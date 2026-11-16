package br.pucrs.pulsepass.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventMessage(
    UUID eventId,
    String eventType,
    int schemaVersion,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    Instant occurredAt,
    String producer,
    UUID correlationId,
    UUID causationId,
    JsonNode payload) {
  public void validate() {
    if (schemaVersion != 1
        || eventId == null
        || aggregateId == null
        || aggregateVersion < 0
        || eventType == null
        || eventType.isBlank()
        || payload == null
        || !payload.isObject()
        || aggregateType == null
        || occurredAt == null
        || producer == null) {
      throw new IllegalArgumentException("Envelope de evento inválido ou versão não suportada");
    }
  }
}
