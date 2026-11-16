import type { Event } from "./types";
import { available } from "./availability";
import { date, money } from "../../shared/formatting";
interface Props {
  events: Event[];
  loading: boolean;
  busy: boolean;
  search: string;
  onSearch: (value: string) => void;
  onOpen: (id: string) => void;
  onReload: () => void;
}
export function EventsPage({
  events,
  loading,
  busy,
  search,
  onSearch,
  onOpen,
  onReload,
}: Props) {
  return (
    <>
      <section className="page-heading">
        <div>
          <span className="eyebrow">DESCUBRA. ESCOLHA. VIVA.</span>
          <h1>
            Seu próximo encontro<span>.</span>
          </h1>
          <p>Experiências para sair da rotina e fazer parte da história.</p>
        </div>
        <span className="edition">
          PORTO ALEGRE & ALÉM
          <br />
          <strong>Agenda PulsePass</strong>
        </span>
      </section>
      <div className="catalog-toolbar">
        <div>
          <span className="live-dot">Vendas abertas</span>
          <span>{events.length} eventos na agenda</span>
        </div>
        <label className="search">
          <span>⌕</span>
          <input
            type="search"
            aria-label="Buscar eventos"
            placeholder="Encontre seu evento"
            value={search}
            onChange={(e) => onSearch(e.target.value)}
          />
        </label>
      </div>
      {loading ? (
        <div className="empty">
          <span className="spinner" />
          Carregando eventos…
        </div>
      ) : (
        <div className="event-grid">
          {events
            .filter((value) =>
              value.name
                .toLocaleLowerCase()
                .includes(search.toLocaleLowerCase()),
            )
            .map((value, index) => {
              const seats = value.seats ?? [];
              const free = seats.filter((seat) => available(seat.status));
              return (
                <article className="event-card" key={value.id}>
                  <div
                    className={`event-art art-${index % 3}`}
                    aria-hidden="true"
                  >
                    <span className="art-lines" />
                    <span className="art-symbol">
                      {["✳", "◒", "✴"][index % 3]}
                    </span>
                    <span className="art-type">
                      AO VIVO
                      <br />É DIFERENTE.
                    </span>
                    <span className="event-number">
                      0{index + 1} / PULSEPASS
                    </span>
                  </div>
                  <div className="event-info">
                    <div className="event-date">
                      {date(value.startsAt)}
                      <span className="chip">
                        {free.length
                          ? `${free.length} lugares livres`
                          : "Esgotado"}
                      </span>
                    </div>
                    <h2>{value.name}</h2>
                    <p className="venue">↗ {value.venue}</p>
                    <div className="event-bottom">
                      <div>
                        <small>A partir de</small>
                        <strong>
                          {free.length
                            ? money(
                                Math.min(
                                  ...free.map((seat) => seat.priceCents),
                                ),
                              )
                            : "—"}
                        </strong>
                      </div>
                      <button
                        disabled={busy || !free.length}
                        onClick={() => onOpen(value.id)}
                      >
                        Ver assentos ↗
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
        </div>
      )}
      {!loading && events.length === 0 && (
        <div className="empty">
          <h2>Nenhum evento disponível</h2>
          <button onClick={onReload}>Atualizar agenda</button>
        </div>
      )}
    </>
  );
}
