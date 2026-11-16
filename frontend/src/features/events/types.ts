export interface Seat {
  id: string;
  sector: string;
  row: string;
  number: string | number;
  priceCents: number;
  status: string;
}
export interface Event {
  id: string;
  name: string;
  startsAt: string;
  venue: string;
  seats: Seat[];
}
