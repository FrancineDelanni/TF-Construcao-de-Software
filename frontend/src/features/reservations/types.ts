export interface Item {
  seatId: string;
  sector: string;
  row: string;
  number: string | number;
  priceCents: number;
}
export interface Reservation {
  reservationId: string;
  eventId: string;
  eventName: string;
  status: string;
  expiresAt: string;
  serverNow: string;
  totalCents: number;
  currency: string;
  items: Item[];
}
