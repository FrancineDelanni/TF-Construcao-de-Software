package br.pucrs.pulsepass.delivery.infrastructure.persistence;

import br.pucrs.pulsepass.delivery.application.TicketStore;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTicketStore implements TicketStore {
  private final JdbcTemplate jdbc;

  public JdbcTicketStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(
      UUID id,
      UUID itemId,
      UUID orderId,
      String customer,
      UUID eventId,
      String name,
      String starts,
      String venue,
      UUID seatId,
      String seat) {
    jdbc.update(
        "INSERT INTO ticket(id,order_item_id,order_id,customer_id,event_id,event_name,event_starts_at,venue,seat_id,seat_description) VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(order_item_id) DO NOTHING",
        id,
        itemId,
        orderId,
        customer,
        eventId,
        name,
        starts,
        venue,
        seatId,
        seat);
  }

  @Override
  public Map<String, Object> itemIdentity(UUID itemId) {
    return jdbc.queryForMap(
        "SELECT order_id,customer_id,seat_id FROM ticket WHERE order_item_id=?", itemId);
  }

  @Override
  public List<Map<String, Object>> nextJob() {
    return jdbc.queryForList(
        "SELECT id FROM ticket WHERE status<>'ISSUED' AND next_attempt_at<=clock_timestamp() AND (lease_until IS NULL OR lease_until<clock_timestamp()) ORDER BY issued_at LIMIT 1 FOR UPDATE SKIP LOCKED");
  }

  @Override
  public void lease(UUID id, UUID lease) {
    jdbc.update(
        "UPDATE ticket SET status='PROCESSING',lease_token=?,lease_until=clock_timestamp()+interval '1 minute',attempts=attempts+1 WHERE id=?",
        lease,
        id);
  }

  @Override
  public Map<String, Object> job(UUID id) {
    return jdbc.queryForMap("SELECT * FROM ticket WHERE id=?", id);
  }

  @Override
  public List<Map<String, Object>> complete(UUID id, UUID lease, byte[] pdf) {
    return jdbc.queryForList(
        "UPDATE ticket SET status='ISSUED',pdf=?,lease_until=NULL WHERE id=? AND lease_token=? AND status='PROCESSING' RETURNING order_id,order_item_id,event_id",
        pdf,
        id,
        lease);
  }

  @Override
  public void failed(UUID id, UUID lease) {
    jdbc.update(
        "UPDATE ticket SET status='PENDING',lease_until=NULL,next_attempt_at=clock_timestamp()+interval '5 seconds' WHERE id=? AND lease_token=? AND status='PROCESSING'",
        id,
        lease);
  }

  @Override
  public List<Map<String, Object>> list(UUID orderId, String owner) {
    return jdbc.query(
        "SELECT id,order_id,status,seat_description FROM ticket WHERE order_id=? AND customer_id=? ORDER BY seat_description",
        (rs, n) ->
            Map.<String, Object>of(
                "id",
                rs.getObject("id"),
                "orderId",
                rs.getObject("order_id"),
                "status",
                rs.getString("status"),
                "seatDescription",
                rs.getString("seat_description")),
        orderId,
        owner);
  }

  @Override
  public List<Map<String, Object>> ownedPdf(UUID id, String owner) {
    return jdbc.queryForList(
        "SELECT status,pdf FROM ticket WHERE id=? AND customer_id=?", id, owner);
  }
}
