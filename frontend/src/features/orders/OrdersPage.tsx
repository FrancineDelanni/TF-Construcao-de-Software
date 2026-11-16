import type { Order } from "./types";
import { Chip } from "../../shared/ui/Chip";
import { money } from "../../shared/formatting";
interface Props {
  orders: Order[];
  busy: boolean;
  onReload: () => void;
  onBrowse: () => void;
  onOpen: (order: Order) => void;
}
export function OrdersPage({
  orders,
  busy,
  onReload,
  onBrowse,
  onOpen,
}: Props) {
  return (
    <>
      <section className="page-heading">
        <div>
          <span className="eyebrow">SUAS EXPERIÊNCIAS</span>
          <h1>
            Meus pedidos<span>.</span>
          </h1>
          <p>Acompanhe cada etapa, do primeiro clique à confirmação.</p>
        </div>
        <button onClick={onReload} disabled={busy}>
          Atualizar ↻
        </button>
      </section>
      <section className="panel order-list">
        {orders.length === 0 ? (
          <div className="empty">
            <h2>Seu próximo encontro começa aqui</h2>
            <p>Você ainda não tem pedidos.</p>
            <button onClick={onBrowse}>Explorar eventos ↗</button>
          </div>
        ) : (
          orders.map((value) => (
            <article className="order-row" key={value.id}>
              <div>
                <small>#{value.id.slice(0, 8)}</small>
                <h2>{value.eventName}</h2>
                <p>
                  {value.items.length} ingresso
                  {value.items.length === 1 ? "" : "s"} ·{" "}
                  {money(value.totalCents, value.currency)}
                </p>
              </div>
              <Chip status={value.status} />
              <button disabled={busy} onClick={() => onOpen(value)}>
                {value.status === "RESERVED"
                  ? "Continuar pagamento"
                  : "Acompanhar"}{" "}
                ↗
              </button>
            </article>
          ))
        )}
      </section>
    </>
  );
}
