import type { Session } from "./types";
export function RoleNotice({
  session,
}: {
  session: Extract<Session, { authenticated: true }>;
}) {
  return (
    <section className="panel role-notice">
      <span className="eyebrow">SESSÃO AUTENTICADA</span>
      <h1>Olá, {session.user.name}.</h1>
      <p>
        Seu papel é{" "}
        {session.user.roles
          .filter((role) => ["Organizador", "Operador", "Admin"].includes(role))
          .join(", ")}
        . Esta entrega disponibiliza a jornada de compra para contas Cliente.
      </p>
      <p>
        Para testar reservas e ingressos, saia e entre como cliente1 ou
        cliente2.
      </p>
    </section>
  );
}
