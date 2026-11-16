import type { PurchaseFlow } from "./usePurchaseFlow";
import type { Outcome } from "../orders/types";
import { canPay, terminal } from "../orders/behavior";
import { Countdown } from "../reservations/Countdown";
import { Items } from "../reservations/Items";
import { TicketList } from "../tickets/TicketList";
import { Chip } from "../../shared/ui/Chip";
import { labels } from "../../shared/ui/statusLabels";
import { date, money } from "../../shared/formatting";
type Props = Pick<
  PurchaseFlow,
  | "order"
  | "reservation"
  | "tickets"
  | "busy"
  | "outcome"
  | "paymentAttempt"
  | "navigate"
  | "refreshOrder"
  | "refreshReservation"
  | "setView"
  | "setOutcome"
  | "pay"
  | "cancel"
  | "continueOrder"
  | "download"
>;
export function CheckoutPage({
  order,
  reservation,
  tickets,
  busy,
  outcome,
  paymentAttempt,
  navigate,
  refreshOrder,
  refreshReservation,
  setView,
  setOutcome,
  pay,
  cancel,
  continueOrder,
  download,
}: Props) {
  const snapshot = order ?? reservation;
  const canCheckout = order && canPay(order.status, order.paymentStatus);
  if (!snapshot) return null;
  return (
    <>
      <button className="back" onClick={() => navigate("orders")}>
        ← Meus pedidos
      </button>
      <section className="page-heading">
        <div>
          <span className="eyebrow">
            FALTA POUCO PARA VIVER ESSA EXPERIÊNCIA
          </span>
          <h1>
            {order?.status === "PAID"
              ? "Seu lugar está confirmado."
              : "Acompanhe sua compra."}
          </h1>
          <p>{snapshot.eventName}</p>
        </div>
        <span className="step">
          {order?.status === "PAID" ? "03" : "02"}{" "}
          <small>
            / 03 · {order?.status === "PAID" ? "CONFIRMADO" : "CHECKOUT"}
          </small>
        </span>
      </section>
      {!terminal(snapshot.status) && (
        <Countdown
          snapshot={snapshot}
          onZero={order ? refreshOrder : refreshReservation}
        />
      )}
      <div className="checkout-layout">
        <section className="panel">
          <div className="section-title">
            <h2>Resumo da compra</h2>
            <Chip status={order?.status ?? reservation!.status} />
          </div>
          <Items items={snapshot.items} currency={snapshot.currency} />
          <div className="total">
            <span>Total do pedido</span>
            <strong>{money(snapshot.totalCents, snapshot.currency)}</strong>
          </div>
          <small className="reference">
            {order
              ? `Pedido ${order.id}`
              : `Reserva ${reservation!.reservationId}`}
          </small>
          {order?.history?.length ? (
            <div className="history">
              <h3>Histórico do servidor</h3>
              {order.history.map((entry, index) => {
                const at = entry.occurredAt ?? entry.createdAt ?? entry.at;
                return (
                  <div key={index}>
                    <span className="history-dot" />
                    <strong>
                      {labels[entry.status ?? entry.state ?? ""] ??
                        entry.status ??
                        entry.state}
                    </strong>
                    <small>{at ? date(at) : ""}</small>
                  </div>
                );
              })}
            </div>
          ) : null}
        </section>
        <section className="panel payment">
          <span className="eyebrow">
            {order?.status === "PAID"
              ? "COMPRA CONCLUÍDA"
              : "AMBIENTE DE DEMONSTRAÇÃO"}
          </span>
          {!order &&
          reservation &&
          ["EXPIRED", "CANCELLED"].includes(reservation.status) ? (
            <>
              <h2>
                {reservation.status === "EXPIRED"
                  ? "A reserva expirou"
                  : "Reserva cancelada"}
              </h2>
              <p>
                O estado foi confirmado pelo servidor. Escolha novos lugares
                para começar outra compra.
              </p>
              <button onClick={() => navigate("events")}>
                Escolher novos assentos ↗
              </button>
            </>
          ) : !order ? (
            <>
              <h2>Preparando seu pedido</h2>
              <p>
                A reserva já foi registrada. Estamos vinculando os seus assentos
                ao pedido.
              </p>
              <button
                disabled={busy || reservation?.status !== "ACTIVE"}
                onClick={continueOrder}
              >
                {busy ? "Preparando…" : "Consultar / continuar pedido"}
              </button>
            </>
          ) : order.status === "PAID" ? (
            <>
              <div className="success-icon">✓</div>
              <h2>Pagamento confirmado!</h2>
              <p>
                O servidor confirmou seus assentos. Seus ingressos estão a
                caminho desta tela.
              </p>
              <button onClick={() => setView("tickets")}>
                Ver meus ingressos ↗
              </button>
            </>
          ) : ["EXPIRED", "CANCELLED"].includes(order.status) ? (
            <>
              <h2>
                {order.status === "EXPIRED"
                  ? "A reserva expirou"
                  : "Pedido cancelado"}
              </h2>
              <p>
                {order.status === "EXPIRED"
                  ? "O servidor confirmou o fim do prazo. Selecione novos lugares para começar outra compra."
                  : "O cancelamento foi confirmado pelo servidor."}
              </p>
              <button onClick={() => navigate("events")}>
                Escolher novos assentos ↗
              </button>
            </>
          ) : order.status === "CREATED" ? (
            <>
              <span className="spinner" />
              <h2>Vinculando os assentos</h2>
              <p>
                Seu pedido foi recebido. Consultamos o servidor a cada dois
                segundos.
              </p>
            </>
          ) : canCheckout ? (
            <>
              <h2>Pagamento simulado</h2>
              <p>Nenhum dado de cartão real é solicitado ou armazenado.</p>
              {order.paymentStatus === "DECLINED" && (
                <div className="notice warning" role="status">
                  Pagamento recusado. Seus assentos permanecem reservados pelo
                  prazo restante. Você pode tentar outra simulação.
                </div>
              )}
              <label htmlFor="outcome">Resultado do gateway</label>
              <select
                id="outcome"
                value={
                  paymentAttempt?.orderId === order.id
                    ? paymentAttempt.outcome
                    : outcome
                }
                disabled={busy || paymentAttempt?.orderId === order.id}
                onChange={(e) => setOutcome(e.target.value as Outcome)}
              >
                <option value="APPROVE">Aprovar pagamento</option>
                <option value="DECLINE">Recusar pagamento</option>
                <option value="TIMEOUT">Resposta lenta / timeout</option>
              </select>
              <p className="hint">
                Resposta lenta mantém a mesma tentativa em processamento.
                Aguarde a confirmação do servidor.
              </p>
              <button className="primary wide" disabled={busy} onClick={pay}>
                {busy
                  ? "Enviando…"
                  : paymentAttempt?.orderId === order.id
                    ? "Repetir a mesma tentativa →"
                    : `Simular pagamento · ${money(order.totalCents, order.currency)}`}
              </button>
              <small>
                Se a conexão falhar, a tentativa usa a mesma chave para evitar
                duplicação.
              </small>
              <button className="link danger" disabled={busy} onClick={cancel}>
                Solicitar cancelamento da reserva
              </button>
            </>
          ) : (
            <>
              <span className="spinner" />
              <h2>Aguardando confirmação</h2>
              <p>
                O pagamento está sendo processado. Mantenha esta tela aberta ou
                acompanhe em Meus pedidos.
              </p>
              <div className="pending-status">
                ◷{" "}
                {labels[order.paymentStatus ?? ""] ??
                  "Confirmação dos assentos pendente"}
              </div>
              <p className="hint">
                Uma resposta lenta não significa recusa. A consulta continua até
                o servidor decidir.
              </p>
              <button className="link danger" disabled={busy} onClick={cancel}>
                Solicitar cancelamento do pedido
              </button>
            </>
          )}
        </section>
      </div>
      <TicketList
        order={order}
        tickets={tickets}
        busy={busy}
        onDownload={download}
      />
    </>
  );
}
