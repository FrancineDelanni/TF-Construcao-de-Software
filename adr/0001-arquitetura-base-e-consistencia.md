# ADR 0001 – Arquitetura base e consistência das reservas

**Data:** 19/10/2026 | **Status:** proposta documentada; revisão do grupo pendente.
**Escopo:** Pacote 2 do PulsePass.

## Contexto

O enunciado exige microsserviços, Kafka, provedor de identidade, zero overselling, reservas de dez minutos e tratamento de dual-write/idempotência. O repositório contém requisitos e mockups, sem aplicação executável. É necessário escolher fronteiras e regras antes da implementação.

O solicitante escolheu Java/Spring Boot e React/TypeScript durante a preparação. Essa autorização não é registrada como aprovação coletiva de todo o grupo nem como validação do professor.

## Alternativas consideradas

| Alternativa | Benefício | Limitação |
|---|---|---|
| Toda jornada síncrona via HTTP | Caminho inicial simples de acompanhar | Acopla checkout à emissão/notificações e mantém dependências temporais |
| Kafka em toda interação, inclusive leituras | Desacoplamento amplo | Complexidade e latência desnecessárias em catálogo e resposta de reserva |
| HTTP em operações curtas e eventos em propagação | UX de reserva direta e processamento assíncrono resiliente | Exige estados intermediários e reconciliação explícitos |

Para concorrência: Redis como única trava é rápido, mas não resolve sozinho consistência com PostgreSQL nem todas as falhas de lease. Locks e transações no banco proprietário permitem decidir reserva/confirmação/liberação no mesmo local; custam contenção e requerem medição. Proposta: terceira alternativa de comunicação com PostgreSQL como autoridade.

## Decisão proposta

1. Usar IAM, Inventory, Orders, Delivery e Analytics como cinco serviços, com bancos/usuários exclusivos. Keycloak é o provedor de identidade; não criar emissor JWT próprio.
2. Usar Redis para cache e refresh tokens cifrados, sem autoridade sobre estoque. Expiração tem prazo persistido, checagem após locks e worker de limpeza.
3. Centralizar confirmação, cancelamento e expiração no Inventory. Orders só se torna Pago após confirmação autoritativa. Gateway é simulado e não captura valores reais.
4. Gravar outbox na mesma transação dos fatos; consumidores usam inbox e unicidades de negócio. Entrega é at-least-once, sem promessa de exactly-once entre banco, Kafka e SMTP.
5. Adicionar Cancelado como extensão dos estados mínimos. Recusa definitiva permite retry enquanto houver prazo; timeout não autoriza uma nova tentativa independente.
6. Exibir download pela T06 no R1; área completa T09 continua R2. Incluir consulta do operador na US-31, sem criar uma história nova sem estimativa.
7. Descrever pipeline GitHub Actions e GCS neste pacote; implementação executável ocorre com o desenvolvimento. Blockchain permanece opcional e assíncrona.

## Consequências

É possível testar a regra de sobre-venda no banco proprietário. Há indisponibilidade temporária quando componentes críticos falham, sem liberar venda insegura. Mensagens e respostas intermediárias exigem polling/WebSocket, reconciliação, DLQ e evidências de testes de falha. Cinco serviços e dependências têm custo de operação; o Compose local de broker único não representa alta disponibilidade de produção.

Uma aprovação fictícia perto do prazo pode não resultar em compra se o comando só chegar ao Inventory depois de expirar. Isso preserva a regra estrita. Pagamentos reais exigiriam outra decisão sobre autorização, captura e compensação.

## Referências e revisão

[Arquitetura](../docs/06-arquitetura-c4.md), [Dados](../docs/07-modelo-dados-conceitual.md), [Kafka](../docs/08-estrutura-mensageria-kafka.md), [Pipeline](../docs/09-pipeline-desenvolvimento.md) e [GCS](../docs/10-processo-gcs.md).

Aceitação do grupo deve ser registrada em PR/revisão; somente então alterar o status deste ADR. Nenhum revisor humano foi atribuído ou aprovação presumida nesta preparação.
