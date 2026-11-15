package br.pucrs.pulsepass.inventory.domain;

import java.time.Instant;

/** The instant must come from PostgreSQL after acquiring all decision locks. */
public final class ReservationPolicy {
    private ReservationPolicy() { }
    public static ReservationStatus expire(ReservationStatus status, Instant expiresAt, Instant now) {
        return status == ReservationStatus.ACTIVE && !now.isBefore(expiresAt) ? ReservationStatus.EXPIRED : status;
    }
    public static ReservationStatus confirm(ReservationStatus status, Instant expiresAt, Instant now) {
        var effective = expire(status, expiresAt, now);
        return effective == ReservationStatus.ACTIVE ? ReservationStatus.CONFIRMED : effective;
    }
    public static ReservationStatus cancel(ReservationStatus status, Instant expiresAt, Instant now) {
        var effective = expire(status, expiresAt, now);
        return effective == ReservationStatus.ACTIVE ? ReservationStatus.CANCELLED : effective;
    }
}
