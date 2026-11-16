package br.pucrs.pulsepass.delivery.infrastructure.messaging;

import br.pucrs.pulsepass.common.messaging.EventMessage;
import br.pucrs.pulsepass.delivery.application.PaidOrderHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaidOrderConsumer {
  private final ObjectMapper json;
  private final PaidOrderHandler handler;

  public PaidOrderConsumer(ObjectMapper json, PaidOrderHandler handler) {
    this.json = json;
    this.handler = handler;
  }

  @KafkaListener(topics = "pulsepass.orders.events.v1", groupId = PaidOrderHandler.GROUP)
  public void consume(String raw) throws Exception {
    handler.accept(json.readValue(raw, EventMessage.class));
  }
}
