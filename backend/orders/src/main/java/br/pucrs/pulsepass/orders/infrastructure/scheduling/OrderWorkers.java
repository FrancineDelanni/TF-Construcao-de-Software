package br.pucrs.pulsepass.orders.infrastructure.scheduling;

import br.pucrs.pulsepass.orders.application.*;
import br.pucrs.pulsepass.orders.application.InventoryClient;
import br.pucrs.pulsepass.orders.application.OrderCoordinator;
import br.pucrs.pulsepass.orders.application.OrderService;
import br.pucrs.pulsepass.orders.application.PaymentClient;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderWorkers {
  private static final Logger log = LoggerFactory.getLogger(OrderWorkers.class);
  private final OrderService service;
  private final InventoryClient inventory;
  private final PaymentClient payment;
  private final OrderCoordinator coordinator;

  @org.springframework.beans.factory.annotation.Value("${pulsepass.orders.workers:true}")
  private boolean enabled;

  public OrderWorkers(
      OrderService service,
      InventoryClient inventory,
      PaymentClient payment,
      OrderCoordinator coordinator) {
    this.service = service;
    this.inventory = inventory;
    this.payment = payment;
    this.coordinator = coordinator;
  }

  // Every HTTP request completes before entering the transactional service method.
  @Scheduled(fixedDelayString = "${pulsepass.orders.reconcile-ms:1000}")
  public void reconcile() {
    if (!enabled) return;
    for (var row : service.workOrders())
      try {
        UUID id = (UUID) row.get("id"), reservation = (UUID) row.get("reservation_id");
        com.fasterxml.jackson.databind.JsonNode snapshot;
        if ("CREATED".equals(row.get("state"))) {
          snapshot = coordinator.bind(id, reservation, (String) row.get("customer_id"));
        } else snapshot = inventory.reservation(reservation);
        service.snapshot(id, snapshot);
      } catch (RuntimeException failure) {
        log.warn("Reconciliação de pedido pendente: {}", row.get("id"));
      }
  }

  @Scheduled(fixedDelayString = "${pulsepass.orders.payment-ms:1000}")
  public void payments() {
    if (!enabled) return;
    for (var attempt : service.workPayments())
      try {
        var result = payment.payment((UUID) attempt.get("id"), (String) attempt.get("outcome"));
        service.paymentResult((UUID) attempt.get("id"), result.path("status").asText());
      } catch (RuntimeException failure) {
        log.warn("Pagamento pendente: {}", attempt.get("id"));
      }
  }
}
