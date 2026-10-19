# PulsePass

Plataforma distribuída de bilhetagem, reserva e acesso para megaeventos, proposta para a disciplina **Construção de Software – PUCRS – 2026/2**.

**Grupo 10:** João Vitor Krampe, Leticia Nunes, Francine Delanni, Luis Eduardo Trein e Enzo Picoli.

## Estado do projeto

O repositório contém requisitos, mockups e a documentação arquitetural do Pacote 2. A aplicação, Docker Compose e os workflows de CI ainda não foram implementados. Metas de desempenho e testes descritos são objetivos a verificar durante o desenvolvimento.

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

## Solução proposta

Java 21/Spring Boot, React/TypeScript, Keycloak, PostgreSQL, Redis e Kafka. Cinco serviços: IAM, Inventory, Orders, Delivery e Analytics. O ambiente futuro será orquestrado por Docker Compose e validado no GitHub Actions.

As garantias centrais são zero overselling, reservas com validade de dez minutos, checkout idempotente, outbox transacional, consumidores idempotentes e ingresso em PDF com QR assinado. PostgreSQL do Inventory decide confirmação, cancelamento e expiração; cache Redis não substitui essa decisão. Pagamentos são fictícios. Blockchain permanece uma extensão opcional.

## Como ler os diagramas

Abra os arquivos Markdown no GitHub ou em um preview com suporte a Mermaid. As visões C4 usam fluxogramas com fronteiras e tipos explícitos. O modelo conceitual usa diagrama entidade-relacionamento. Os blocos Mermaid são a fonte editável e devem acompanhar alterações futuras.

## Próximo marco

Revisar as decisões em grupo e implementar skeleton, contratos, ambiente e primeira jornada ponta a ponta conforme [pipeline](docs/09-pipeline-desenvolvimento.md). Os comandos de aplicação descritos nesse documento são planejados; não há comando de execução do produto disponível neste checkout.
