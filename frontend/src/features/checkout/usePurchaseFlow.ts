import { useCallback, useEffect, useRef, useState } from "react";
import { logout } from "../auth/session";
import type { Session } from "../auth/types";
import { ApiError } from "../../shared/http/client";
import { message } from "../../shared/formatting";
import { getEvent, listEvents } from "../events/api";
import type { Event } from "../events/types";
import { getReservation, reserveSeats } from "../reservations/api";
import type { Reservation } from "../reservations/types";
import {
  getOrder,
  listOrders,
  createOrder as submitOrder,
  payOrder,
  cancelOrder,
} from "../orders/api";
import type { Order, Outcome } from "../orders/types";
import { listTickets, downloadTicket } from "../tickets/api";
import type { Ticket } from "../tickets/types";
import { OperationKeys } from "./OperationKeys";
import type { View } from "./types";
export function usePurchaseFlow(session: Session) {
  const [view, setView] = useState<View>("events");
  const [events, setEvents] = useState<Event[]>([]);
  const [event, setEvent] = useState<Event | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [outcome, setOutcome] = useState<Outcome>("APPROVE");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [syncError, setSyncError] = useState("");
  const paymentPendingSeen = useRef(new Set<string>());
  const [search, setSearch] = useState("");
  const keys = useRef(new OperationKeys());
  const payment = useRef<{
    orderId: string;
    outcome: Outcome;
    awaitingCount: number;
  } | null>(null);
  const requestBusy = useRef(false);
  const currentUser = session.authenticated ? session.user.sub : null;
  const isCustomer =
    session.authenticated && session.user.roles.includes("Cliente");
  useEffect(() => {
    setEvents([]);
    setEvent(null);
    setSelected([]);
    setReservation(null);
    setOrder(null);
    setOrders([]);
    setTickets([]);
    keys.current = new OperationKeys();
    payment.current = null;
    setView("events");
  }, [currentUser]);
  const fetchEvents = useCallback(async () => {
    setLoading(true);
    try {
      setEvents(await listEvents());
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    if (currentUser && isCustomer)
      fetchEvents().catch((e) => setError(message(e)));
  }, [currentUser, isCustomer, fetchEvents]);
  const refreshOrder = useCallback(async () => {
    if (!order) return;
    try {
      const updated = await getOrder(order.id);
      setOrder(updated);
      setSyncError("");
      if (updated.status === "AWAITING_PAYMENT")
        paymentPendingSeen.current.add(updated.id);
      if (
        updated.status === "RESERVED" &&
        updated.paymentStatus === "DECLINED" &&
        (paymentPendingSeen.current.has(updated.id) ||
          (payment.current?.orderId === updated.id &&
            (updated.history ?? []).filter(
              (entry) => (entry.status ?? entry.state) === "AWAITING_PAYMENT",
            ).length > payment.current.awaitingCount))
      ) {
        paymentPendingSeen.current.delete(updated.id);
        keys.current.complete(`payment:${updated.id}`);
        payment.current = null;
      }
      if (updated.status === "PAID") setTickets(await listTickets(updated.id));
    } catch (e) {
      setSyncError(
        `Atualização interrompida: ${message(e)} A consulta será repetida.`,
      );
    }
  }, [order?.id]);
  const refreshReservation = useCallback(async () => {
    if (order || !reservation) return;
    try {
      setReservation(await getReservation(reservation.reservationId));
      setSyncError("");
    } catch (e) {
      setSyncError(message(e));
    }
  }, [order?.id, reservation?.reservationId]);
  useEffect(() => {
    if (view !== "checkout" && view !== "tickets") return;
    let active = true;
    const poll = async () => {
      if (active) {
        await (order ? refreshOrder() : refreshReservation());
        if (active) timer = setTimeout(poll, 2000);
      }
    };
    let timer = setTimeout(poll, 2000);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [view, refreshOrder, refreshReservation, order?.id]);
  useEffect(() => {
    if (view !== "seats" || !event) return;
    let live = true;
    const poll = async () => {
      try {
        const fresh = await getEvent(event.id);
        if (live) {
          setEvent(fresh);
          setSyncError("");
        }
      } catch (e) {
        if (live) setSyncError(`Disponibilidade: ${message(e)}`);
      }
      if (live) timer = setTimeout(poll, 2000);
    };
    let timer = setTimeout(poll, 2000);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [view, event?.id]);
  async function action(work: () => Promise<void>) {
    if (requestBusy.current) return;
    requestBusy.current = true;
    setBusy(true);
    setError("");
    try {
      await work();
    } catch (e) {
      setError(message(e));
    } finally {
      requestBusy.current = false;
      setBusy(false);
    }
  }
  async function openEvent(id: string) {
    await action(async () => {
      setEvent(await getEvent(id));
      setSelected([]);
      setSyncError("");
      setView("seats");
    });
  }
  async function createOrder(value: Reservation) {
    const created = await submitOrder(
      value.reservationId,
      keys.current.for(`order:${value.reservationId}`),
    );
    setOrder(created);
  }
  async function reserve() {
    await action(async () => {
      if (!event || !selected.length) return;
      const seatIds = [...selected].sort();
      let reserved: Reservation;
      try {
        reserved = await reserveSeats(
          event.id,
          seatIds,
          keys.current.for(`reserve:${event.id}:${seatIds.join(",")}`),
        );
      } catch (e) {
        if (e instanceof ApiError && e.status === 409) {
          setEvent(await getEvent(event.id));
          setSelected([]);
          throw new Error(
            "Assentos indisponíveis: outra pessoa acabou de reservá-los. Nenhum assento desta tentativa foi bloqueado. Escolha novamente.",
          );
        }
        throw e;
      }
      setReservation(reserved);
      setOrder(null);
      setTickets([]);
      setView("checkout");
      await createOrder(reserved);
    });
  }
  async function pay() {
    await action(async () => {
      if (!order) return;
      if (!payment.current || payment.current.orderId !== order.id)
        payment.current = {
          orderId: order.id,
          outcome,
          awaitingCount: (order.history ?? []).filter(
            (entry) => (entry.status ?? entry.state) === "AWAITING_PAYMENT",
          ).length,
        };
      const updated = await payOrder(
        order.id,
        payment.current.outcome,
        keys.current.for(`payment:${order.id}`),
      );
      setOrder(updated);
      if (updated.status === "AWAITING_PAYMENT")
        paymentPendingSeen.current.add(updated.id);
      if (
        updated.status === "RESERVED" &&
        updated.paymentStatus === "DECLINED" &&
        (paymentPendingSeen.current.has(updated.id) ||
          (payment.current?.orderId === updated.id &&
            (updated.history ?? []).filter(
              (entry) => (entry.status ?? entry.state) === "AWAITING_PAYMENT",
            ).length > payment.current.awaitingCount))
      ) {
        paymentPendingSeen.current.delete(updated.id);
        keys.current.complete(`payment:${order.id}`);
        payment.current = null;
      }
    });
  }
  async function cancel() {
    await action(async () => {
      if (!order) return;
      setOrder(
        await cancelOrder(order.id, keys.current.for(`cancel:${order.id}`)),
      );
    });
  }
  async function navigate(next: View) {
    await action(async () => {
      setSyncError("");
      if (next === "events") await fetchEvents();
      if (next === "orders" || next === "tickets")
        setOrders(await listOrders());
      if (next === "tickets" && order?.status === "PAID")
        setTickets(await listTickets(order.id));
      setView(next);
    });
  }
  async function openOrder(value: Order, next: View = "checkout") {
    await action(async () => {
      setOrder(await getOrder(value.id));
      setReservation(null);
      setTickets([]);
      setView(next);
      if (value.status === "PAID") setTickets(await listTickets(value.id));
    });
  }
  async function download(ticket: Ticket) {
    await action(async () => {
      await downloadTicket(ticket);
    });
  }

  return {
    view,
    events,
    event,
    selected,
    reservation,
    order,
    orders,
    tickets,
    outcome,
    busy,
    loading,
    error,
    syncError,
    search,
    isCustomer,
    paymentAttempt: payment.current,
    setSearch,
    setSelected,
    setError,
    setOutcome,
    setView,
    refreshOrder,
    refreshReservation,
    openEvent,
    reserve,
    pay,
    cancel,
    navigate,
    openOrder,
    download,
    reloadEvents: () => action(fetchEvents),
    continueOrder: () => action(() => createOrder(reservation!)),
    signOut: () => action(logout),
  };
}
export type PurchaseFlow = ReturnType<typeof usePurchaseFlow>;
