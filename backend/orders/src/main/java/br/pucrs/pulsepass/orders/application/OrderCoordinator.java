package br.pucrs.pulsepass.orders.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Remote HTTP calls finish before entering transactional OrderService methods. */
@Service
public class OrderCoordinator {
  private static final Logger log = LoggerFactory.getLogger(OrderCoordinator.class);
  private final OrderService service;
  private final InventoryClient inventory;

  public OrderCoordinator(OrderService service, InventoryClient inventory) {
    this.service = service;
    this.inventory = inventory;
  }

  public com.fasterxml.jackson.databind.JsonNode bind(UUID id, UUID reservation, String owner) {
    com.fasterxml.jackson.databind.JsonNode snapshot;
    try {
      snapshot = inventory.bind(reservation, id, owner);
    } catch (org.springframework.web.client.HttpClientErrorException rejected) {
      try {
        snapshot = inventory.reservation(reservation);
      } catch (org.springframework.web.client.HttpClientErrorException.NotFound missing) {
        service.rejectIntent(id);
        throw new br.pucrs.pulsepass.common.web.ApiException(404, "Reserva não encontrada");
      }
    }
    if (!owner.equals(snapshot.path("customerId").asText())) {
      service.rejectIntent(id);
      throw new br.pucrs.pulsepass.common.web.ApiException(404, "Reserva não encontrada");
    }
    return snapshot;
  }

  public void start(UUID id, UUID reservation, String owner) {
    try {
      service.snapshot(id, bind(id, reservation, owner));
    } catch (br.pucrs.pulsepass.common.web.ApiException business) {
      throw business;
    } catch (RuntimeException pending) {
      log.warn("Vinculação de pedido pendente: {}", id);
    }
  }
}
