package br.pucrs.pulsepass.inventory.infrastructure.messaging;

import br.pucrs.pulsepass.common.messaging.EventMessage;
import br.pucrs.pulsepass.inventory.application.InventoryCommands;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandConsumer {
  private final ObjectMapper json;
  private final InventoryCommands commands;

  InventoryCommandConsumer(ObjectMapper json, InventoryCommands commands) {
    this.json = json;
    this.commands = commands;
  }

  @KafkaListener(topics = "pulsepass.inventory.commands.v1", groupId = InventoryCommands.CONSUMER)
  public void receive(String raw) throws JsonProcessingException {
    commands.handle(json.readValue(raw, EventMessage.class));
  }
}
