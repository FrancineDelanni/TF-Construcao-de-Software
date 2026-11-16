import type { Item } from "../reservations/types";
export interface Order {
  id: string;
  reservationId: string;
  eventName: string;
  status:
    | "CREATED"
    | "RESERVED"
    | "AWAITING_PAYMENT"
    | "PAID"
    | "EXPIRED"
    | "CANCELLED";
  expiresAt: string;
  serverNow: string;
  totalCents: number;
  currency: string;
  paymentStatus: string | null;
  items: Item[];
  history: {
    status?: string;
    state?: string;
    occurredAt?: string;
    createdAt?: string;
    at?: string;
  }[];
}
export type Outcome = "APPROVE" | "DECLINE" | "TIMEOUT";
