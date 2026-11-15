package br.pucrs.pulsepass.orders.domain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
public final class OrderPolicy {
 private OrderPolicy() {}
 public static OrderState authoritative(OrderState current,String reservationStatus,boolean approved) {
  if("CONFIRMED".equals(reservationStatus) && (current==OrderState.EXPIRED || current==OrderState.CANCELLED))throw new IllegalStateException("Reserva confirmada incompatível com pedido finalizado");
  OrderState desired=switch(reservationStatus) {
   case "EXPIRED" -> OrderState.EXPIRED;
   case "CANCELLED" -> OrderState.CANCELLED;
   case "CONFIRMED" -> approved ? OrderState.PAID : current;
   default -> current==OrderState.CREATED ? OrderState.RESERVED : current;
  };
  if(current==desired) return current;
  if(current==OrderState.PAID || current==OrderState.CANCELLED || current==OrderState.EXPIRED)
   throw new IllegalStateException("Estados terminais incompatíveis; reconciliação necessária");
  // Authoritative terminal result may arrive before the local bound/payment projection.
  OrderState cursor=current;
  if(cursor==OrderState.CREATED && desired!=OrderState.EXPIRED) cursor=cursor.transitionTo(OrderState.RESERVED);
  if(desired==OrderState.PAID && cursor==OrderState.RESERVED) cursor=cursor.transitionTo(OrderState.AWAITING_PAYMENT);
  return cursor==desired?cursor:cursor.transitionTo(desired);
 }
 public static String hash(String body) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
