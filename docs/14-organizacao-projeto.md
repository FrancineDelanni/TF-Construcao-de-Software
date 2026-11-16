# Organização do projeto

Este guia descreve a estrutura implementada após a reorganização de 16/11/2026. A mudança preserva os serviços, os contratos HTTP/Kafka, as migrações e as regras da jornada de compra.

## Mapa do repositório

| Local | Responsabilidade |
|---|---|
| `frontend/` | Aplicação React/TypeScript e sua configuração de build |
| `backend/` | Agregador Maven e serviços Java/Spring Boot |
| `infra/` | Configuração de Keycloak, PostgreSQL e tópicos Kafka |
| `scripts/` | Smoke, replay e manutenção do ambiente |
| `specs/` | Especificações do incremento e contratos de eventos |
| `docs/` | Requisitos, arquitetura, entregas e guias de operação |
| `adr/` | Histórico das decisões arquiteturais e seu status |
| `Mockups/` | Referências visuais das entregas acadêmicas |
| `ai/log/` | Registro do uso de IA nas entregas |

`docker-compose.yml` permanece na raiz para preservar os comandos de execução. Dockerfiles e configurações de build continuam próximos de suas aplicações.

## Frontend

```text
frontend/src/
├── main.tsx                    # Inicialização do React
├── app/
│   ├── App.tsx                 # Composição das telas e sessão
│   ├── AppLayout.tsx           # Cabeçalho, navegação e rodapé
│   ├── App.test.tsx            # Caracterização da jornada integrada
│   └── styles.css              # Estilos globais existentes
├── features/
│   ├── auth/                  # Sessão em memória, refresh e telas de acesso
│   ├── events/                # Catálogo, disponibilidade e seleção de assentos
│   ├── reservations/          # API da reserva, itens e relógio do servidor
│   ├── checkout/              # Coordenação da compra e chaves idempotentes
│   ├── orders/                # API, estados e listagem dos pedidos
│   └── tickets/               # Emissão assíncrona e download autenticado
└── shared/
    ├── http/                  # Transporte HTTP e decodificação de erros
    ├── ui/                    # Apresentação reutilizável de status
    └── formatting.ts          # Datas, valores e mensagens de erro
```

Cada funcionalidade mantém seus tipos e chamadas de API. Os componentes recebem dados e ações por propriedades; o `App` seleciona as telas, enquanto `usePurchaseFlow` coordena reserva, pedido, pagamento, polling e navegação. Essa coordenação permanece centralizada para conservar a duração das chaves idempotentes e das tentativas de pagamento durante a troca de telas.

O transporte em `shared/http` não conhece sessão ou componentes. `auth/session.ts` acrescenta autenticação e repetições após refresh. As outras funcionalidades usam essa entrada autenticada; tokens continuam somente em memória.

Regras para novas alterações:

- Colocar regras e tipos específicos na funcionalidade correspondente.
- Manter endereços HTTP nos arquivos `api.ts` e gerenciamento da sessão em `auth`.
- Usar `shared` apenas para código realmente compartilhado e sem dependência das funcionalidades.
- Manter testes próximos do código; os testes entre funcionalidades ficam em `app`.
- Criar componentes quando houver uma responsabilidade identificável, sem criar pastas vazias antecipadamente.

## Backend

Os módulos de negócio continuam independentes: `inventory`, `orders`, `delivery`, `iam` e o `gateway` de pagamento simulado. `common` é uma biblioteca técnica, não um serviço.

A convenção dentro de cada serviço é:

```text
br/pucrs/pulsepass/<servico>/
├── <Servico>Application.java
├── api/                       # Controllers e adaptação HTTP
├── application/               # Casos de uso, transações e portas necessárias
├── domain/                    # Regras e estados de negócio existentes
└── infrastructure/
    ├── persistence/           # JDBC/Redis e consultas SQL
    ├── clients/               # Clientes HTTP externos
    ├── messaging/             # Consumidores Kafka
    ├── scheduling/            # Agendamentos dos workers
    ├── security/              # Integrações e configuração de segurança
    ├── config/                # Propriedades de configuração
    └── pdf/                   # Renderização do ingresso em Delivery
```

Somente os pacotes utilizados por cada serviço existem. Os testes acompanham os pacotes das classes testadas; testes de integração que abrangem o serviço inteiro podem permanecer na raiz de testes do serviço.

### Responsabilidades e dependências

- `api` traduz HTTP e delega aos casos de uso. O controller de Orders chama `OrderCoordinator`, sem depender de um agendamento.
- `application` coordena o trabalho. `OrderService`, `InventoryService`, `InventoryCommands`, `PaidOrderHandler` e `TicketJobs` conservam os limites transacionais originais.
- As portas `OrderStore`, `InventoryStore`, `TicketStore` e `GatewayStore` descrevem operações de persistência. As implementações `Jdbc*Store` concentram o SQL e participam da transação do chamador; não abrem transações independentes.
- `InventoryClient` e `PaymentClient` separam as integrações de Orders. Seus adaptadores HTTP e o cache do token de serviço ficam em `infrastructure/clients`.
- `domain` preserva as políticas de reserva, estados de pedido e regras de assinatura/decisão já existentes.
- `SessionService` concentra validação da identidade, criação da sessão e renovação sob lock. O controller de IAM adapta cookies, CSRF e redirecionamentos; Redis, OIDC e criptografia têm pacotes próprios.

Esta é uma organização incremental. Alguns resultados de consulta ainda usam `Map<String,Object>`/`JsonNode`, e IAM utiliza diretamente seus adaptadores técnicos. A mudança não introduz entidades artificiais ou interfaces para todas as classes. Novas extrações devem ser justificadas por uma responsabilidade ou integração concreta.

### Código compartilhado

`backend/common` é dividido em:

| Pacote | Conteúdo |
|---|---|
| `web` | Erros e tradução para respostas HTTP |
| `security` | Configuração JWT e autorização compartilhada |
| `messaging` | Envelope, inbox, outbox, publicação e quarentena |

Regras de compra, reserva e emissão pertencem aos respectivos serviços. A reorganização não cria um modelo de negócio compartilhado entre eles.

### Garantias preservadas

A propriedade dos bancos, os tópicos, os formatos JSON e as migrações Flyway permanecem os mesmos. A extração dos repositórios conserva SQL, ordem de aquisição de locks, predicados de atualização e transações locais. Outbox/inbox continuam na mesma transação do efeito de negócio.

O limite de cobertura de linhas permanece em 70%. A configuração inclui explicitamente o novo pacote do renderizador PDF, que antes estava na raiz de Delivery, para que sua movimentação não retire esse código da verificação.

## Documentação atual e planejada

- [Como o sistema funciona](13-como-funciona.md): sequência implementada da compra.
- [Checkpoint do Pacote 3](11-checkpoint-pacote-3.md): funcionalidades completas, parciais e pendentes.
- [Guia de execução](12-guia-execucao.md): ambiente, demonstração e operação.
- [Arquitetura C4](06-arquitetura-c4.md): arquitetura alvo, com a nota sobre diferenças da implementação.
- [Decisões arquiteturais](../adr/): alternativas, consequências e status das propostas.

Os documentos acadêmicos numerados e os mockups mantêm seus caminhos para preservar as referências das entregas.

## Verificação

Na pasta `frontend`, executar `npm ci`, `npm run lint`, `npm run typecheck`, `npm run test:ci` e `npm run build`.

Na pasta `backend`, com JDK 21 e Docker ativos, executar `./mvnw -B -ntp clean verify`. O `clean` remove classes dos pacotes antigos; a verificação executa testes unitários, integrações reais e cobertura. Para testar a stack completa, seguir o guia de execução e executar `python3 scripts/smoke.py`.
