import { api } from "../auth/session";
import type { Event } from "./types";
export const getEvent = (id: string) =>
  api<Event>(`/api/inventory/events/${id}`);
export async function listEvents(): Promise<Event[]> {
  const catalog = await api<Event[]>("/api/inventory/events");
  return Promise.all(
    catalog.map((value) =>
      value.seats ? Promise.resolve(value) : getEvent(value.id),
    ),
  );
}
