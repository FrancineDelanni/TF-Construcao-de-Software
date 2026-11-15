# PulsePass

Plataforma distribuída de bilhetagem, reserva e acesso para megaeventos, proposta para a disciplina **Construção de Software – PUCRS – 2026/2**.

**Grupo 10:** João Vitor Krampe, Leticia Nunes, Francine Delanni, Luis Eduardo Trein e Enzo Picoli.

## Estado do projeto

O incremento do **Pacote 3** implementa login Keycloak, catálogo, reserva atômica de assentos, pedidos, pagamento simulado, confirmação pelo Inventory e emissão de ingresso PDF por Kafka. Há APIs Spring Boot, frontend React/TypeScript, migrações Flyway, Compose e workflow de CI. Metas de desempenho continuam objetivos sem benchmark.

Comece pelos documentos:

- [11 — Checkpoint e limites do Pacote 3](docs/11-checkpoint-pacote-3.md)
- [12 — Como executar e demonstrar](docs/12-guia-execucao.md)
- [13 — Como o sistema funciona](docs/13-como-funciona.md)

Com Docker ativo, na raiz:

```sh
test -f .env || cp .env.example .env
docker compose up --build -d --wait --wait-timeout 300
```

Abra **http://localhost:8080**. Entre como `cliente1` ou `cliente2`, senha fictícia `PulsePass123!`. Keycloak: http://localhost:8081; Kafka UI: http://localhost:8082. O guia documenta todos os perfis, portas, logs e cenários.

Parar preservando dados: `docker compose down`. Reset com `--volumes` é destrutivo e deve ser uma decisão explícita.

## Entrega 2

Comece pelo **[índice do Pacote 2](docs/00-entrega-2.md)**, que relaciona os documentos às exigências do enunciado.

| Documento | Conteúdo |
|---|---|
| [06 – Arquitetura C4](docs/06-arquitetura-c4.md) | Contexto, contêineres, componentes e fluxo de compra |
| [07 – Modelo de dados conceitual](docs/07-modelo-dados-conceitual.md) | Entidades, cardinalidades, regras e propriedade dos dados |
| [08 – Mensageria Kafka](docs/08-estrutura-mensageria-kafka.md) | Tópicos, contratos, grupos, idempotência, retry e DLQ |
| [09 – Pipeline](docs/09-pipeline-desenvolvimento.md) | Esquema de CI/CD, testes e ambiente de desenvolvimento |
| [10 – GCS](docs/10-processo-gcs.md) | Branches, PRs, revisão, baselines, releases e recuperação |

## Entrega 1

| Documento | Conteúdo |
|---|---|
| [01 – Processo de desenvolvimento](docs/01-processo-desenvolvimento.md) | Ciclo, specs, uso de IA e revisão humana |
| [02 – Visão do produto](docs/02-visao.md) | Problema, usuários, objetivos, escopo e riscos |
| [03 – Backlog e Story Mapping](docs/03-backlog.md) | Cinco épicos e 43 histórias com critérios de aceite |
| [04 – Requisitos não funcionais](docs/04-requisitos-nao-funcionais.md) | Consistência, desempenho, segurança, testes e metas iniciais |
| [05 – Mockups e navegação](Mockups/05-mockups-mapa-navegacao.md) | Dezessete telas, fluxos e decisões de interface |
| [HTML dos mockups](<Mockups/PulsePass – Mockups e Mapa de Navegação.html>) | Exportação visual original do Pacote 1 |

Os documentos do Pacote 1 foram alinhados à proposta do Pacote 2. O HTML permanece como referência visual original; as adaptações estão descritas no documento 05. R1/R2/R3 são releases funcionais internas, distintas dos quatro pacotes acadêmicos.

## Solução implementada e fronteiras

Java 21/Spring Boot, React/TypeScript, Keycloak, PostgreSQL, Redis e Kafka. IAM, Inventory, Orders e Delivery executam no Compose, junto com gateway fictício e infraestrutura. Analytics permanece uma fronteira planejada, sem aplicação vazia. GitHub Actions está configurado para PRs e pushes em `develop`; execução remota e proteções de branch não são presumidas.

As garantias centrais são zero overselling, reservas com validade de dez minutos, checkout idempotente, outbox transacional, consumidores idempotentes e ingresso em PDF com QR assinado. PostgreSQL do Inventory decide confirmação, cancelamento e expiração; Redis não substitui essa decisão. Pagamentos são fictícios. Blockchain permanece uma extensão opcional.

## Como ler os diagramas

Abra os arquivos Markdown no GitHub ou em um preview com suporte a Mermaid. As visões C4 usam fluxogramas com fronteiras e tipos explícitos. O modelo conceitual usa diagrama entidade-relacionamento. Os blocos Mermaid são a fonte editável e devem acompanhar alterações futuras.

## Código e testes

`backend/common` contém segurança JWT, outbox/inbox e retry/quarentena; `inventory` é a autoridade das reservas; `orders` integra a máquina de estados original ao checkout durável; `delivery` emite PDF/QR assinado; `iam` protege sessões Redis; `gateway` simula resultados. Cada banco tem proprietário e migrações exclusivos.

Com JDK 21 e Docker ativo:

```sh
cd backend
./mvnw -B -ntp verify
```

Com Node 22.20.0:

```sh
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

Smoke da stack já iniciada, com Python 3:

```sh
python3 scripts/smoke.py
```

O Maven Wrapper baixa a versão fixada e as dependências na primeira execução. Windows: `mvnw.cmd`. Testcontainers usa PostgreSQL, Kafka e Redis reais; Docker ausente provoca falha explícita, sem transformar integração em teste simulado.

## Próximo marco

Gestão de eventos/contas, auditoria administrativa, e-mail, WebSocket, check-in/revogação, Analytics e testes de carga estão pendentes. O catálogo usa seed de demonstração e polling; o checkpoint separa histórias completas, parciais e adiadas. R1/R2/R3 continuam distintos dos pacotes acadêmicos.
