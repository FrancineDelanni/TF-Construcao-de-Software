package br.pucrs.pulsepass.orders.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface InventoryClient {
  JsonNode bind(UUID reservation, UUID order, String owner);

  JsonNode reservation(UUID reservation);
}
