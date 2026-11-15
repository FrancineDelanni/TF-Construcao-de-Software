# PulsePass – Pacote 2: Arquitetura e Infraestrutura de Desenvolvimento

> **Atualização de implementação — 15/11/2026:** O Pacote 2 permanece a baseline arquitetural de 19/10. O estágio de implementação, evidências e desvios desta proposta estão no [checkpoint do Pacote 3](11-checkpoint-pacote-3.md); execução e explicação nos documentos [12](12-guia-execucao.md) e [13](13-como-funciona.md). Descrições de componentes planejados abaixo não implicam que todos tenham sido implementados.

**PUCRS – Construção de Software – 2026/2 | Grupo 10**

**Integrantes:** João Vitor Krampe, Leticia Nunes, Francine Delanni, Luis Eduardo Trein e Enzo Picoli.

**Versão:** 1.0 | **Data:** 19/10/2026 | **Status:** material preparado para revisão do grupo.

## 1. Escopo desta entrega

O Pacote 2 transforma os requisitos do PulsePass em uma proposta de arquitetura e de organização do desenvolvimento. O enunciado, página 4, pede cinco descrições. Os arquivos abaixo constituem o pacote; diagramas estão em Mermaid, legíveis no GitHub ou em um preview compatível do editor.

| Exigência do enunciado | Documento | Conteúdo |
|---|---|---|
| Arquitetura C4 | [06 – Arquitetura C4](06-arquitetura-c4.md) | Contexto, contêineres, componentes dos cinco serviços, sequência de compra e estados |
| Modelo de dados conceitual | [07 – Modelo de dados](07-modelo-dados-conceitual.md) | Entidades, cardinalidades, regras, propriedade e complemento lógico |
| Estrutura de mensageria Kafka | [08 – Mensageria Kafka](08-estrutura-mensageria-kafka.md) | Tópicos, chaves, grupos, mensagens, outbox, inbox, retry e DLQ |
| Esquema da pipeline | [09 – Pipeline](09-pipeline-desenvolvimento.md) | Gatilhos, etapas, cobertura, Testcontainers, builds, Compose e releases |
| Processo de GCS | [10 – GCS](10-processo-gcs.md) | Git/GitHub, branches, commits, PRs, revisão, baselines e recuperação |

A descrição da pipeline e do ambiente é projeto para implementação posterior. Esta entrega não declara que código, testes, Compose, pipeline remota ou proteções de branches já existem. Os Pacotes 3 e 4 devem apresentar o estágio real e o produto completo, respectivamente. Prazos são consultados no Moodle.

## 2. Decisões da proposta

- Stack autorizada nesta preparação: Java/Spring Boot e React/TypeScript. Complementos propostos: Keycloak, PostgreSQL, Redis, Kafka e GitHub Actions.
- Cinco serviços com dados próprios: IAM, Inventory, Orders, Delivery e Analytics.
- PostgreSQL do Inventory é a autoridade dos assentos. Redis atende sessões/cache; sua perda não pode gerar sobre-venda.
- Pedidos, reservas e eventos são coordenados sem transação distribuída, por operações idempotentes, outbox e reconciliação.
- Gateway é fictício. `Pago` só ocorre depois da confirmação dos assentos dentro do prazo pelo Inventory.
- `Cancelado` é estado adicional para desistência antes de compra. Recusa definitiva permite nova tentativa no tempo restante; timeout exige reconciliação da mesma tentativa.
- Blockchain é opcional e não bloqueia a infraestrutura obrigatória.

Justificativas, alternativas e consequências estão na seção 1 da [Arquitetura C4](06-arquitetura-c4.md).

## 3. Coerência com o Pacote 1

Os documentos de visão, backlog, NFRs, processo e descrição dos mockups foram alinhados às regras do Pacote 2. Metas de desempenho são alvos iniciais de teste, ainda sem medição. As faixas R1/R2/R3 continuam sendo planejamento interno e não equivalem aos quatro pacotes acadêmicos.

| Pendência anterior | Tratamento nesta proposta |
|---|---|
| Cancelamento sem estado definido | `Cancelado` adicionado às regras e à máquina de estados |
| Recusa e timeout tratados de forma ambígua | Recusa definitiva volta a Reservado; timeout permanece pendente até reconciliação |
| Download sem área no R1 | T06 fornece download do ingresso; T09 continua área completa no R2 |
| Operador sem história explícita de consulta | US-31 passa a incluir consulta autorizada do ingresso e seu pedido associado |
| Login/renovação indefinidos | Keycloak OIDC, renovação mediada por IAM e refresh cifrado no Redis |
| Expiração dependente de TTL | Prazo persistido e decisão transacional no Inventory; limpeza e reconciliação pelo banco |
| Metas numéricas em aberto | Valores iniciais definidos nos NFRs, sujeitos a medição e ajuste registrado |

O HTML de mockups é a exportação visual original do Pacote 1; não foi reimplementado nesta preparação. A descrição em Markdown registra adaptações de login OIDC, respostas assíncronas e estados. Isso deve orientar o frontend futuro; não representa teste de navegação do HTML existente.

## 4. Revisão e envio

- [ ] Grupo revisou as regras e consegue explicar os diagramas e as decisões.
- [ ] Grupo confirmou a divisão de responsabilidades das próximas histórias.
- [ ] Acesso do professor ao repositório e aos mockups foi verificado.
- [ ] Material enviado corresponde ao commit/tag identificado no GCS.
- [ ] Formato de anexos exigido no Moodle foi conferido; exportar PDF apenas se necessário, preservando legibilidade dos diagramas.

Essas ações dependem de revisão e envio reais; não foram marcadas como realizadas. A preparação local não envia arquivos ao Moodle, não cria tags e não publica alterações no GitHub.
