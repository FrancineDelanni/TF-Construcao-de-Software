import type { Event, Seat } from "./types";
import { available } from "./availability";
import { Items } from "../reservations/Items";
import { labels } from "../../shared/ui/statusLabels";
import { date, money } from "../../shared/formatting";
interface Props {
  event: Event;
  selected: string[];
  busy: boolean;
  onBack: () => void;
  onToggle: (id: string) => void;
  onReserve: () => void;
}
export function SeatsPage({
  event,
  selected,
  busy,
  onBack,
  onToggle,
  onReserve,
}: Props) {
  const picked = event.seats.filter((seat) => selected.includes(seat.id));
  const selectionInvalid = picked.some((seat) => !available(seat.status));
  const grouped = event.seats.reduce<Record<string, Seat[]>>((all, seat) => {
    const group = `${seat.sector} · Fileira ${seat.row}`;
    (all[group] ??= []).push(seat);
    return all;
  }, {});

  return (
    <>
      <button className="back" onClick={onBack}>
        ← Voltar aos eventos
      </button>
      <section className="page-heading">
        <div>
          <span className="eyebrow">ESCOLHA O SEU LUGAR</span>
          <h1>{event.name}</h1>
          <p>
            {date(event.startsAt)} · {event.venue}
          </p>
        </div>
        <span className="step">
          01 <small>/ 03 · ASSENTOS</small>
        </span>
      </section>
      <div className="seat-layout">
        <section className="panel map">
          <div className="section-title">
            <h2>Mapa de assentos</h2>
            <span className="live-dot">Atualiza a cada 5 s</span>
          </div>
          <div className="stage">PALCO / ÁREA PRINCIPAL</div>
          <div className="legend">
            <span>□ Livre</span>
            <span>✓ Selecionado</span>
            <span>▨ Reservado</span>
            <span>× Vendido</span>
          </div>
          {Object.entries(grouped).map(([group, seats]) => (
            <section className="seat-group" key={group}>
              <h3>{group}</h3>
              <div className="seats">
                {seats
                  .sort((a, b) => Number(a.number) - Number(b.number))
                  .map((seat) => (
                    <button
                      key={seat.id}
                      disabled={busy || !available(seat.status)}
                      className={`seat ${selected.includes(seat.id) ? "selected" : seat.status.toLowerCase()}`}
                      aria-pressed={selected.includes(seat.id)}
                      aria-label={`${group}, assento ${seat.number}, ${selected.includes(seat.id) ? "Selecionado" : (labels[seat.status] ?? seat.status)}, ${money(seat.priceCents)}`}
                      onClick={() => onToggle(seat.id)}
                    >
                      {selected.includes(seat.id)
                        ? "✓"
                        : seat.status === "SOLD"
                          ? "×"
                          : seat.number}
                    </button>
                  ))}
              </div>
            </section>
          ))}
        </section>
        <aside className="panel selection">
          <span className="eyebrow">SEU RESUMO</span>
          <h2>
            {picked.length
              ? `${picked.length} lugar${picked.length > 1 ? "es" : ""} escolhido${picked.length > 1 ? "s" : ""}`
              : "Qual vai ser seu lugar?"}
          </h2>
          {picked.length ? (
            <Items
              items={picked.map((seat) => ({ ...seat, seatId: seat.id }))}
            />
          ) : (
            <p>Toque em um assento livre no mapa para começar.</p>
          )}
          <div className="total">
            <span>Total</span>
            <strong>
              {money(picked.reduce((sum, seat) => sum + seat.priceCents, 0))}
            </strong>
          </div>
          <p className="hint">
            ◷ Ao reservar, todos os assentos escolhidos ficam bloqueados por 10
            minutos.
          </p>
          {selectionInvalid && (
            <p role="alert" className="login-error">
              A disponibilidade mudou. Desmarque os assentos indisponíveis ou
              refaça sua seleção.
            </p>
          )}
          <button
            className="primary wide"
            disabled={busy || !picked.length || selectionInvalid}
            onClick={onReserve}
          >
            {busy ? "Reservando…" : "Reservar assentos →"}
          </button>
          <small>Reserva atômica: todos os lugares ou nenhum.</small>
        </aside>
      </div>
    </>
  );
}
