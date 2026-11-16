import type { ReactNode } from "react";
import type { Session } from "../features/auth/types";
import type { View } from "../features/checkout/types";
interface Props {
  session: Session;
  view: View;
  busy: boolean;
  isCustomer: boolean;
  navigate: (view: View) => void;
  onLogout: () => void;
  children: ReactNode;
}
export function AppLayout({
  session,
  view,
  busy,
  isCustomer,
  navigate,
  onLogout,
  children,
}: Props) {
  return (
    <>
      <header className="header">
        <a href="/" className="brand" aria-label="PulsePass início">
          <span className="brand-mark">
            p<span>•</span>
          </span>
          pulsepass<span className="brand-dot">.</span>
        </a>
        {session.authenticated && (
          <>
            <nav aria-label="Navegação principal">
              {isCustomer && (
                <>
                  <button
                    className={
                      view === "events" || view === "seats" ? "active" : ""
                    }
                    onClick={() => navigate("events")}
                    disabled={busy}
                  >
                    Eventos
                  </button>
                  <button
                    className={
                      view === "orders" || view === "checkout" ? "active" : ""
                    }
                    onClick={() => navigate("orders")}
                    disabled={busy}
                  >
                    Meus pedidos
                  </button>
                  <button
                    className={view === "tickets" ? "active" : ""}
                    onClick={() => navigate("tickets")}
                    disabled={busy}
                  >
                    Ingressos
                  </button>
                </>
              )}
            </nav>
            <div className="account">
              <span>
                {session.user.name}
                <small>
                  {session.user.roles
                    .filter((role) =>
                      ["Cliente", "Organizador", "Operador", "Admin"].includes(
                        role,
                      ),
                    )
                    .join(" · ")}
                </small>
              </span>
              <button className="link" onClick={onLogout} disabled={busy}>
                Sair ↗
              </button>
            </div>
          </>
        )}
      </header>
      <main>{children}</main>
      <footer>
        <span className="brand">pulsepass.</span>
        <p>Mais do que um ingresso. Um encontro.</p>
        <small>Ambiente local · Dados e pagamentos fictícios</small>
      </footer>
    </>
  );
}
