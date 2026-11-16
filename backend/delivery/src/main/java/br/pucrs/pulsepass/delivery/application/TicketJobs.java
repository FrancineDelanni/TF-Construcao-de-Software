package br.pucrs.pulsepass.delivery.application;

import br.pucrs.pulsepass.common.messaging.Outbox;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketJobs {
  private final TicketStore store;
  private final Outbox outbox;

  public TicketJobs(TicketStore store, Outbox outbox) {
    this.store = store;
    this.outbox = outbox;
  }

  @Transactional
  public Optional<Map<String, Object>> claim() {
    var jobs = store.nextJob();
    if (jobs.isEmpty()) return Optional.empty();
    UUID lease = UUID.randomUUID();
    Object id = jobs.getFirst().get("id");
    store.lease((UUID) id, lease);
    return Optional.of(store.job((UUID) id));
  }

  @Transactional
  public void complete(UUID id, UUID lease, byte[] pdf) {
    var rows = store.complete(id, lease, pdf);
    if (rows.isEmpty()) return;
    var t = rows.getFirst();
    outbox.add(
        "pulsepass.delivery.events.v1",
        "ticket.issued",
        "order",
        (UUID) t.get("order_id"),
        1,
        Map.of(
            "ticketId",
            id,
            "orderId",
            t.get("order_id"),
            "orderItemId",
            t.get("order_item_id"),
            "eventId",
            t.get("event_id")));
  }

  @Transactional
  public void failed(UUID id, UUID lease) {
    store.failed(id, lease);
  }
}
