package br.pucrs.pulsepass.gateway;
import java.time.Instant;
public final class GatewayDecision {
 private GatewayDecision(){}
 public static String status(String outcome,Instant createdAt,Instant now){
  return switch(outcome){case "APPROVE"->"APPROVED";case "DECLINE"->"DECLINED";case "TIMEOUT"->now.isBefore(createdAt.plusSeconds(8))?"PENDING":"APPROVED";default->throw new IllegalArgumentException("Resultado inválido");};
 }
}
