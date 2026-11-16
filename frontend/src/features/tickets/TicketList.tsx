import type { Order } from "../orders/types";
import type { Ticket } from "./types";
import { Chip } from "../../shared/ui/Chip";
interface Props {
  order: Order | null;
  tickets: Ticket[];
  busy: boolean;
  onDownload: (ticket: Ticket) => void;
}
export function TicketList({ order, tickets, busy, onDownload }: Props) {
  if (!order || order.status !== "PAID") return null;
  return (
    <section className="panel tickets">
      <div className="section-title">
        <h2>Seus ingressos</h2>
        <span className="live-dot">Emissão assíncrona</span>
      </div>
      {tickets.length === 0 ? (
        <div className="empty">
          <span className="spinner" />
          <h3>Estamos emitindo seus ingressos</h3>
          <p>
            A compra está confirmada. O PDF aparecerá aqui assim que estiver
            disponível.
          </p>
        </div>
      ) : (
        tickets.map((ticket) => (
          <article className="ticket" key={ticket.id}>
            <div>
              <span className="eyebrow">PULSEPASS · INGRESSO</span>
              <h3>{order.eventName}</h3>
              <p>{ticket.seatDescription}</p>
              <small>Código {ticket.id}</small>
              <p>
                <Chip status={ticket.status} />
              </p>
            </div>
            <button
              disabled={
                busy ||
                !["ISSUED", "READY", "VALID", "ACTIVE"].includes(ticket.status)
              }
              onClick={() => onDownload(ticket)}
            >
              Baixar PDF ↓
            </button>
          </article>
        ))
      )}
    </section>
  );
}
