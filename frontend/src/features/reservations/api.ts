import { api } from "../auth/session";
import type { Reservation } from "./types";
export const getReservation = (id: string) =>
  api<Reservation>(`/api/inventory/reservations/${id}`);
export const reserveSeats = (eventId: string, seatIds: string[], key: string) =>
  api<Reservation>(`/api/inventory/events/${eventId}/reservations`, {
    method: "POST",
    body: { seatIds },
    key,
  });
