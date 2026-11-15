package br.pucrs.pulsepass.orders.domain;

/** Estados da US-19. A confirmação externa e a persistência cabem aos futuros casos de uso. */
public enum OrderState {
    CREATED,
    RESERVED,
    AWAITING_PAYMENT,
    PAID,
    EXPIRED,
    CANCELLED;

    public OrderState transitionTo(OrderState next) {
        if (next == null) {
            throw new IllegalArgumentException("O estado de destino é obrigatório");
        }

        boolean allowed = switch (this) {
            case CREATED -> next == RESERVED || next == EXPIRED;
            case RESERVED -> next == AWAITING_PAYMENT || next == EXPIRED || next == CANCELLED;
            case AWAITING_PAYMENT -> next == RESERVED || next == PAID
                    || next == EXPIRED || next == CANCELLED;
            case PAID, EXPIRED, CANCELLED -> false;
        };

        if (!allowed) {
            throw new IllegalStateException("Transição inválida: " + this + " → " + next);
        }
        return next;
    }
}
