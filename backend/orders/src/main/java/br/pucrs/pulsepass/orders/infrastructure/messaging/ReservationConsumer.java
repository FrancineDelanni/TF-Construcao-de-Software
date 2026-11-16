package br.pucrs.pulsepass.orders.infrastructure.messaging;

import br.pucrs.pulsepass.common.messaging.EventMessage;
import br.pucrs.pulsepass.orders.application.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReservationConsumer {
  private final ObjectMapper json;
  private final OrderService orders;

  public ReservationConsumer(ObjectMapper json, OrderService orders) {
    this.json = json;
    this.orders = orders;
  }

  @KafkaListener(topics = "pulsepass.inventory.reservations.v1", groupId = "orders-reservations-v1")
  public void receive(String raw) throws Exception {
    orders.consume(json.readValue(raw, EventMessage.class));
  }
}
