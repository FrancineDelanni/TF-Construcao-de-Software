export function WelcomePage() {
  return (
    <section className="welcome">
      <div>
        <span className="eyebrow">ENCONTROS QUE VIRAM HISTÓRIA</span>
        <h1>
          O próximo grande
          <br />
          momento começa
          <br />
          <em>com você.</em>
        </h1>
        <p>
          Escolha seu lugar. Viva a experiência.
          <br />
          Seus eventos e ingressos, em um só lugar.
        </p>
        <a className="button" href="/api/iam/login">
          Entrar com segurança <span>↗</span>
        </a>
        <small>Autenticação protegida pelo Keycloak</small>
        {new URLSearchParams(location.search).has("login") && (
          <p className="login-error" role="alert">
            O acesso não foi concluído. Tente entrar novamente.
          </p>
        )}
      </div>
      <aside className="welcome-card">
        <div className="orbit">✳</div>
        <span className="eyebrow">AMBIENTE DE DEMONSTRAÇÃO</span>
        <h2>
          Seu lugar está
          <br />
          te esperando.
        </h2>
        <p>Na tela de acesso, use uma conta fictícia:</p>
        <dl>
          <div>
            <dt>Cliente</dt>
            <dd>cliente1 ou cliente2</dd>
          </div>
          <div>
            <dt>Organizador</dt>
            <dd>organizador</dd>
          </div>
          <div>
            <dt>Operador</dt>
            <dd>operador</dd>
          </div>
          <div>
            <dt>Admin</dt>
            <dd>admin</dd>
          </div>
          <div>
            <dt>Senha local</dt>
            <dd>PulsePass123!</dd>
          </div>
        </dl>
        <p className="fine-print">
          Pagamentos são simulados. Não informe dados reais de cartão.
        </p>
      </aside>
    </section>
  );
}
