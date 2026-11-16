import { AppLayout } from "./AppLayout";
import { useSession } from "../features/auth/useSession";
import { WelcomePage } from "../features/auth/WelcomePage";
import { RoleNotice } from "../features/auth/RoleNotice";
import { usePurchaseFlow } from "../features/checkout/usePurchaseFlow";
import { CheckoutPage } from "../features/checkout/CheckoutPage";
import { EventsPage } from "../features/events/EventsPage";
import { SeatsPage } from "../features/events/SeatsPage";
import { OrdersPage } from "../features/orders/OrdersPage";
import { TicketsPage } from "../features/tickets/TicketsPage";
import "./styles.css";
export default function App() {
  const auth = useSession();
  const flow = usePurchaseFlow(auth.session);
  const error = flow.error || auth.error;
  return (
    <AppLayout
      session={auth.session}
      view={flow.view}
      busy={flow.busy}
      isCustomer={flow.isCustomer}
      navigate={flow.navigate}
      onLogout={flow.signOut}
    >
      {error && (
        <div className="notice error" role="alert">
          <div>
            <strong>Não foi possível concluir</strong>
            <p>{error}</p>
          </div>
          <button
            aria-label="Fechar aviso"
            onClick={() => {
              flow.setError("");
              auth.clearError();
            }}
          >
            ×
          </button>
        </div>
      )}
      {flow.syncError && (
        <div className="notice warning" role="status">
          {flow.syncError}
        </div>
      )}
      {auth.booting ? (
        <div className="empty">
          <span className="spinner" />
          <h1>Conectando sua sessão…</h1>
        </div>
      ) : !auth.session.authenticated ? (
        <WelcomePage />
      ) : !flow.isCustomer ? (
        <RoleNotice session={auth.session} />
      ) : (
        <>
          {flow.view === "events" && (
            <EventsPage
              events={flow.events}
              loading={flow.loading}
              busy={flow.busy}
              search={flow.search}
              onSearch={flow.setSearch}
              onOpen={flow.openEvent}
              onReload={flow.reloadEvents}
            />
          )}
          {flow.view === "seats" && flow.event && (
            <SeatsPage
              event={flow.event}
              selected={flow.selected}
              busy={flow.busy}
              onBack={() => flow.navigate("events")}
              onToggle={(id) => {
                flow.setSelected((value) =>
                  value.includes(id)
                    ? value.filter((selected) => selected !== id)
                    : [...value, id],
                );
                flow.setError("");
              }}
              onReserve={flow.reserve}
            />
          )}
          {flow.view === "checkout" && <CheckoutPage {...flow} />}
          {flow.view === "orders" && (
            <OrdersPage
              orders={flow.orders}
              busy={flow.busy}
              onReload={() => flow.navigate("orders")}
              onBrowse={() => flow.navigate("events")}
              onOpen={flow.openOrder}
            />
          )}
          {flow.view === "tickets" && (
            <TicketsPage
              order={flow.order}
              orders={flow.orders}
              tickets={flow.tickets}
              busy={flow.busy}
              onDownload={flow.download}
              onOrders={() => flow.navigate("orders")}
              onOpen={(order) => flow.openOrder(order, "tickets")}
            />
          )}
        </>
      )}
    </AppLayout>
  );
}
