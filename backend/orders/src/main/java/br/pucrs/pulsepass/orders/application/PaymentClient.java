package br.pucrs.pulsepass.orders.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface PaymentClient {
  JsonNode payment(UUID attempt, String outcome);
}
