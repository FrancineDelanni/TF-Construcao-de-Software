package br.pucrs.pulsepass.gateway;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class GatewayDecisionTest {
 @Test void timeoutRemainsPendingForEightSecondsThenApproves() {
  Instant start=Instant.parse("2026-01-01T00:00:00Z");
  assertEquals("PENDING",GatewayDecision.status("TIMEOUT",start,start.plusSeconds(7)));
  assertEquals("APPROVED",GatewayDecision.status("TIMEOUT",start,start.plusSeconds(8)));
 }
 @Test void immediateOutcomes() {
  Instant now=Instant.now();
  assertEquals("APPROVED",GatewayDecision.status("APPROVE",now,now));
  assertEquals("DECLINED",GatewayDecision.status("DECLINE",now,now));
  assertThrows(IllegalArgumentException.class,()->GatewayDecision.status("OTHER",now,now));
 }
}
