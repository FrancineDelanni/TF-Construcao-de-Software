package br.pucrs.pulsepass.inventory.application;

import br.pucrs.pulsepass.common.EventMessage;
import br.pucrs.pulsepass.common.Inbox;
import br.pucrs.pulsepass.common.Outbox;
import br.pucrs.pulsepass.inventory.domain.ReservationPolicy;
import br.pucrs.pulsepass.inventory.domain.ReservationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Called through a separate Spring bean: inbox, decision and outbox share one local commit. */
@Service
public class InventoryCommands {
    public static final String CONSUMER="inventory-commands-v1";
    private final InventoryService inventory;
    private final InventoryStore store;
    private final Inbox inbox;
    private final Outbox outbox;
    InventoryCommands(InventoryService inventory,InventoryStore store,Inbox inbox,Outbox outbox) {
        this.inventory=inventory;this.store=store;this.inbox=inbox;this.outbox=outbox;
    }
    @Transactional
    public void handle(EventMessage message) {
        validateEnvelope(message);
        if(!inbox.claim(CONSUMER,message.eventId())) return;
        boolean confirm="reservation.confirm.requested".equals(message.eventType());
        boolean cancel="reservation.cancel.requested".equals(message.eventType());
        if(!confirm && !cancel) return;
        var reservationId=uuid(message.payload(),"reservationId");
        var orderId=uuid(message.payload(),"orderId");
        var customerId=message.payload().path("customerId").asText();
        if(customerId.isBlank() || !reservationId.equals(message.aggregateId())) throw new IllegalArgumentException("Invalid command identity");
        if(confirm) uuid(message.payload(),"paymentAttemptId");
        var r=inventory.lock(reservationId);
        var now=store.now();
        var effective=ReservationPolicy.expire(r.status(),r.expiresAt(),now);
        boolean matches=orderId.equals(r.orderId()) && customerId.equals(r.customerId());
        boolean published=store.event(r.eventId(),false).status().equals("PUBLISHED");
        boolean applicable=matches && (!confirm || published);
        var next=applicable ? (confirm?ReservationPolicy.confirm(effective,r.expiresAt(),now):ReservationPolicy.cancel(effective,r.expiresAt(),now)) : effective;
        if(confirm && !published && effective==ReservationStatus.ACTIVE) next=ReservationStatus.CANCELLED;
        inventory.transition(r,next,message.eventId());
        var current=store.reservation(r.id(),false);
        boolean accepted=applicable && current.status()==(confirm?ReservationStatus.CONFIRMED:ReservationStatus.CANCELLED);
        // A state-changing transition already emitted the authoritative accepted event.
        if(!accepted || r.status()==current.status()) {
            var type=accepted?(confirm?"reservation.confirmed":"reservation.cancelled"):(confirm?"reservation.confirmation-rejected":"reservation.cancellation-rejected");
            var payload=inventory.result(current,message.eventId());
            outbox.add(InventoryService.TOPIC,type,"reservation",r.id(),current.version(),payload);
        }
    }
    private void validateEnvelope(EventMessage message) {
        if(message==null) throw new IllegalArgumentException("Missing event envelope");
        message.validate();
    }
    private UUID uuid(JsonNode payload,String key) {
        try {return UUID.fromString(payload.path(key).asText());}catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid command field: "+key);}
    }
}
