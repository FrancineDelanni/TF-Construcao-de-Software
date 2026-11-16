package br.pucrs.pulsepass.orders.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrderPolicyTest {
  @Test
  void onlyAuthoritativeConfirmationAfterApprovalMeansPaid() {
    assertEquals(
        OrderState.AWAITING_PAYMENT,
        OrderPolicy.authoritative(OrderState.AWAITING_PAYMENT, "ACTIVE", true));
    assertEquals(
        OrderState.AWAITING_PAYMENT,
        OrderPolicy.authoritative(OrderState.AWAITING_PAYMENT, "CONFIRMED", false));
    assertEquals(
        OrderState.PAID, OrderPolicy.authoritative(OrderState.AWAITING_PAYMENT, "CONFIRMED", true));
  }

  @Test
  void cancellationAndExpirationAreAuthoritativeEvenBeforeBinding() {
    assertEquals(
        OrderState.CANCELLED, OrderPolicy.authoritative(OrderState.CREATED, "CANCELLED", false));
    assertEquals(
        OrderState.EXPIRED, OrderPolicy.authoritative(OrderState.RESERVED, "EXPIRED", true));
  }

  @Test
  void conflictingTerminalProjectionCannotOverwritePaid() {
    assertThrows(
        IllegalStateException.class,
        () -> OrderPolicy.authoritative(OrderState.PAID, "CANCELLED", true));
    assertEquals(OrderState.PAID, OrderPolicy.authoritative(OrderState.PAID, "CONFIRMED", true));
  }

  @Test
  void allAuthoritativeCombinationsPreserveTerminalMonotonicity() {
    for (OrderState current : OrderState.values())
      for (String authority : java.util.List.of("ACTIVE", "CONFIRMED", "EXPIRED", "CANCELLED"))
        for (boolean approved : new boolean[] {true, false}) {
          boolean terminal =
              java.util.Set.of(OrderState.PAID, OrderState.EXPIRED, OrderState.CANCELLED)
                  .contains(current);
          try {
            OrderState result = OrderPolicy.authoritative(current, authority, approved);
            if (terminal) assertEquals(current, result);
          } catch (IllegalStateException conflict) {
            assertTrue(terminal);
          }
        }
  }

  @Test
  void requestHashIsStableAndIncludesOperationAndResource() {
    assertEquals(OrderPolicy.hash("pay:a:APPROVE"), OrderPolicy.hash("pay:a:APPROVE"));
    assertNotEquals(OrderPolicy.hash("pay:a:APPROVE"), OrderPolicy.hash("pay:a:DECLINE"));
  }
}
