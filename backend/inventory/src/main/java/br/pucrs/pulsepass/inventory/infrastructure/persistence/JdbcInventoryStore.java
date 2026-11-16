package br.pucrs.pulsepass.inventory.infrastructure.persistence;

import br.pucrs.pulsepass.common.web.ApiException;
import br.pucrs.pulsepass.inventory.application.InventoryStore;
import br.pucrs.pulsepass.inventory.application.InventoryStore.*;
import br.pucrs.pulsepass.inventory.application.model.ReservationSnapshot;
import br.pucrs.pulsepass.inventory.domain.ReservationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryStore implements InventoryStore {
  private final JdbcTemplate jdbc;

  public JdbcInventoryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  static final RowMapper<Seat> SEAT =
      (r, n) ->
          new Seat(
              r.getObject("id", UUID.class),
              r.getObject("event_id", UUID.class),
              r.getString("sector"),
              r.getString("seat_row"),
              r.getString("seat_number"),
              r.getLong("price_cents"),
              r.getString("status"),
              r.getObject("reservation_id", UUID.class),
              r.getObject("lot_id", UUID.class),
              r.getString("lot_name"),
              r.getBoolean("lot_active"));
  static final RowMapper<Reservation> RESERVATION =
      (r, n) ->
          new Reservation(
              r.getObject("id", UUID.class),
              r.getObject("event_id", UUID.class),
              r.getString("customer_id"),
              ReservationStatus.valueOf(r.getString("status")),
              instant(r, "expires_at"),
              r.getObject("order_id", UUID.class),
              r.getLong("version"));

  static Instant instant(ResultSet r, String field) throws SQLException {
    return r.getTimestamp(field).toInstant();
  }

  public Instant now() {
    return jdbc.queryForObject("select clock_timestamp()", java.sql.Timestamp.class).toInstant();
  }

  public Event event(UUID id, boolean lock) {
    var events =
        jdbc.query(
            "select * from inventory_events where id=?" + (lock ? " for share" : ""),
            (r, n) ->
                new Event(
                    r.getObject("id", UUID.class),
                    r.getString("name"),
                    instant(r, "starts_at"),
                    r.getString("venue"),
                    r.getString("status"),
                    r.getString("currency")),
            id);
    if (events.isEmpty()) throw new ApiException(404, "Evento não encontrado");
    return events.getFirst();
  }

  public Reservation reservation(UUID id, boolean lock) {
    var reservations =
        jdbc.query(
            "select * from inventory_reservations where id=?" + (lock ? " for update" : ""),
            RESERVATION,
            id);
    if (reservations.isEmpty()) throw new ApiException(404, "Reserva não encontrada");
    return reservations.getFirst();
  }

  public List<Seat> seats(Collection<UUID> ids, boolean lock) {
    if (ids.isEmpty()) return List.of();
    return jdbc.query(
        "select s.*,l.name as lot_name,l.active as lot_active from inventory_seats s join inventory_lots l on l.id=s.lot_id where s.id in ("
            + String.join(",", Collections.nCopies(ids.size(), "?"))
            + ") order by s.id"
            + (lock ? " for update of s" : ""),
        SEAT,
        ids.toArray());
  }

  public List<UUID> itemSeats(UUID reservation) {
    return jdbc.queryForList(
        "select seat_id from inventory_reservation_items where reservation_id=? order by seat_id",
        UUID.class,
        reservation);
  }

  public ReservationSnapshot snapshot(UUID id) {
    var items =
        jdbc.query(
            "select * from inventory_reservation_items where reservation_id=? order by seat_id",
            (r, n) ->
                new ReservationSnapshot.Item(
                    r.getObject("id", UUID.class),
                    r.getObject("seat_id", UUID.class),
                    r.getString("sector"),
                    r.getString("seat_row"),
                    r.getString("seat_number"),
                    r.getLong("price_cents"),
                    r.getObject("lot_id", UUID.class),
                    r.getString("lot_name")),
            id);
    var now = now();
    return jdbc.queryForObject(
        "select * from inventory_reservations where id=?",
        (r, n) ->
            new ReservationSnapshot(
                id,
                r.getObject("event_id", UUID.class),
                r.getString("event_name"),
                instant(r, "event_starts_at"),
                r.getString("venue"),
                r.getString("customer_id"),
                r.getString("status"),
                instant(r, "expires_at"),
                now,
                r.getObject("order_id", UUID.class),
                r.getLong("version"),
                r.getString("currency"),
                r.getLong("total_cents"),
                items),
        id);
  }

  public List<Map<String, Object>> catalog() {
    return jdbc.query(
        "select * from inventory_events where status='PUBLISHED' order by starts_at",
        (r, n) -> eventMap(r));
  }

  public Map<String, Object> catalogEvent(UUID event) {
    event(event, false);
    var result =
        jdbc.queryForObject(
            "select * from inventory_events where id=?", (r, n) -> eventMap(r), event);
    // Expired occupations are logically available even before the worker releases their rows.
    var seats =
        jdbc.query(
            "select s.*,l.name as lot_name, case when s.status='RESERVED' and r.expires_at<=clock_timestamp() then 'AVAILABLE' else s.status end display_status from inventory_seats s join inventory_lots l on l.id=s.lot_id left join inventory_reservations r on r.id=s.reservation_id where s.event_id=? order by s.sector,s.seat_row,s.seat_number::int",
            (r, n) -> {
              Map<String, Object> s = new LinkedHashMap<>();
              s.put("id", r.getObject("id", UUID.class));
              s.put("sector", r.getString("sector"));
              s.put("row", r.getString("seat_row"));
              s.put("number", r.getString("seat_number"));
              s.put("priceCents", r.getLong("price_cents"));
              s.put("lotId", r.getObject("lot_id", UUID.class));
              s.put("lotName", r.getString("lot_name"));
              s.put("status", r.getString("display_status"));
              return s;
            },
            event);
    result.put("seats", seats);
    return result;
  }

  private Map<String, Object> eventMap(ResultSet r) throws SQLException {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", r.getObject("id", UUID.class));
    e.put("name", r.getString("name"));
    e.put("startsAt", instant(r, "starts_at"));
    e.put("venue", r.getString("venue"));
    return e;
  }

  public Map<String, Object> claimIdempotency(
      String customer, String route, String key, String hash) {
    jdbc.update(
        "insert into inventory_idempotency(customer_id,route,idempotency_key,payload_hash) values(?,?,?,?) on conflict do nothing",
        customer,
        route,
        key,
        hash);
    return jdbc.queryForMap(
        "select payload_hash,response::text as response from inventory_idempotency where customer_id=? and route=? and idempotency_key=? for update",
        customer,
        route,
        key);
  }

  public void createReservation(
      UUID id, Event event, String customer, Instant expiresAt, long total) {
    jdbc.update(
        "insert into inventory_reservations(id,event_id,customer_id,status,expires_at,version,event_name,event_starts_at,venue,currency,total_cents) values(?,?,?,'ACTIVE',?,1,?,?,?,?,?)",
        id,
        event.id(),
        customer,
        java.sql.Timestamp.from(expiresAt),
        event.name(),
        java.sql.Timestamp.from(event.startsAt()),
        event.venue(),
        event.currency(),
        total);
  }

  public void reserveSeat(UUID reservation, Seat seat) {
    jdbc.update(
        "insert into inventory_reservation_items(id,reservation_id,seat_id,sector,seat_row,seat_number,price_cents,lot_id,lot_name) values(?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        reservation,
        seat.id(),
        seat.sector(),
        seat.row(),
        seat.number(),
        seat.priceCents(),
        seat.lotId(),
        seat.lotName());
    jdbc.update(
        "update inventory_seats set status='RESERVED',reservation_id=? where id=?",
        reservation,
        seat.id());
  }

  public void saveResponse(String customer, String route, String key, String response) {
    jdbc.update(
        "update inventory_idempotency set response=?::jsonb where customer_id=? and route=? and idempotency_key=?",
        response,
        customer,
        route,
        key);
  }

  public List<UUID> reservationsForOrder(UUID order) {
    return jdbc.queryForList(
        "select id from inventory_reservations where order_id=?", UUID.class, order);
  }

  public void bindOrder(UUID reservation, UUID order) {
    jdbc.update(
        "update inventory_reservations set order_id=?,version=version+1 where id=?",
        order,
        reservation);
  }

  public List<UUID> due() {
    return jdbc.queryForList(
        "select id from inventory_reservations where status='ACTIVE' and expires_at<=clock_timestamp() order by expires_at limit 100",
        UUID.class);
  }

  public void transition(UUID reservation, ReservationStatus status) {
    jdbc.update(
        "update inventory_reservations set status=?,version=version+1 where id=?",
        status.name(),
        reservation);
  }

  public int sellSeats(UUID reservation) {
    return jdbc.update(
        "update inventory_seats set status='SOLD' where reservation_id=? and status='RESERVED'",
        reservation);
  }

  public void releaseSeats(UUID reservation) {
    jdbc.update(
        "update inventory_seats set status='AVAILABLE',reservation_id=null where reservation_id=? and status='RESERVED'",
        reservation);
  }
}
