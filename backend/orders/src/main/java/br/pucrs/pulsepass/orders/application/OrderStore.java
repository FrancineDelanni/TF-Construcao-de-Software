package br.pucrs.pulsepass.orders.application;

import java.util.*;

/** SQL and locking are implemented by infrastructure; transactions belong to OrderService. */
public interface OrderStore {
  int insertOrder(UUID id, UUID reservation, String owner);

  Map<String, Object> orderForReservation(UUID reservation);

  void createPayment(UUID attempt, UUID id, String outcome);

  void markPaymentPending(UUID id);

  void requestCancellation(UUID id);

  List<Map<String, Object>> list(String owner);

  List<Map<String, Object>> findOwned(UUID id, boolean lock);

  List<Map<String, Object>> history(UUID id);

  void lockIdempotency(String owner, String op, String key);

  List<Map<String, Object>> idempotency(String owner, String op, String key);

  void saveKey(String owner, String op, String key, String hash, UUID id);

  void appendHistory(UUID id, String state);

  void transition(UUID id, String state, long version);

  void voidPayment(UUID id);

  void rejectIntent(UUID id);

  List<Map<String, Object>> workOrders();

  List<Map<String, Object>> workPayments();

  List<Map<String, Object>> lockOrder(UUID id);

  void saveSnapshot(UUID id, String snapshot, long version);

  void block(UUID id);

  void quarantine(UUID incident, UUID id, String snapshot);

  List<Map<String, Object>> lockOrderForPayment(UUID attempt);

  String paymentStatus(UUID attempt);

  void updatePayment(UUID attempt, String status);

  void updateOrderPayment(UUID id, String status);

  void observeReservation(UUID reservation, long version, String payload);

  List<Map<String, Object>> lockOrdersForReservation(UUID reservation);

  void reject(UUID id);
}
