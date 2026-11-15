export type Role = 'Cliente' | 'Organizador' | 'Operador' | 'Admin' | string;
export type Session = {authenticated: false} | {authenticated: true; accessToken: string; expiresAt: string; csrfToken: string; user: {sub: string; name: string; roles: Role[]}};
export interface Seat {id: string; sector: string; row: string; number: string | number; priceCents: number; status: string}
export interface Event {id: string; name: string; startsAt: string; venue: string; seats: Seat[]}
export interface Item {seatId: string; sector: string; row: string; number: string | number; priceCents: number}
export interface Reservation {reservationId: string; eventId: string; eventName: string; status: string; expiresAt: string; serverNow: string; totalCents: number; currency: string; items: Item[]}
export interface Order {id: string; reservationId: string; eventName: string; status: 'CREATED'|'RESERVED'|'AWAITING_PAYMENT'|'PAID'|'EXPIRED'|'CANCELLED'; expiresAt: string; serverNow: string; totalCents: number; currency: string; paymentStatus: string | null; items: Item[]; history: {status?: string; state?: string; occurredAt?: string; createdAt?: string; at?: string}[]}
export interface Ticket {id: string; orderId: string; status: string; seatDescription: string}
export type Outcome = 'APPROVE' | 'DECLINE' | 'TIMEOUT';
