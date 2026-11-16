import { api } from "../auth/session";
import type { Ticket } from "./types";
export const listTickets = (orderId: string) =>
  api<Ticket[]>(`/api/delivery/orders/${orderId}/tickets`);
export async function downloadTicket(ticket: Ticket): Promise<void> {
  const blob = await api<Blob>(`/api/delivery/tickets/${ticket.id}/pdf`, {
    blob: true,
  });
  const url = URL.createObjectURL(blob),
    link = document.createElement("a");
  link.href = url;
  link.download = `pulsepass-${ticket.id}.pdf`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
