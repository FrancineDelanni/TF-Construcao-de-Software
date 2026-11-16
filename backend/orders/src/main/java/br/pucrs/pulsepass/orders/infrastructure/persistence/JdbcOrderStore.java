package br.pucrs.pulsepass.orders.infrastructure.persistence;

import br.pucrs.pulsepass.orders.application.OrderStore;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderStore implements OrderStore {
  private final JdbcTemplate db;

  public JdbcOrderStore(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public int insertOrder(UUID id, UUID reservation, String owner) {
    return db.update(
        "INSERT INTO customer_order(id,reservation_id,customer_id,state) VALUES (?,?,?,'CREATED') ON CONFLICT(reservation_id) WHERE rejected=false DO NOTHING",
        id,
        reservation,
        owner);
  }

  @Override
  public Map<String, Object> orderForReservation(UUID reservation) {
    return db.queryForMap(
        "SELECT * FROM customer_order WHERE reservation_id=? AND rejected=false FOR UPDATE",
        reservation);
  }

  @Override
  public void createPayment(UUID attempt, UUID id, String outcome) {
    db.update(
        "INSERT INTO payment_attempt(id,order_id,outcome) VALUES (?,?,?)", attempt, id, outcome);
  }

  @Override
  public void markPaymentPending(UUID id) {
    db.update("UPDATE customer_order SET payment_status='PENDING' WHERE id=?", id);
  }

  @Override
  public void requestCancellation(UUID id) {
    db.update("UPDATE customer_order SET cancel_requested=true WHERE id=?", id);
  }

  @Override
  public List<Map<String, Object>> list(String owner) {
    return db.queryForList(
        "SELECT * FROM customer_order WHERE customer_id=? AND rejected=false ORDER BY created_at DESC",
        owner);
  }

  @Override
  public List<Map<String, Object>> findOwned(UUID id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM customer_order WHERE id=? AND rejected=false" + (lock ? " FOR UPDATE" : ""),
        id);
  }

  @Override
  public List<Map<String, Object>> history(UUID id) {
    return db.queryForList("SELECT status,at FROM order_history WHERE order_id=? ORDER BY id", id);
  }

  @Override
  public void lockIdempotency(String owner, String op, String key) {
    db.queryForList(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", owner + ":" + op + ":" + key);
  }

  @Override
  public List<Map<String, Object>> idempotency(String owner, String op, String key) {
    return db.queryForList(
        "SELECT * FROM order_idempotency WHERE customer_id=? AND operation=? AND key=?",
        owner,
        op,
        key);
  }

  @Override
  public void saveKey(String owner, String op, String key, String hash, UUID id) {
    db.update("INSERT INTO order_idempotency VALUES (?,?,?,?,?)", owner, op, key, hash, id);
  }

  @Override
  public void appendHistory(UUID id, String state) {
    db.update("INSERT INTO order_history(order_id,status) VALUES (?,?)", id, state);
  }

  @Override
  public void transition(UUID id, String state, long version) {
    db.update(
        "UPDATE customer_order SET state=?,version=?,updated_at=clock_timestamp() WHERE id=?",
        state,
        version,
        id);
  }

  @Override
  public void voidPayment(UUID id) {
    db.update(
        "UPDATE customer_order SET payment_status=CASE WHEN payment_status IN ('APPROVED','PENDING') THEN 'VOIDED' ELSE payment_status END WHERE id=?",
        id);
  }

  @Override
  public void rejectIntent(UUID id) {
    db.update(
        "UPDATE customer_order SET rejected=true,blocked=true WHERE id=? AND state='CREATED'", id);
  }

  @Override
  public List<Map<String, Object>> workOrders() {
    return db.queryForList(
        "SELECT * FROM customer_order WHERE blocked=false AND (state NOT IN ('PAID','EXPIRED','CANCELLED') OR NOT (snapshot ? 'items')) ORDER BY updated_at LIMIT 50");
  }

  @Override
  public List<Map<String, Object>> workPayments() {
    return db.queryForList(
        "SELECT p.* FROM payment_attempt p JOIN customer_order o ON o.id=p.order_id WHERE p.status='PENDING' AND o.blocked=false ORDER BY p.created_at LIMIT 50");
  }

  @Override
  public List<Map<String, Object>> lockOrder(UUID id) {
    return db.queryForList("SELECT * FROM customer_order WHERE id=? FOR UPDATE", id);
  }

  @Override
  public void saveSnapshot(UUID id, String snapshot, long version) {
    db.update(
        "UPDATE customer_order SET snapshot=?::jsonb,inventory_version=?,updated_at=clock_timestamp() WHERE id=?",
        snapshot,
        version,
        id);
  }

  @Override
  public void block(UUID id) {
    db.update("UPDATE customer_order SET blocked=true WHERE id=?", id);
  }

  @Override
  public void quarantine(UUID incident, UUID id, String snapshot) {
    db.update(
        "INSERT INTO orders_quarantine(id,aggregate_id,original,error) VALUES (?,?,?,?)",
        incident,
        id,
        snapshot,
        "Estado terminal incompatível com Inventory");
  }

  @Override
  public List<Map<String, Object>> lockOrderForPayment(UUID attempt) {
    return db.queryForList(
        "SELECT o.* FROM customer_order o JOIN payment_attempt p ON p.order_id=o.id WHERE p.id=? FOR UPDATE OF o",
        attempt);
  }

  @Override
  public String paymentStatus(UUID attempt) {
    return db.queryForObject(
        "SELECT status FROM payment_attempt WHERE id=?", String.class, attempt);
  }

  @Override
  public void updatePayment(UUID attempt, String status) {
    db.update("UPDATE payment_attempt SET status=? WHERE id=?", status, attempt);
  }

  @Override
  public void updateOrderPayment(UUID id, String status) {
    db.update("UPDATE customer_order SET payment_status=? WHERE id=?", status, id);
  }

  @Override
  public void observeReservation(UUID reservation, long version, String payload) {
    db.update(
        "INSERT INTO reservation_observation(reservation_id,version,payload) VALUES (?,?,?::jsonb) ON CONFLICT(reservation_id) DO UPDATE SET version=EXCLUDED.version,payload=EXCLUDED.payload,received_at=clock_timestamp() WHERE reservation_observation.version<=EXCLUDED.version",
        reservation,
        version,
        payload);
  }

  @Override
  public List<Map<String, Object>> lockOrdersForReservation(UUID reservation) {
    return db.queryForList(
        "SELECT * FROM customer_order WHERE reservation_id=? AND rejected=false FOR UPDATE",
        reservation);
  }

  @Override
  public void reject(UUID id) {
    db.update("UPDATE customer_order SET rejected=true,blocked=true WHERE id=?", id);
  }
}
