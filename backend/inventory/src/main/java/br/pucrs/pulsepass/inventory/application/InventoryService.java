package br.pucrs.pulsepass.inventory.application;

import br.pucrs.pulsepass.common.ApiException;
import br.pucrs.pulsepass.common.Outbox;
import br.pucrs.pulsepass.inventory.api.ReservationSnapshot;
import br.pucrs.pulsepass.inventory.domain.ReservationPolicy;
import br.pucrs.pulsepass.inventory.domain.ReservationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class InventoryService {
    static final String TOPIC="pulsepass.inventory.reservations.v1";
    private final InventoryStore store;
    private final Outbox outbox;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    InventoryService(InventoryStore store,Outbox outbox,ObjectMapper json,PlatformTransactionManager manager) {
        this.store=store;this.outbox=outbox;this.json=json;this.tx=new TransactionTemplate(manager);
    }
    public List<Map<String,Object>> catalog() { return store.catalog(); }
    public Map<String,Object> catalogEvent(UUID id) { return store.catalogEvent(id); }
    public ReservationSnapshot reserve(UUID event,String customer,String key,List<UUID> seatIds) {
        if(customer==null || customer.isBlank()) throw new ApiException(401,"Cliente obrigatório");
        if(key==null || key.isBlank() || key.length()>200) throw new ApiException(422,"Idempotency-Key obrigatório, até 200 caracteres");
        if(seatIds==null || seatIds.isEmpty() || seatIds.stream().anyMatch(Objects::isNull) || new HashSet<>(seatIds).size()!=seatIds.size()) throw new ApiException(422,"Informe assentos distintos");
        var sorted=seatIds.stream().sorted().toList();
        var route="/api/inventory/events/"+event+"/reservations";
        var hash=hash(sorted.toString());
        return retry(()->reserveInTransaction(event,customer,key,route,hash,sorted));
    }
    private ReservationSnapshot reserveInTransaction(UUID eventId,String customer,String key,String route,String hash,List<UUID> requested) {
        store.jdbc().update("insert into inventory_idempotency(customer_id,route,idempotency_key,payload_hash) values(?,?,?,?) on conflict do nothing",customer,route,key,hash);
        var previous=store.jdbc().queryForMap("select payload_hash,response::text as response from inventory_idempotency where customer_id=? and route=? and idempotency_key=? for update",customer,route,key);
        if(!hash.equals(previous.get("payload_hash"))) throw new ApiException(409,"Chave reutilizada com outro corpo");
        if(previous.get("response")!=null) return decode(previous.get("response").toString());
        var event=store.event(eventId,true);
        if(!event.status().equals("PUBLISHED")) throw new ApiException(409,"Evento indisponível");
        var observed=store.seats(requested,false);
        if(observed.size()!=requested.size() || observed.stream().anyMatch(s->!s.eventId().equals(eventId))) throw new ApiException(422,"Assento não pertence ao evento");
        // Read a plan, lock every observed reservation in deterministic order, then lock the complete seat set.
        // A changed occupation after the plan causes a fresh transaction, never an out-of-order reservation lock.
        var owners=observed.stream().map(InventoryStore.Seat::reservationId).filter(Objects::nonNull).distinct().sorted().toList();
        var lockedReservations=new ArrayList<InventoryStore.Reservation>();
        var lockSeats=new HashSet<>(requested);
        for(var owner:owners) {var r=store.reservation(owner,true);lockedReservations.add(r);lockSeats.addAll(store.itemSeats(owner));}
        var allSeats=store.seats(lockSeats,true);
        var seats=allSeats.stream().filter(s->requested.contains(s.id())).toList();
        if(seats.stream().anyMatch(s->s.reservationId()!=null && !owners.contains(s.reservationId()))) throw new OccupationChanged();
        Instant now=store.now();
        for(var reservation:lockedReservations) transition(reservation,ReservationPolicy.expire(reservation.status(),reservation.expiresAt(),now),null);
        seats=store.seats(requested,false);
        if(seats.stream().anyMatch(s->!s.status().equals("AVAILABLE") || !s.lotActive())) throw new ApiException(409,"Um ou mais assentos estão indisponíveis");
        var id=UUID.randomUUID();
        long total=seats.stream().mapToLong(InventoryStore.Seat::priceCents).sum();
        store.jdbc().update("insert into inventory_reservations(id,event_id,customer_id,status,expires_at,version,event_name,event_starts_at,venue,currency,total_cents) values(?,?,?,'ACTIVE',?,1,?,?,?,?,?)",id,eventId,customer,java.sql.Timestamp.from(now.plusSeconds(600)),event.name(),java.sql.Timestamp.from(event.startsAt()),event.venue(),event.currency(),total);
        for(var seat:seats) {
            store.jdbc().update("insert into inventory_reservation_items(id,reservation_id,seat_id,sector,seat_row,seat_number,price_cents,lot_id,lot_name) values(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,seat.id(),seat.sector(),seat.row(),seat.number(),seat.priceCents(),seat.lotId(),seat.lotName());
            store.jdbc().update("update inventory_seats set status='RESERVED',reservation_id=? where id=?",id,seat.id());
        }
        var snapshot=store.snapshot(id);outbox.add(TOPIC,"reservation.created","reservation",id,1,snapshot);
        store.jdbc().update("update inventory_idempotency set response=?::jsonb where customer_id=? and route=? and idempotency_key=?",encode(snapshot),customer,route,key);
        return snapshot;
    }
    public ReservationSnapshot get(UUID id,String customer) {
        return retry(()->{
            var reservation=lock(id);
            if(customer!=null && !customer.equals(reservation.customerId())) throw new ApiException(404,"Reserva não encontrada");
            transition(reservation,ReservationPolicy.expire(reservation.status(),reservation.expiresAt(),store.now()),null);
            return store.snapshot(id);
        });
    }
    public ReservationSnapshot bind(UUID id,UUID order,String customer) {
        if(order==null || customer==null || customer.isBlank()) throw new ApiException(422,"Pedido e cliente obrigatórios");
        var snapshot=retry(()->{
            var reservation=lock(id);
            if(!customer.equals(reservation.customerId())) throw new ApiException(404,"Reserva não encontrada");
            var status=ReservationPolicy.expire(reservation.status(),reservation.expiresAt(),store.now());
            transition(reservation,status,null);
            if(status!=ReservationStatus.ACTIVE) return store.snapshot(id);
            if(reservation.orderId()!=null) {
                if(!reservation.orderId().equals(order)) throw new ApiException(409,"Reserva vinculada a outro pedido");
                return store.snapshot(id);
            }
            // Uniqueness per order is protected by the database as well as the reservation lock.
            var other=store.jdbc().queryForList("select id from inventory_reservations where order_id=?",UUID.class,order);
            if(!other.isEmpty()) throw new ApiException(409,"Pedido já vinculado a outra reserva");
            store.jdbc().update("update inventory_reservations set order_id=?,version=version+1 where id=?",order,id);
            var bound=store.snapshot(id);outbox.add(TOPIC,"reservation.bound","reservation",id,bound.version(),bound);return bound;
        });
        if(snapshot.status().equals("EXPIRED")) throw new ApiException(410,"Reserva expirada");
        if(!snapshot.status().equals("ACTIVE")) throw new ApiException(409,"Reserva já finalizada");
        return snapshot;
    }
    public void expire(UUID id) { retry(()->{var r=lock(id);transition(r,ReservationPolicy.expire(r.status(),r.expiresAt(),store.now()),null);return null;}); }
    public List<UUID> due() {return store.jdbc().queryForList("select id from inventory_reservations where status='ACTIVE' and expires_at<=clock_timestamp() order by expires_at limit 100",UUID.class);}
    InventoryStore.Reservation lock(UUID id) {
        var observed=store.reservation(id,false);
        store.event(observed.eventId(),true);
        var reservation=store.reservation(id,true);store.seats(store.itemSeats(id),true);return reservation;
    }
    void transition(InventoryStore.Reservation reservation,ReservationStatus status,UUID commandId) {
        if(status==reservation.status()) return;
        store.jdbc().update("update inventory_reservations set status=?,version=version+1 where id=?",status.name(),reservation.id());
        if(status==ReservationStatus.CONFIRMED) {
            int sold=store.jdbc().update("update inventory_seats set status='SOLD' where reservation_id=? and status='RESERVED'",reservation.id());
            if(sold!=store.itemSeats(reservation.id()).size()) throw new IllegalStateException("Reservation occupation invariant violated");
        } else {
            // This predicate is essential: a delayed release never clears a newer owner's occupation.
            store.jdbc().update("update inventory_seats set status='AVAILABLE',reservation_id=null where reservation_id=? and status='RESERVED'",reservation.id());
        }
        var current=store.reservation(reservation.id(),false);
        outbox.add(TOPIC,"reservation."+status.name().toLowerCase(Locale.ROOT),"reservation",current.id(),current.version(),result(current,commandId));
    }
    Map<String,Object> result(InventoryStore.Reservation r,UUID commandId) {
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("reservationId",r.id());payload.put("orderId",r.orderId());payload.put("customerId",r.customerId());payload.put("status",r.status().name());payload.put("version",r.version());payload.put("expiresAt",r.expiresAt());payload.put("commandId",commandId);return payload;
    }
    private <T> T retry(Supplier<T> work) {
        for(int attempt=0;;attempt++) {
            try {return tx.execute(ignored->work.get());}
            catch(org.springframework.dao.DuplicateKeyException e) {throw new ApiException(409,"Vínculo já utilizado");}
            catch(OccupationChanged | TransientDataAccessException e) {if(attempt>=3) throw new ApiException(409,"Concorrência temporária; repita com a mesma chave");}
        }
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Snapshot serialization failed",e);}}
    private ReservationSnapshot decode(String value) {try{return json.readValue(value,ReservationSnapshot.class);}catch(JsonProcessingException e){throw new IllegalStateException("Snapshot serialization failed",e);}}
    private String hash(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static final class OccupationChanged extends RuntimeException { }
}
