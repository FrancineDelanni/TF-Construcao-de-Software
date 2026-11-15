# PulsePass — como funciona

Este texto descreve o incremento implementado do Pacote 3. A arquitetura completa planejada continua nos documentos 06–08; Analytics, gestão de eventos/contas, e-mail, WebSocket e check-in não fazem parte desta execução.

## Tecnologias e responsabilidades

| Tecnologia/serviço | Papel efetivo |
|---|---|
| Java 21 / Spring Boot 3.5.7 | APIs, validação JWT, transações JDBC, workers e listeners Kafka |
| Maven Wrapper 3.9.11 | Build reproduzível; módulos common, IAM, Inventory, Orders, Delivery e gateway |
| React 19 / TypeScript / Vite | SPA responsiva; catálogo, seleção, checkout, pedidos e download |
| Nginx | Serve bundle e encaminha `/api/*` aos serviços |
| Keycloak 26.4.2 | Identidade OIDC, senha, emissão/assinatura JWT e papéis |
| IAM + Redis 7.4.6 | Authorization Code/PKCE, sessão opaca, refresh cifrado AES-GCM, renovação/logout |
| Inventory + PostgreSQL 17.6 | Catálogo seed, preços congelados, reservas, ocupação, prazo e decisão final |
| Orders + PostgreSQL | Estados, histórico, chaves HTTP, tentativas duráveis e reconciliação |
| Gateway + banco próprio | Simula aprovar, recusar e ficar pendente antes de aprovar; nenhuma cobrança real |
| Kafka 3.9.1 KRaft | Transporta comandos de confirmação/cancelamento e fatos de negócio |
| Delivery + PostgreSQL | Inbox, ingresso único por item, tarefa PDF, bytes do arquivo e autorização |
| PDFBox / ZXing / Ed25519 | PDF, imagem do QR e assinatura criptográfica do conteúdo |
| Flyway | Migrações de cada banco; seed sem duplicação na reexecução |
| JUnit / Testcontainers / JaCoCo | Regras, integração real PostgreSQL/Kafka/Redis e cobertura |
| Checkstyle / ESLint / GitHub Actions | Qualidade estática e workflow versionado para PR/push em develop |
| Docker Compose | Ambiente local com redes, volumes, readiness e inicialização explícita de tópicos |

Common compartilha código de infraestrutura, não banco nem entidades de outros serviços. Não há consultas SQL, joins ou FKs entre bancos. Joins dentro de um banco proprietário são permitidos. IAM neste incremento usa apenas Redis; não foi criado banco SQL fictício para parecer que auditoria/gestão já existe. Analytics mantém sua fronteira documentada e não tem processo vazio no Compose.

## Do login ao ingresso

```mermaid
sequenceDiagram
    actor Pessoa
    participant SPA as React/Nginx
    participant IAM
    participant KC as Keycloak
    participant I as Inventory
    participant O as Orders
    participant G as Gateway fictício
    participant K as Kafka
    participant D as Delivery
    Pessoa->>SPA: Entrar
    SPA->>IAM: Iniciar login
    IAM-->>KC: Redirecionar com state, nonce e PKCE
    KC-->>IAM: Callback com authorization code
    IAM->>KC: Trocar code; validar tokens
    IAM-->>SPA: Cookie opaco; access token em memória
    SPA->>I: Catálogo e reserva dos assentos
    I->>I: Locks, prazo e commit reserva + outbox
    I-->>SPA: Snapshot, expiresAt, serverNow
    SPA->>O: Criar pedido com Idempotency-Key
    O->>I: Vincular reserva idempotentemente via HTTP
    SPA->>O: Pagar com Idempotency-Key
    O->>O: Commit tentativa + Aguardando
    O->>G: Autorizar/consultar mesma tentativa
    G-->>O: Aprovação fictícia
    O->>O: Commit resultado + outbox de comando
    O->>K: reservation.confirm.requested
    K->>I: Consumir e decidir após locks
    I->>I: Commit Confirmada + assentos Vendidos + outbox
    I->>K: reservation.confirmed
    K->>O: Registrar observação
    O->>I: Reconciliar snapshot autoritativo via HTTP
    O->>O: Commit Pago + order.paid na outbox
    O->>K: order.paid
    K->>D: Inbox + ingresso/tarefa únicos
    D->>D: Worker gera QR/PDF e persiste arquivo
    SPA->>O: Polling do pedido
    SPA->>D: Polling da emissão; download com JWT
    D-->>Pessoa: PDF do próprio ingresso
```

HTTP lê o catálogo e vincula/reconcilia a reserva. Kafka leva os comandos e eventos duráveis. O navegador nunca acessa Kafka ou bancos diretamente. Orders não mantém transação SQL aberta enquanto chama Inventory, Keycloak ou gateway: registra intenção, sai da transação, faz a chamada e grava o resultado em outra transação. Depois de reinício, workers consultam as intenções/tentativas pendentes.

Os nomes dos estados no JSON são `CREATED`, `RESERVED`, `AWAITING_PAYMENT`, `PAID`, `EXPIRED`, `CANCELLED`. A interface traduz para Criado, Reservado, Aguardando, Pago, Expirado, Cancelado. `OrderState.transitionTo` continua a máquina de estados original; o serviço a utiliza e persiste cada mudança no histórico.

## Por que não há sobre-venda

A autoridade é o PostgreSQL de Inventory. A operação bloqueia o evento e as reservas/assentos envolvidos em ordem determinística, revalida o conjunto e só então verifica todos os assentos. Se qualquer item estiver indisponível, faz rollback de toda a reserva. Uma restrição liga cada ocupação ao identificador de sua reserva; não existe ocupação autoritativa em Redis.

Reservas anteriores vencidas podem ser liberadas na mesma transação que tenta reservar novamente. Uma limpeza antiga altera somente assentos cujo `reservation_id` ainda é o da reserva antiga. Assento vendido não é liberado por timeout. Confirmar, cancelar e expirar disputam os mesmos locks e terminais não voltam para ativos. PostgreSQL e constraints continuam protegendo o estoque se Redis desaparecer ou o worker reiniciar.

## Os dez minutos

Inventory consulta `clock_timestamp()` **depois de adquirir os locks**. O prazo é persistido como dez minutos a partir desse instante. Retry, vínculo de pedido e pagamento recusado não estendem `expiresAt`. Na confirmação, `agora >= expiresAt` já significa inválida, mesmo se o worker ainda não limpou as linhas.

O worker percorre reservas vencidas e registra liberação/expiração/outbox. A validade lógica termina no prazo; publicação Kafka e atualização visual podem ocorrer depois. Orders reconcilia o estado autoritativo, inclusive quando eventos chegam antes do pedido ou ficam atrasados. Aprovação fictícia perto do fim pode resultar em Expirado se o comando só chegar depois do prazo.

A SPA calcula o contador por `expiresAt - serverNow - tempo monotônico decorrido`. Ao chegar a zero, consulta novamente o servidor. Não decide sozinha que o pedido expirou: um Inventory que confirmou dentro do prazo pode terminar a propagação depois de o contador zerar.

## Idempotência HTTP e pagamento

A chave é vinculada ao usuário, à operação/rota e ao hash dos parâmetros relevantes. Mesma chave e mesmos parâmetros retomam o mesmo resultado/identificador sem novo efeito. Corpo semanticamente diferente recebe **409**. A reserva conserva o snapshot original; uma consulta posterior traz status/horário atual. Orders pode retornar o estado atual da mesma operação em vez de congelar a resposta 202. As chaves persistem neste incremento, sem coleta automática.

Há unicidade da reserva no Orders e do vínculo pedido no Inventory. Uma tentativa de pagamento possui ID estável e no máximo uma tentativa pendente por pedido. O gateway persiste o resultado no próprio banco e rejeita reutilizar o ID com outro resultado solicitado.

- **APPROVE:** aprovação fictícia; ainda falta confirmar o estoque.
- **DECLINE:** recusa definitiva; permite nova tentativa enquanto a reserva continuar válida.
- **TIMEOUT:** fica pendente por aproximadamente oito segundos e depois aprova. Orders consulta/reenvia a mesma tentativa. Não interpreta timeout como recusa nem permite outra tentativa concorrente.

Cancelar é uma solicitação. Só Inventory aceita a devolução. Se confirmar já ganhou a disputa, o pedido converge para Pago. Não há cancelamento financeiro ou estorno de pedido pago neste incremento.

## Outbox, inbox e ordem

O mesmo commit grava mudança de negócio e linha de outbox. O relay publica com `acks=all` e produtor idempotente; só marca enviado após ack. Se cair entre ack e atualização SQL, a mensagem pode ser republicada com o **mesmo eventId**. Isso é entrega at-least-once; não existe promessa de exactly-once entre banco, Kafka e HTTP.

O relay usa um lock transacional de publicação por banco e drena por sequência local. Essa escolha conserva uma ordem mais forte que por agregado, mas limita throughput e pode atrasar outros agregados durante uma falha. É uma simplificação intencional do ambiente local.

O listener chama um serviço transacional separado. Inbox, efeitos e novas outboxes entram no mesmo commit; só depois de retornar o listener o container confirma o offset. Uma duplicata já na inbox não reaplica efeito. Em Delivery, `UNIQUE(order_item_id)` também protege contra a mesma compra reapresentada com outro ID de mensagem.

Resultados fora de ordem não devem reabrir terminais. Orders mantém observações/versionamento e busca snapshot autoritativo. Um terminal incompatível é bloqueado e registrado em `orders_quarantine`/DLQ para análise, em vez de forçar transição inválida.

Retry de consumidor ocorre três vezes, com intervalo de 1 s, preservando a partição durante a tentativa. Ao exaurir, a transação grava quarentena e intenção DLQ. O agregado fica bloqueado até replay/reconciliação; eventos seguintes dele ficam retidos para reprocessar em ordem. Se o banco falhar, não há commit do offset. Procedimento concreto em [guia de execução](12-guia-execucao.md).

## Identidade, autorização e PDF

Keycloak emite e assina os JWTs. IAM usa Authorization Code + PKCE, state/nonce e valida o retorno; guarda refresh cifrado no Redis com TTL. O navegador recebe cookie opaco HttpOnly/SameSite e access token somente em memória. Renovar e sair exigem Origin permitido e token CSRF. O logout invalida a sessão local e tenta revogar no provedor; um access token já emitido continua válido até expirar (cinco minutos no realm local).

As APIs validam assinatura/JWKS, issuer, audience, prazo e papéis. Inventory exige `ServiceOrders` nas rotas internas e Orders obtém esse token por client credentials, sem compartilhar refresh de clientes. RBAC não substitui propriedade: consulta de pedido e download de ingresso comparam o `sub` ao dono persistido. Acesso a recurso de outro usuário retorna 404; papel sem permissão recebe 403.

Delivery cria uma tarefa persistente por item de pedido. O worker toma um lease, gera PDF fora da transação e grava bytes/status/outbox em nova transação. Falha reagenda; reinício recupera lease vencido. O PDF contém evento, local, assento, instante e QR. O QR carrega dados assinados com Ed25519; a chave privada nasce em runtime e fica no volume de chaves, sem aparecer no repositório, eventos ou logs. A chave pública autenticada está em `/api/delivery/public-key`. Assinatura não implementa check-in: validação de entrada/uso único e revogação por cancelamento de evento estão adiadas.

## Quando uma dependência falha

| Falha | Comportamento |
|---|---|
| PostgreSQL Inventory | Reserva/confirmar indisponíveis; não vende por cache |
| Redis/IAM | Sessão/renovação indisponíveis; novo login pode ser necessário; estoque preservado |
| Keycloak | Login/refresh ou obtenção de service token pendentes; JWKS já em cache pode manter validação local |
| Gateway | Tentativa persistida fica pendente, com mesma chave e reconciliação |
| Kafka | Outbox acumula; confirmação/emissão podem atrasar; prazo continua autoritativo |
| Delivery/PDF | Tarefa e lease persistentes permitem retomada; SPA mostra emissão pendente |
| Mensagem inválida | Retry, quarentena e DLQ; sem inbox de sucesso nem efeito parcial |

O nó Kafka único e o PostgreSQL único são infraestrutura local sem alta disponibilidade. Não foram medidos throughput, p95/p99 ou metas de produção. Os checkpoints distinguem implementação, evidência executada e metas futuras.

## Referências técnicas das escolhas

Compatibilidade Java/Spring confirmada nos [requisitos oficiais do Spring Boot3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html). Configuração de listeners, erro e commit após processamento segue [Spring for Apache Kafka](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html). OIDC, PKCE, clientes e papéis seguem o [guia oficial do Keycloak](https://www.keycloak.org/securing-apps/overview). Os arquivos versionados fixam as versões concretas deste incremento; referências não substituem os resultados executados do checkpoint.
