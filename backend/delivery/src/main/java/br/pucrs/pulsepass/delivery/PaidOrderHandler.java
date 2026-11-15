package br.pucrs.pulsepass.delivery;

import br.pucrs.pulsepass.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaidOrderHandler {
 public static final String GROUP="delivery-ticket-issuer-v1";
 private final JdbcTemplate jdbc;private final Inbox inbox;
 public PaidOrderHandler(JdbcTemplate jdbc,Inbox inbox) {this.jdbc=jdbc;this.inbox=inbox;}
 @Transactional
 public void accept(EventMessage message) {
  message.validate();
  if(!"order.paid".equals(message.eventType())) {inbox.claim(GROUP,message.eventId());return;}
  var p=message.payload();
  UUID orderId=id(p,"orderId");
  if(!orderId.equals(message.aggregateId())) throw new IllegalArgumentException("Pedido diverge do agregado");
  UUID eventId=id(p,"eventId");String customer=required(p,"customerId");String name=required(p,"eventName");
  String starts=required(p,"eventStartsAt");String venue=required(p,"venue");
  if(!p.path("items").isArray() || p.path("items").isEmpty()) throw new IllegalArgumentException("Itens obrigatórios");
  if(!inbox.claim(GROUP,message.eventId())) return;
  for(var item:p.path("items")) {
   UUID itemId=id(item,"orderItemId");UUID seatId=id(item,"seatId");
   String seat=required(item,"sector")+" / "+required(item,"row")+" / "+required(item,"number");
   jdbc.update("INSERT INTO ticket(id,order_item_id,order_id,customer_id,event_id,event_name,event_starts_at,venue,seat_id,seat_description) VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(order_item_id) DO NOTHING",
      UUID.randomUUID(),itemId,orderId,customer,eventId,name,starts,venue,seatId,seat);
   var existing=jdbc.queryForMap("SELECT order_id,customer_id,seat_id FROM ticket WHERE order_item_id=?",itemId);
   if(!orderId.equals(existing.get("order_id")) || !customer.equals(existing.get("customer_id")) || !seatId.equals(existing.get("seat_id")))
      throw new IllegalArgumentException("Item já emitido com dados incompatíveis");
  }
 }
 private static String required(JsonNode node,String key) {String text=node.path(key).asText("");if(text.isBlank()) throw new IllegalArgumentException("Campo obrigatório: "+key);return text;}
 private static UUID id(JsonNode node,String key) {return UUID.fromString(required(node,key));}
}
