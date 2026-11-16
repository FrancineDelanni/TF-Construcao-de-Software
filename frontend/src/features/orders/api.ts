import { api } from "../auth/session";
import type { Order, Outcome } from "./types";
export const getOrder = (id: string) => api<Order>(`/api/orders/${id}`);
export const listOrders = () => api<Order[]>("/api/orders");
export const createOrder = (reservationId: string, key: string) =>
  api<Order>("/api/orders", { method: "POST", body: { reservationId }, key });
export const payOrder = (id: string, outcome: Outcome, key: string) =>
  api<Order>(`/api/orders/${id}/payments`, {
    method: "POST",
    body: { outcome },
    key,
  });
export const cancelOrder = (id: string, key: string) =>
  api<Order>(`/api/orders/${id}/cancel`, { method: "POST", body: {}, key });
