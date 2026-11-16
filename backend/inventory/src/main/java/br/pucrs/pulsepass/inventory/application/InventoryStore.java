package br.pucrs.pulsepass.inventory.application;

import br.pucrs.pulsepass.inventory.application.model.ReservationSnapshot;
import br.pucrs.pulsepass.inventory.domain.ReservationStatus;
import java.time.Instant;
import java.util.*;

/** Persistence operations participate in the caller's local transaction. */
public interface InventoryStore {
  public record Event(
      UUID id, String name, Instant startsAt, String venue, String status, String currency) {}

  public record Seat(
      UUID id,
      UUID eventId,
      String sector,
      String row,
      String number,
      long priceCents,
      String status,
      UUID reservationId,
      UUID lotId,
      String lotName,
      boolean lotActive) {}

  public record Reservation(
      UUID id,
      UUID eventId,
      String customerId,
      ReservationStatus status,
      Instant expiresAt,
      UUID orderId,
      long version) {}

  Instant now();

  Event event(UUID id, boolean lock);

  Reservation reservation(UUID id, boolean lock);

  List<Seat> seats(Collection<UUID> ids, boolean lock);

  List<UUID> itemSeats(UUID reservation);

  ReservationSnapshot snapshot(UUID id);

  List<Map<String, Object>> catalog();

  Map<String, Object> catalogEvent(UUID event);

  Map<String, Object> claimIdempotency(String customer, String route, String key, String hash);

  void createReservation(UUID id, Event event, String customer, Instant expiresAt, long total);

  void reserveSeat(UUID reservation, Seat seat);

  void saveResponse(String customer, String route, String key, String response);

  List<UUID> reservationsForOrder(UUID order);

  void bindOrder(UUID reservation, UUID order);

  List<UUID> due();

  void transition(UUID reservation, ReservationStatus status);

  int sellSeats(UUID reservation);

  void releaseSeats(UUID reservation);
}
