import type { Order } from "../orders/types";
import type { Ticket } from "./types";
import { TicketList } from "./TicketList";
interface Props {
  order: Order | null;
  orders: Order[];
  tickets: Ticket[];
  busy: boolean;
  onDownload: (ticket: Ticket) => void;
  onOrders: () => void;
  onOpen: (order: Order) => void;
}
export function TicketsPage({
  order,
  orders,
  tickets,
  busy,
  onDownload,
  onOrders,
  onOpen,
}: Props) {
  return (
    <>
      <section className="page-heading">
        <div>
          <span className="eyebrow">PRONTO PARA O GRANDE DIA</span>
          <h1>
            Meus ingressos<span>.</span>
          </h1>
          <p>Baixe seu PDF autenticado e guarde para a entrada.</p>
        </div>
      </section>
      {order?.status === "PAID" ? (
        <TicketList
          order={order}
          tickets={tickets}
          busy={busy}
          onDownload={onDownload}
        />
      ) : null}
      <section className="panel">
        <h2>Compras confirmadas</h2>
        {orders.filter((value) => value.status === "PAID").length === 0 ? (
          <div className="empty">
            <p>Seus ingressos aparecerão após a confirmação de uma compra.</p>
            <button onClick={onOrders}>Acompanhar meus pedidos</button>
          </div>
        ) : (
          orders
            .filter((value) => value.status === "PAID")
            .map((value) => (
              <article className="order-row" key={value.id}>
                <div>
                  <h3>{value.eventName}</h3>
                  <small>Pedido {value.id.slice(0, 8)}</small>
                </div>
                <button disabled={busy} onClick={() => onOpen(value)}>
                  Ver ingressos ↗
                </button>
              </article>
            ))
        )}
      </section>
    </>
  );
}
