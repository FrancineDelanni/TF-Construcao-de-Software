package br.pucrs.pulsepass.orders.application;

import br.pucrs.pulsepass.common.messaging.EventMessage;
import br.pucrs.pulsepass.common.messaging.Inbox;
import br.pucrs.pulsepass.common.messaging.Outbox;
import br.pucrs.pulsepass.common.web.ApiException;
import br.pucrs.pulsepass.orders.domain.*;
import br.pucrs.pulsepass.orders.domain.OrderPolicy;
import br.pucrs.pulsepass.orders.domain.OrderState;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final OrderStore store;
  private final ObjectMapper json;
  private final Outbox outbox;
  private final Inbox inbox;

  public OrderService(OrderStore store, ObjectMapper json, Outbox outbox, Inbox inbox) {
    this.store = store;
    this.json = json;
    this.outbox = outbox;
    this.inbox = inbox;
  }

  @Transactional
  public UUID create(String owner, String key, UUID reservation) {
    validateKey(key);
    if (reservation == null) throw new ApiException(400, "Reserva obrigatória");
    String hash = OrderPolicy.hash(reservation.toString());
    UUID existing = repeat(owner, "create", key, hash);
    if (existing != null) return existing;
    UUID id = UUID.randomUUID();
    int inserted = store.insertOrder(id, reservation, owner);
    var row = store.orderForReservation(reservation);
    if (!owner.equals(row.get("customer_id")))
      throw new ApiException(409, "Reserva já associada a outro pedido");
    id = (UUID) row.get("id");
    if (inserted == 1) {
      history(id, "CREATED");
      publish(row, "order.created");
    }
    saveKey(owner, "create", key, hash, id);
    return id;
  }

  @Transactional
  public UUID pay(String owner, UUID id, String key, String outcome) {
    validateKey(key);
    if (!Set.of("APPROVE", "DECLINE", "TIMEOUT").contains(Objects.toString(outcome, "")))
      throw new ApiException(400, "Resultado inválido");
    String op = "pay:" + id, hash = OrderPolicy.hash(id + ":" + outcome);
    UUID existing = repeat(owner, op, key, hash);
    if (existing != null) return existing;
    var row = owned(owner, id, true);
    if (!"RESERVED".equals(row.get("state")) || Boolean.TRUE.equals(row.get("cancel_requested")))
      throw new ApiException(409, "Pedido não disponível para pagamento");
    UUID attempt = UUID.randomUUID();
    store.createPayment(attempt, id, outcome);
    store.markPaymentPending(id);
    transition(row, OrderState.AWAITING_PAYMENT, "order.awaiting-payment");
    saveKey(owner, op, key, hash, id);
    return id;
  }

  @Transactional
  public UUID cancel(String owner, UUID id, String key) {
    validateKey(key);
    String op = "cancel:" + id, hash = OrderPolicy.hash(id.toString());
    UUID existing = repeat(owner, op, key, hash);
    if (existing != null) return existing;
    var row = owned(owner, id, true);
    String state = (String) row.get("state");
    if (Set.of("PAID", "EXPIRED", "CANCELLED").contains(state))
      throw new ApiException(409, "Pedido já finalizado");
    store.requestCancellation(id);
    if (!"CREATED".equals(state)) command(row, "reservation.cancel.requested", null);
    saveKey(owner, op, key, hash, id);
    return id;
  }

  public Map<String, Object> get(String owner, UUID id) {
    return view(owned(owner, id, false));
  }

  public List<Map<String, Object>> list(String owner) {
    return store.list(owner).stream().map(this::view).toList();
  }

  private Map<String, Object> owned(String owner, UUID id, boolean lock) {
    var rows = store.findOwned(id, lock);
    if (rows.isEmpty() || !owner.equals(rows.getFirst().get("customer_id")))
      throw new ApiException(404, "Pedido não encontrado");
    return rows.getFirst();
  }

  private Map<String, Object> view(Map<String, Object> row) {
    JsonNode s = parse(row.get("snapshot").toString());
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("id", row.get("id"));
    v.put("reservationId", row.get("reservation_id"));
    v.put("status", row.get("state"));
    v.put("expiresAt", s.path("expiresAt").isMissingNode() ? null : s.path("expiresAt").asText());
    v.put("serverNow", Instant.now());
    v.put("eventName", s.path("eventName").asText("Reserva em processamento"));
    v.put("totalCents", s.path("totalCents").asLong());
    v.put("currency", s.path("currency").asText("BRL"));
    v.put("items", s.path("items").isArray() ? s.path("items") : json.createArrayNode());
    v.put("paymentStatus", row.get("payment_status"));
    v.put(
        "history",
        store.history((UUID) row.get("id")).stream()
            .map(
                h ->
                    Map.of(
                        "status",
                        h.get("status"),
                        "at",
                        ((java.sql.Timestamp) h.get("at")).toInstant()))
            .toList());
    return v;
  }

  private void validateKey(String key) {
    if (key == null || key.isBlank() || key.length() > 200)
      throw new ApiException(400, "Idempotency-Key obrigatório (até 200 caracteres)");
  }

  private UUID repeat(String owner, String op, String key, String hash) {
    store.lockIdempotency(owner, op, key);
    var rows = store.idempotency(owner, op, key);
    if (rows.isEmpty()) return null;
    if (!hash.equals(rows.getFirst().get("request_hash")))
      throw new ApiException(409, "Idempotency-Key utilizado com outro conteúdo");
    return (UUID) rows.getFirst().get("order_id");
  }

  private void saveKey(String owner, String op, String key, String hash, UUID id) {
    store.saveKey(owner, op, key, hash, id);
  }

  private void history(UUID id, String state) {
    store.appendHistory(id, state);
  }

  private void transition(Map<String, Object> row, OrderState next, String event) {
    OrderState old = OrderState.valueOf((String) row.get("state"));
    if (old == next) return;
    old.transitionTo(next);
    long version = ((Number) row.get("version")).longValue() + 1;
    store.transition((UUID) row.get("id"), next.name(), version);
    row.put("state", next.name());
    row.put("version", version);
    history((UUID) row.get("id"), next.name());
    publish(row, event);
  }

  private void authoritativeTransition(Map<String, Object> row, String status) {
    OrderState current = OrderState.valueOf((String) row.get("state"));
    OrderState desired =
        OrderPolicy.authoritative(current, status, "APPROVED".equals(row.get("payment_status")));
    if (current == desired) return;
    if (current == OrderState.CREATED && desired != OrderState.EXPIRED) {
      transition(row, OrderState.RESERVED, "order.reserved");
      current = OrderState.RESERVED;
    }
    if (desired == OrderState.PAID && current == OrderState.RESERVED)
      transition(row, OrderState.AWAITING_PAYMENT, "order.awaiting-payment");
    transition(row, desired, "order." + desired.name().toLowerCase(Locale.ROOT));
    if (desired == OrderState.EXPIRED || desired == OrderState.CANCELLED)
      store.voidPayment((UUID) row.get("id"));
  }

  private void publish(Map<String, Object> row, String type) {
    ObjectNode p = json.createObjectNode();
    p.put("orderId", row.get("id").toString());
    p.put("reservationId", row.get("reservation_id").toString());
    p.put("customerId", row.get("customer_id").toString());
    JsonNode snapshot = parse(row.get("snapshot") == null ? "{}" : row.get("snapshot").toString());
    for (String f :
        List.of(
            "eventId", "eventName", "eventStartsAt", "venue", "currency", "totalCents", "items"))
      if (snapshot.has(f)) p.set(f, snapshot.get(f));
    p.put("status", row.get("state").toString());
    outbox.add(
        "pulsepass.orders.events.v1",
        type,
        "order",
        (UUID) row.get("id"),
        ((Number) row.get("version")).longValue(),
        p);
  }

  private void command(Map<String, Object> row, String type, UUID attempt) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("reservationId", row.get("reservation_id"));
    p.put("orderId", row.get("id"));
    p.put("customerId", row.get("customer_id"));
    if (attempt != null) p.put("paymentAttemptId", attempt);
    outbox.add(
        "pulsepass.inventory.commands.v1",
        type,
        "reservation",
        (UUID) row.get("reservation_id"),
        ((Number) row.get("version")).longValue(),
        p);
  }

  @Transactional
  public void rejectIntent(UUID id) {
    store.rejectIntent(id);
  }

  public List<Map<String, Object>> workOrders() {
    return store.workOrders();
  }

  public List<Map<String, Object>> workPayments() {
    return store.workPayments();
  }

  @Transactional
  public void snapshot(UUID id, JsonNode snapshot) {
    var rows = store.lockOrder(id);
    if (rows.isEmpty()) return;
    var row = rows.getFirst();
    if (!row.get("customer_id").equals(snapshot.path("customerId").asText()))
      throw new ApiException(409, "Proprietário da reserva incompatível");
    String authority = snapshot.path("status").asText();
    boolean unboundTerminal =
        !snapshot.hasNonNull("orderId") && Set.of("EXPIRED", "CANCELLED").contains(authority);
    if (!unboundTerminal && !row.get("id").toString().equals(snapshot.path("orderId").asText()))
      throw new ApiException(409, "Vínculo da reserva incompatível");
    ObjectNode s = (ObjectNode) snapshot.deepCopy();
    ArrayNode items = json.createArrayNode();
    for (JsonNode original : snapshot.path("items")) {
      ObjectNode item = (ObjectNode) original.deepCopy();
      item.put(
          "orderItemId",
          UUID.nameUUIDFromBytes(
                  (id
                          + ":"
                          + original
                              .path("reservationItemId")
                              .asText(original.path("seatId").asText()))
                      .getBytes(StandardCharsets.UTF_8))
              .toString());
      items.add(item);
    }
    s.set("items", items);
    long incoming = s.path("version").asLong();
    long current = ((Number) row.get("inventory_version")).longValue();
    if (incoming < current) return;
    store.saveSnapshot(id, s.toString(), incoming);
    row.put("snapshot", s.toString());
    row.put("inventory_version", incoming);
    boolean wasCreated = "CREATED".equals(row.get("state"));
    applyStatus(row, s.path("status").asText());
    if (wasCreated
        && Boolean.TRUE.equals(row.get("cancel_requested"))
        && "ACTIVE".equals(s.path("status").asText()))
      command(row, "reservation.cancel.requested", null);
  }

  private void applyStatus(Map<String, Object> row, String status) {
    try {
      authoritativeTransition(row, status);
    } catch (IllegalStateException conflict) {
      store.block((UUID) row.get("id"));
      UUID incident = UUID.randomUUID();
      store.quarantine(incident, (UUID) row.get("id"), row.get("snapshot").toString());
      outbox.add(
          "pulsepass.dlq.orders-reservations-v1",
          "order.reconciliation-conflict",
          "order",
          (UUID) row.get("id"),
          ((Number) row.get("version")).longValue(),
          Map.of(
              "incidentId",
              incident,
              "orderId",
              row.get("id"),
              "status",
              status,
              "error",
              "Estado terminal incompatível; análise necessária"));
    }
  }

  @Transactional
  public void paymentResult(UUID attempt, String status) {
    var list = store.lockOrderForPayment(attempt);
    if (list.isEmpty()) return;
    var row = list.getFirst();
    String old = store.paymentStatus(attempt);
    if (!"PENDING".equals(old) || "PENDING".equals(status)) return;
    if (!Set.of("APPROVED", "DECLINED").contains(status))
      throw new IllegalArgumentException("Resposta inválida do gateway");
    store.updatePayment(attempt, status);
    boolean terminal = Set.of("PAID", "EXPIRED", "CANCELLED").contains(row.get("state"));
    String localStatus = terminal && !"PAID".equals(row.get("state")) ? "VOIDED" : status;
    store.updateOrderPayment((UUID) row.get("id"), localStatus);
    row.put("payment_status", localStatus);
    if (terminal) return;
    if ("DECLINED".equals(status)) transition(row, OrderState.RESERVED, "order.payment-declined");
    else if (!Boolean.TRUE.equals(row.get("cancel_requested"))) {
      command(row, "reservation.confirm.requested", attempt);
      JsonNode s = parse(row.get("snapshot").toString());
      if ("CONFIRMED".equals(s.path("status").asText())) applyStatus(row, "CONFIRMED");
    }
  }

  @Transactional
  public void consume(EventMessage event) {
    event.validate();
    if (!inbox.claim("orders-reservations-v1", event.eventId())) return;
    JsonNode p = event.payload();
    if (!event.eventType().startsWith("reservation.")) return;
    UUID reservation =
        UUID.fromString(p.path("reservationId").asText(event.aggregateId().toString()));
    long version = p.path("version").asLong(event.aggregateVersion());
    store.observeReservation(reservation, version, p.toString());
    var rows = store.lockOrdersForReservation(reservation);
    if (rows.isEmpty()) return;
    var row = rows.getFirst();
    if (p.hasNonNull("orderId") && !row.get("id").toString().equals(p.path("orderId").asText()))
      return;
    if (p.hasNonNull("customerId")
        && !row.get("customer_id").equals(p.path("customerId").asText())) {
      if ("CREATED".equals(row.get("state"))) {
        store.reject((UUID) row.get("id"));
        return;
      }
      throw new IllegalArgumentException("Proprietário incompatível");
    }
    String status = p.path("status").asText("");
    if (Set.of("EXPIRED", "CANCELLED").contains(status)) applyStatus(row, status);
    // Confirmation requires the complete authoritative price/item snapshot; worker reconciles it
    // outside this tx.
  }

  public JsonNode parse(String raw) {
    try {
      return json.readTree(raw);
    } catch (Exception e) {
      throw new IllegalArgumentException("JSON inválido", e);
    }
  }
}
