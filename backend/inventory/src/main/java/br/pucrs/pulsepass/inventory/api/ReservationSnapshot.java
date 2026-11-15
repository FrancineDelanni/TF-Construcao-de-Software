package br.pucrs.pulsepass.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationSnapshot(UUID reservationId, UUID eventId, String eventName, Instant eventStartsAt,
        String venue, String customerId, String status, Instant expiresAt, Instant serverNow, UUID orderId,
        long version, String currency, long totalCents, List<Item> items) {
    public record Item(UUID reservationItemId, UUID seatId, String sector, String row, String number, long priceCents, UUID lotId, String lotName) { }
}
