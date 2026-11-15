package br.pucrs.pulsepass.inventory.application;

import br.pucrs.pulsepass.common.ApiException;
import br.pucrs.pulsepass.inventory.api.ReservationSnapshot;
import br.pucrs.pulsepass.inventory.domain.ReservationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class InventoryStore {
    private final JdbcTemplate jdbc;
    InventoryStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public JdbcTemplate jdbc() { return jdbc; }
    record Event(UUID id,String name,Instant startsAt,String venue,String status,String currency) { }
    record Seat(UUID id,UUID eventId,String sector,String row,String number,long priceCents,String status,UUID reservationId,UUID lotId,String lotName,boolean lotActive) { }
    record Reservation(UUID id,UUID eventId,String customerId,ReservationStatus status,Instant expiresAt,UUID orderId,long version) { }
    static final RowMapper<Seat> SEAT=(r,n)->new Seat(r.getObject("id",UUID.class),r.getObject("event_id",UUID.class),r.getString("sector"),r.getString("seat_row"),r.getString("seat_number"),r.getLong("price_cents"),r.getString("status"),r.getObject("reservation_id",UUID.class),r.getObject("lot_id",UUID.class),r.getString("lot_name"),r.getBoolean("lot_active"));
    static final RowMapper<Reservation> RESERVATION=(r,n)->new Reservation(r.getObject("id",UUID.class),r.getObject("event_id",UUID.class),r.getString("customer_id"),ReservationStatus.valueOf(r.getString("status")),instant(r,"expires_at"),r.getObject("order_id",UUID.class),r.getLong("version"));
    static Instant instant(ResultSet r,String field) throws SQLException { return r.getTimestamp(field).toInstant(); }
    Instant now() { return jdbc.queryForObject("select clock_timestamp()", java.sql.Timestamp.class).toInstant(); }
    Event event(UUID id,boolean lock) {
        var events=jdbc.query("select * from inventory_events where id=?"+(lock?" for share":""),(r,n)->new Event(r.getObject("id",UUID.class),r.getString("name"),instant(r,"starts_at"),r.getString("venue"),r.getString("status"),r.getString("currency")),id);
        if(events.isEmpty()) throw new ApiException(404,"Evento não encontrado");return events.getFirst();
    }
    Reservation reservation(UUID id,boolean lock) {
        var reservations=jdbc.query("select * from inventory_reservations where id=?"+(lock?" for update":""),RESERVATION,id);
        if(reservations.isEmpty()) throw new ApiException(404,"Reserva não encontrada");return reservations.getFirst();
    }
    List<Seat> seats(Collection<UUID> ids,boolean lock) {
        if(ids.isEmpty()) return List.of();
        return jdbc.query("select s.*,l.name as lot_name,l.active as lot_active from inventory_seats s join inventory_lots l on l.id=s.lot_id where s.id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by s.id"+(lock?" for update of s":""),SEAT,ids.toArray());
    }
    List<UUID> itemSeats(UUID reservation) { return jdbc.queryForList("select seat_id from inventory_reservation_items where reservation_id=? order by seat_id",UUID.class,reservation); }
    ReservationSnapshot snapshot(UUID id) {
        var items=jdbc.query("select * from inventory_reservation_items where reservation_id=? order by seat_id",(r,n)->new ReservationSnapshot.Item(r.getObject("id",UUID.class),r.getObject("seat_id",UUID.class),r.getString("sector"),r.getString("seat_row"),r.getString("seat_number"),r.getLong("price_cents"),r.getObject("lot_id",UUID.class),r.getString("lot_name")),id);
        var now=now();
        return jdbc.queryForObject("select * from inventory_reservations where id=?",(r,n)->new ReservationSnapshot(id,r.getObject("event_id",UUID.class),r.getString("event_name"),instant(r,"event_starts_at"),r.getString("venue"),r.getString("customer_id"),r.getString("status"),instant(r,"expires_at"),now,r.getObject("order_id",UUID.class),r.getLong("version"),r.getString("currency"),r.getLong("total_cents"),items),id);
    }
    List<Map<String,Object>> catalog() {
        return jdbc.query("select * from inventory_events where status='PUBLISHED' order by starts_at",(r,n)->eventMap(r));
    }
    Map<String,Object> catalogEvent(UUID event) {
        event(event,false);
        var result=jdbc.queryForObject("select * from inventory_events where id=?",(r,n)->eventMap(r),event);
        // Expired occupations are logically available even before the worker releases their rows.
        var seats=jdbc.query("select s.*,l.name as lot_name, case when s.status='RESERVED' and r.expires_at<=clock_timestamp() then 'AVAILABLE' else s.status end display_status from inventory_seats s join inventory_lots l on l.id=s.lot_id left join inventory_reservations r on r.id=s.reservation_id where s.event_id=? order by s.sector,s.seat_row,s.seat_number::int",(r,n)->{
            Map<String,Object> s=new LinkedHashMap<>();s.put("id",r.getObject("id",UUID.class));s.put("sector",r.getString("sector"));s.put("row",r.getString("seat_row"));s.put("number",r.getString("seat_number"));s.put("priceCents",r.getLong("price_cents"));s.put("lotId",r.getObject("lot_id",UUID.class));s.put("lotName",r.getString("lot_name"));s.put("status",r.getString("display_status"));return s;
        },event);result.put("seats",seats);return result;
    }
    private Map<String,Object> eventMap(ResultSet r) throws SQLException {
        Map<String,Object> e=new LinkedHashMap<>();e.put("id",r.getObject("id",UUID.class));e.put("name",r.getString("name"));e.put("startsAt",instant(r,"starts_at"));e.put("venue",r.getString("venue"));return e;
    }
}
