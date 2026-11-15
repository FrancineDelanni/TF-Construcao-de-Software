package br.pucrs.pulsepass.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="pulsepass.messaging.enabled",havingValue="true",matchIfMissing=true)
public class Outbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String producer;
    public Outbox(JdbcTemplate jdbc, ObjectMapper json, @Value("${spring.application.name}") String producer) {
        this.jdbc=jdbc; this.json=json; this.producer=producer;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID add(String topic, String eventType, String aggregateType, UUID aggregateId, long version, Object payload) {
        var id=UUID.randomUUID();
        var message=new EventMessage(id,eventType,1,aggregateType,aggregateId,version,Instant.now(),producer,id,null,json.valueToTree(payload));
        try {
            jdbc.update("INSERT INTO outbox(event_id,aggregate_id,aggregate_version,topic,payload) VALUES (?,?,?,?,?)",
                id,aggregateId,version,topic,json.writeValueAsString(message));
        } catch (JsonProcessingException ex) { throw new IllegalArgumentException("Payload inválido",ex); }
        return id;
    }
}
