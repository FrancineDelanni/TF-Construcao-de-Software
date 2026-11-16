package br.pucrs.pulsepass.orders.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStateTest {
  // Transições da seção 6 do C4, independentes da implementação do enum.
  private static final Set<Transition> ALLOWED =
      Set.of(
          new Transition(OrderState.CREATED, OrderState.RESERVED),
          new Transition(OrderState.CREATED, OrderState.EXPIRED),
          new Transition(OrderState.RESERVED, OrderState.AWAITING_PAYMENT),
          new Transition(OrderState.RESERVED, OrderState.EXPIRED),
          new Transition(OrderState.RESERVED, OrderState.CANCELLED),
          new Transition(OrderState.AWAITING_PAYMENT, OrderState.RESERVED),
          new Transition(OrderState.AWAITING_PAYMENT, OrderState.PAID),
          new Transition(OrderState.AWAITING_PAYMENT, OrderState.EXPIRED),
          new Transition(OrderState.AWAITING_PAYMENT, OrderState.CANCELLED));

  @ParameterizedTest
  @MethodSource("allowedTransitions")
  void acceptsDocumentedTransition(Transition transition) {
    assertEquals(transition.to(), transition.from().transitionTo(transition.to()));
  }

  @ParameterizedTest
  @MethodSource("forbiddenTransitions")
  void rejectsUndocumentedTransition(Transition transition) {
    assertThrows(
        IllegalStateException.class, () -> transition.from().transitionTo(transition.to()));
  }

  @ParameterizedTest
  @EnumSource(OrderState.class)
  void rejectsNullDestination(OrderState state) {
    assertThrows(IllegalArgumentException.class, () -> state.transitionTo(null));
  }

  static Stream<Transition> allowedTransitions() {
    return ALLOWED.stream();
  }

  static Stream<Transition> forbiddenTransitions() {
    return Arrays.stream(OrderState.values())
        .flatMap(from -> Arrays.stream(OrderState.values()).map(to -> new Transition(from, to)))
        .filter(transition -> !ALLOWED.contains(transition));
  }

  record Transition(OrderState from, OrderState to) {}
}
