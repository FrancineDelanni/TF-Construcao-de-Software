package br.pucrs.pulsepass.inventory.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ReservationPolicyTest {
    private final Instant deadline = Instant.parse("2026-11-09T12:10:00Z");
    @Test void confirmsOnlyStrictlyBeforeDeadline() {
        assertEquals(ReservationStatus.CONFIRMED, ReservationPolicy.confirm(ReservationStatus.ACTIVE, deadline, deadline.minusNanos(1)));
        assertEquals(ReservationStatus.EXPIRED, ReservationPolicy.confirm(ReservationStatus.ACTIVE, deadline, deadline));
        assertEquals(ReservationStatus.EXPIRED, ReservationPolicy.confirm(ReservationStatus.ACTIVE, deadline, deadline.plusNanos(1)));
    }
    @Test void confirmedReservationsNeverReleaseEvenAfterDeadline() {
        assertEquals(ReservationStatus.CONFIRMED, ReservationPolicy.cancel(ReservationStatus.CONFIRMED, deadline, deadline.plusSeconds(1)));
        assertEquals(ReservationStatus.CONFIRMED, ReservationPolicy.expire(ReservationStatus.CONFIRMED, deadline, deadline.plusSeconds(1)));
    }
    @Test void terminalStatesAreStableForEveryOperation() {
        for (var status : new ReservationStatus[]{ReservationStatus.CONFIRMED, ReservationStatus.EXPIRED, ReservationStatus.CANCELLED}) {
            assertEquals(status, ReservationPolicy.confirm(status, deadline, deadline.minusSeconds(1)));
            assertEquals(status, ReservationPolicy.cancel(status, deadline, deadline.minusSeconds(1)));
            assertEquals(status, ReservationPolicy.expire(status, deadline, deadline.plusSeconds(1)));
        }
    }
    @Test void cancelAtDeadlineExpiresAndCancelBeforeDeadlineCancels() {
        assertEquals(ReservationStatus.CANCELLED, ReservationPolicy.cancel(ReservationStatus.ACTIVE, deadline, deadline.minusSeconds(1)));
        assertEquals(ReservationStatus.EXPIRED, ReservationPolicy.cancel(ReservationStatus.ACTIVE, deadline, deadline));
    }
    @Test void expiryDoesNotRenewOrExpireBeforeDeadline() {
        assertEquals(ReservationStatus.ACTIVE, ReservationPolicy.expire(ReservationStatus.ACTIVE, deadline, deadline.minusNanos(1)));
        assertEquals(ReservationStatus.EXPIRED, ReservationPolicy.expire(ReservationStatus.ACTIVE, deadline, deadline));
    }
}
