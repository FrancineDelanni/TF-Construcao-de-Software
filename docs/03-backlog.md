# PulsePass – Backlog: Épicos, Histórias de Usuário e Story Mapping

**PUCRS – Escola Politécnica | Construção de Software – 2026/2 | Projeto Integrador – Pacote 1**

**Grupo:** [Grupo 10] | **Versão:** 1.0 | **Data:** 12/10/2026

---

## 1. Como ler este documento

- **Story Map (seção 3):** visão da jornada do usuário (espinha dorsal) com as histórias organizadas em releases.
- **Épicos e histórias (seção 4):** detalhamento com critérios de aceite em formato *Dado / Quando / Então*, prioridade (MoSCoW) e estimativa (pontos de história, escala Fibonacci).
- **Rastreabilidade (seção 5):** relação entre os requisitos do enunciado e as histórias.
- **Planejamento de releases (seção 6).**

**Legenda de prioridade (MoSCoW):** **M** = Must have, **S** = Should have, **C** = Could have, **W** = Won't have (agora).

**Releases:**

| Release | Objetivo |
|---|---|
| **R1 – MVP (Walking Skeleton)** | Fluxo ponta a ponta mínimo: login → catálogo → reservar assento → pedido → pagamento simulado → Outbox → Kafka → ingresso emitido |
| **R2 – Robustez** | Expiração de reservas, idempotência completa, RBAC completo, tempo real (WebSocket), notificações |
| **R3 – Operação e Analítica** | Telemetria, painel em tempo real, validação de ingressos, testes de carga, observabilidade |

---

## 2. Épicos

| Épico | Nome | Frente do enunciado | Complexidade |
|---|---|---|---|
| **E1** | Identidade e Governança | IAM | Média |
| **E2** | Catálogo, Assentos e Bloqueio Concorrente | Inventário e Concorrência | Alta |
| **E3** | Pedidos, Checkout e Transações Resilientes | Transações e Checkout | Alta |
| **E4** | Emissão de Ingressos, PDF e Notificações | Mensageria e Despacho | Média-Alta |
| **E5** | Telemetria e DevOps | Análise e Operações | Alta |

---

## 3. Story Map

### 3.1 Espinha dorsal (atividades da jornada)

**Cliente:** Cadastrar/Autenticar → Explorar eventos → Escolher assentos → Reservar → Pagar → Receber ingresso → Acompanhar pedido

**Organizador:** Autenticar → Criar evento → Configurar lotes e mapa de assentos → Acompanhar vendas

**Operador:** Autenticar → Validar ingresso na entrada

**Admin:** Autenticar → Gerir organizadores → Auditar → Monitorar plataforma

### 3.2 Mapa (atividades × releases)

| | **Autenticar** | **Explorar eventos** | **Gerir evento** | **Escolher e reservar assentos** | **Pagar** | **Receber ingresso** | **Acompanhar / Operar** | **Operações e Qualidade** |
|---|---|---|---|---|---|---|---|---|
| **R1 – MVP** | US-01 Cadastro<br>US-02 Login JWT<br>US-08 Validação JWT nos serviços | US-11 Catálogo de eventos<br>US-12 Mapa de assentos | US-09 Criar evento<br>US-10 Lotes e mapa de assentos | US-13 Reserva atômica | US-18 Criar pedido<br>US-19 Máquina de estados<br>US-21 Pagamento simulado<br>US-22 Outbox<br>US-23 Relay do Outbox | US-26 Consumo idempotente<br>US-27 PDF + QR Code | US-25 Consultar pedido | US-36 Docker Compose<br>US-37 CI/CD |
| **R2 – Robustez** | US-03 Refresh token<br>US-04 Logout<br>US-05 RBAC<br>US-06 Organizadores | US-30 Meus ingressos | US-17 Editar/cancelar evento | US-14 Expiração em 10 min<br>US-15 Devolução em cancelamento<br>US-16 Evento de expiração | US-20 Idempotency-Key<br>US-24 Cancelar pedido | US-28 E-mail<br>US-29 Tempo real (WebSocket)<br>US-32 Retry e DLQ | US-07 Auditoria | US-38 Testcontainers<br>US-40 Observabilidade |
| **R3 – Operação** | US-41 Gestão de contas (Admin) | US-42 Filtros e busca no catálogo | | | | US-43 Prova de procedência do ingresso | US-31 Validar QR (Operador)<br>US-33 Ingestão de eventos<br>US-34 Agregação<br>US-35 Painel em tempo real | US-39 Teste de carga |

> **Dica de uso:** reproduza este mapa em Miro/FigJam/GitHub Projects para uma visão gráfica (atividades no topo, histórias abaixo, faixas de release) e exporte para o PDF final.

---

## 4. Épicos e Histórias de Usuário

**Formato:** *Como [papel], quero [ação] para [benefício].*

### Épico E1 – Identidade e Governança

| ID | História | Critérios de aceite | Prior. | Pts | Rel. |
|---|---|---|---|---|---|
| **US-01** | Como **Cliente**, quero me cadastrar na plataforma para poder comprar ingressos. | **Dado** que informo e-mail e senha válidos, **quando** envio o cadastro, **então** minha conta é criada com o papel Cliente. **Dado** um e-mail já cadastrado, **quando** tento cadastrar, **então** recebo erro 409 sem expor dados de outras contas. | M | 3 | R1 |
| **US-02** | Como **usuário**, quero autenticar com e-mail e senha para acessar funcionalidades conforme meu papel. | **Dado** credenciais válidas, **quando** faço login, **então** recebo um access token JWT assinado e um refresh token. **Dado** credenciais inválidas, **quando** faço login, **então** recebo 401 e a tentativa é registrada. | M | 5 | R1 |
| **US-03** | Como **usuário autenticado**, quero renovar minha sessão com refresh token para continuar usando o sistema sem novo login. | **Dado** um refresh token válido armazenado no Redis, **quando** o apresento, **então** recebo novo access token (e rotação do refresh token). **Dado** um refresh token expirado, revogado ou reutilizado, **quando** o apresento, **então** recebo 401 e a sessão é invalidada. | M | 5 | R2 |
| **US-04** | Como **usuário**, quero encerrar minha sessão (logout) para proteger minha conta. | **Dado** que faço logout, **quando** o refresh token é revogado no Redis, **então** ele não pode mais gerar novos access tokens. | S | 2 | R2 |
| **US-05** | Como **Admin**, quero que as rotas sejam protegidas por papéis (Cliente, Organizador, Operador, Admin) para garantir que cada perfil acesse apenas o que é permitido. | **Dado** um token com papel Cliente, **quando** acesso rota exclusiva de Organizador, **então** recebo 403. **Dado** ausência de token ou token inválido, **então** recebo 401. A matriz de permissões está documentada e testada. | M | 5 | R2 |
| **US-06** | Como **Admin**, quero cadastrar e gerir organizadores de eventos para controlar quem pode publicar eventos. | **Dado** que sou Admin, **quando** cadastro, ativo, suspendo ou removo um organizador, **então** o papel Organizador é concedido ou revogado e a ação é auditada. **Dado** um organizador suspenso, **quando** tenta criar evento, **então** recebe 403. | M | 5 | R2 |
| **US-07** | Como **Admin**, quero consultar a trilha de auditoria de credenciais para detectar e investigar acessos suspeitos. | **Dado** eventos de login, falha, refresh, logout e mudança de papel, **quando** consulto a auditoria, **então** vejo quem, quando, ação e origem, com filtros por usuário e período. Registros não podem ser alterados. | S | 5 | R2 |
| **US-08** | Como **desenvolvedor da plataforma**, quero que todos os microsserviços validem a assinatura do JWT para garantir segurança transversal. | **Dado** um token com assinatura inválida ou expirado, **quando** chamo qualquer serviço, **então** recebo 401. A validação usa as chaves públicas do provedor (Keycloak/Cognito) com cache de JWKS. | M | 5 | R1 |
| **US-41** | Como **Admin**, quero listar, bloquear e desbloquear contas de usuários para tratar abusos. | **Dado** que bloqueio uma conta, **quando** o usuário tenta autenticar ou renovar token, **então** o acesso é negado e a ação é auditada. | C | 3 | R3 |

### Épico E2 – Catálogo, Assentos e Bloqueio Concorrente

| ID | História | Critérios de aceite | Prior. | Pts | Rel. |
|---|---|---|---|---|---|
| **US-09** | Como **Organizador**, quero criar um evento (nome, local, data, descrição) para disponibilizá-lo à venda. | **Dado** dados válidos, **quando** crio o evento, **então** ele é salvo em rascunho e pertence ao meu organizador. **Dado** que não sou Organizador, **então** recebo 403. | M | 3 | R1 |
| **US-10** | Como **Organizador**, quero criar lotes e o mapa de assentos do evento para definir setores, preços e capacidade. | **Dado** um evento em rascunho, **quando** defino setores, fileiras, assentos e preço por lote, **então** cada assento recebe identificador único e estado Livre. **Dado** uma configuração inconsistente (assento duplicado), **então** recebo erro de validação. | M | 8 | R1 |
| **US-11** | Como **Cliente**, quero ver o catálogo de eventos para escolher o que comprar, mesmo em pico de acesso. | **Dado** eventos publicados, **quando** acesso o catálogo, **então** vejo a lista paginada com nome, data, local e disponibilidade. A leitura é servida de cache e atende à meta de latência p95 definida nos NFRs. | M | 5 | R1 |
| **US-12** | Como **Cliente**, quero ver o mapa de assentos de um evento com disponibilidade para escolher meus lugares. | **Dado** um evento, **quando** abro o mapa, **então** vejo assentos Livres, Reservados e Vendidos por setor. A disponibilidade reflete reservas recentes com atraso máximo definido nos NFRs. | M | 5 | R1 |
| **US-13** | Como **Cliente**, quero reservar assentos de forma atômica para ter certeza de que ninguém mais os comprará enquanto finalizo o pagamento. | **Dado** assentos livres, **quando** reservo, **então** todos são bloqueados em uma única operação atômica (tudo ou nada) e recebo o horário de expiração (agora + 10 min). **Dado** que dois clientes disputam o mesmo assento, **então** exatamente um obtém a reserva e o outro recebe 409. **Dado** que um dos assentos solicitados já está reservado, **então** nenhum assento é bloqueado. | M | 13 | R1 |
| **US-14** | Como **Cliente**, quero que minha reserva não paga expire em 10 minutos para que assentos abandonados voltem à venda. | **Dado** uma reserva sem pagamento, **quando** passam 10 minutos, **então** os assentos voltam a Livre atomicamente e o pedido passa a Expirado. A expiração é garantida mesmo após reinício de serviço (TTL + job de reconciliação). | M | 8 | R2 |
| **US-15** | Como **Cliente**, quero cancelar minha reserva para liberar os assentos se desistir da compra. | **Dado** uma reserva ativa, **quando** cancelo, **então** os assentos são devolvidos atomicamente e o pedido é cancelado. Cancelamentos repetidos não causam efeitos duplicados. | S | 3 | R2 |
| **US-16** | Como **plataforma**, quero publicar eventos de reserva criada, expirada e cancelada no broker para que outros serviços reajam de forma desacoplada. | **Dado** que uma reserva muda de estado, **então** um evento com identificador único é publicado no tópico correspondente (via Outbox) com schema versionado. | M | 5 | R2 |
| **US-17** | Como **Organizador**, quero editar ou cancelar meu evento para corrigir informações ou lidar com imprevistos. | **Dado** um evento com vendas, **quando** tento reduzir o mapa de assentos já vendidos, **então** a operação é bloqueada. **Dado** o cancelamento do evento, **então** são emitidos eventos para tratamento dos pedidos afetados. | C | 5 | R2 |
| **US-42** | Como **Cliente**, quero buscar e filtrar eventos por nome, data e cidade para achar rapidamente o que me interessa. | **Dado** filtros, **quando** busco, **então** a lista retorna apenas eventos compatíveis, mantendo a meta de latência. | C | 3 | R3 |

### Épico E3 – Pedidos, Checkout e Transações Resilientes

| ID | História | Critérios de aceite | Prior. | Pts | Rel. |
|---|---|---|---|---|---|
| **US-18** | Como **Cliente**, quero que um pedido seja criado a partir da minha reserva para prosseguir ao pagamento. | **Dado** uma reserva válida, **quando** inicio o checkout, **então** um pedido é criado vinculado aos assentos e ao valor total. **Dado** uma reserva expirada, **então** recebo erro 410/422. | M | 5 | R1 |
| **US-19** | Como **plataforma**, quero uma máquina de estados finitos do pedido (Criado, Reservado, Aguardando, Pago, Expirado) para garantir transições válidas. | **Dado** um pedido, **quando** ocorre uma transição inválida (ex.: Expirado → Pago), **então** ela é rejeitada e registrada. Todas as transições válidas têm teste unitário (cobertura de domínio ≥ 70%). | M | 8 | R1 |
| **US-20** | Como **Cliente**, quero que o reenvio da mesma requisição de checkout não gere cobrança duplicada para estar protegido de duplo clique e falhas de rede. | **Dado** duas requisições com a mesma **Idempotency-Key** e o mesmo corpo, **então** a segunda retorna a resposta da primeira sem novo efeito. **Dado** a mesma chave com corpo diferente, **então** recebo 422. As chaves expiram após período definido. | M | 8 | R2 |
| **US-21** | Como **Cliente**, quero pagar meu pedido para confirmar a compra. | **Dado** um pedido Reservado, **quando** submeto o pagamento, **então** o pedido passa a Aguardando e o gateway simulado é consultado. **Dado** aprovação, **então** o pedido passa a Pago. **Dado** recusa ou timeout, **então** o pedido segue regras definidas (nova tentativa ou Expirado). | M | 8 | R1 |
| **US-22** | Como **plataforma**, quero gravar o evento de domínio na mesma transação ACID da mudança de estado do pedido (**Transactional Outbox**) para evitar escrita dupla. | **Dado** que o pedido muda para Pago, **então** a atualização do pedido e o registro na tabela *outbox* ocorrem na mesma transação. Se a transação falhar, nenhum evento é registrado. | M | 8 | R1 |
| **US-23** | Como **plataforma**, quero um worker que leia a tabela outbox e publique no Kafka para garantir entrega *at-least-once* dos eventos. | **Dado** eventos pendentes, **quando** o worker executa, **então** publica no Kafka e marca como enviado. **Dado** que o Kafka está indisponível, **então** os eventos permanecem pendentes e são publicados após a recuperação, sem perda. | M | 8 | R1 |
| **US-24** | Como **Cliente**, quero cancelar um pedido ainda não pago para desistir da compra. | **Dado** um pedido Reservado ou Aguardando, **quando** cancelo, **então** o pedido é encerrado, os assentos são devolvidos e o evento é publicado via Outbox. | S | 3 | R2 |
| **US-25** | Como **Cliente**, quero consultar meus pedidos e seus estados para acompanhar a compra. | **Dado** que estou autenticado, **quando** consulto meus pedidos, **então** vejo apenas os meus, com estado e histórico. | M | 3 | R1 |

### Épico E4 – Emissão de Ingressos, PDF e Notificações

| ID | História | Critérios de aceite | Prior. | Pts | Rel. |
|---|---|---|---|---|---|
| **US-26** | Como **plataforma**, quero consumir eventos de pagamento confirmado de forma idempotente para emitir ingressos sem duplicidade. | **Dado** o mesmo evento entregue duas ou mais vezes, **então** apenas um conjunto de ingressos é emitido (deduplicação por id do evento). Falhas de processamento acionam retry. | M | 8 | R1 |
| **US-27** | Como **Cliente**, quero receber meu ingresso em PDF com QR Code e carimbo de tempo para entrar no evento. | **Dado** um pagamento confirmado, **então** é gerado um PDF por ingresso contendo dados do evento, assento, carimbo de tempo e QR Code com payload assinado. | M | 8 | R1 |
| **US-28** | Como **Cliente**, quero receber um comprovante por e-mail com meus ingressos para ter um registro fora da plataforma. | **Dado** ingressos emitidos, **então** um e-mail é enviado (simulado em dev) com os PDFs. Falha de envio não bloqueia a emissão e é reprocessada. | S | 5 | R2 |
| **US-29** | Como **Cliente**, quero ver o status do meu pedido atualizar em tempo real para saber quando o pagamento foi confirmado sem recarregar a página. | **Dado** que o pedido muda de estado, **então** a tela é atualizada via WebSocket em poucos segundos. Em caso de queda da conexão, o cliente reconecta e sincroniza o estado atual. | S | 5 | R2 |
| **US-30** | Como **Cliente**, quero acessar "Meus ingressos" para baixar ou reabrir meus ingressos quando precisar. | **Dado** ingressos emitidos, **quando** acesso a área, **então** vejo a lista e posso baixar o PDF. | S | 3 | R2 |
| **US-31** | Como **Operador**, quero validar o QR Code na entrada para impedir fraudes e reutilização de ingressos. | **Dado** um QR válido não utilizado, **quando** valido, **então** o ingresso é marcado como utilizado. **Dado** QR já utilizado, adulterado ou de outro evento, **então** a validação é recusada com motivo. | S | 5 | R3 |
| **US-32** | Como **plataforma**, quero reprocessamento e fila de mensagens com falha (retry + DLQ) para não perder emissões em caso de erros. | **Dado** uma mensagem que falha repetidamente, **então** ela é enviada a uma Dead Letter Queue com contexto do erro e pode ser reprocessada. | S | 5 | R2 |
| **US-43** | Como **plataforma**, quero ancorar provas dos eventos relevantes do ciclo de vida dos ingressos em blockchain para manter uma trilha de auditoria verificável. | **Dado** um evento de emissão, revogação ou validação de ingresso, **quando** a plataforma o processa, **então** a prova correspondente é ancorada e fica disponível para auditoria. **Dado** que a blockchain está indisponível, **então** a prova fica pendente para nova tentativa, sem interromper os fluxos de compra, emissão ou validação de ingressos. | C | 5 | R3 |

### Épico E5 – Telemetria e DevOps

| ID | História | Critérios de aceite | Prior. | Pts | Rel. |
|---|---|---|---|---|---|
| **US-33** | Como **plataforma**, quero ingerir eventos de navegação e compras para alimentar a análise em tempo real. | **Dado** ações de usuários (visualização, reserva, pagamento), **então** eventos são publicados em tópicos Kafka e consumidos pelo serviço de telemetria sem impactar o fluxo de compra. | S | 5 | R3 |
| **US-34** | Como **plataforma**, quero agregar métricas continuamente (ex.: vendas por minuto, conversão, ocupação) para exibição no painel. | **Dado** o fluxo de eventos, **então** as agregações são atualizadas em janelas definidas e armazenadas para consulta rápida. | S | 8 | R3 |
| **US-35** | Como **Admin/Organizador**, quero um painel analítico em tempo real para acompanhar a abertura de vendas. | **Dado** que acesso o painel, **então** vejo métricas atualizadas automaticamente. O Organizador vê apenas seus eventos. | S | 8 | R3 |
| **US-36** | Como **desenvolvedor**, quero subir todo o ambiente com `docker compose up` para reproduzir o sistema facilmente. | **Dado** uma máquina limpa com Docker, **quando** executo o comando, **então** sobem backend, frontend, PostgreSQL, Redis, Kafka e UI de mensageria, com healthchecks e README de execução. | M | 8 | R1 |
| **US-37** | Como **desenvolvedor**, quero um pipeline de CI para validar cada PR e merge na `develop`. | **Dado** um PR, **quando** é aberto ou atualizado, **então** o pipeline executa linters, testes e build; PR com falha não pode ser integrado. | M | 5 | R1 |
| **US-38** | Como **desenvolvedor**, quero testes de integração com Testcontainers (PostgreSQL, Kafka, Redis) para validar o sistema com dependências reais. | **Dado** o pipeline, **então** testes de integração sobem contêineres reais e cobrem: reserva concorrente, Outbox com indisponibilidade do Kafka e consumo idempotente. | M | 8 | R2 |
| **US-39** | Como **desenvolvedor**, quero testes de carga que simulem a abertura de vendas para validar as metas de desempenho. | **Dado** um cenário de rajada, **então** relatório mostra throughput, latência p95/p99, taxa de erro e confirma zero overselling. | C | 8 | R3 |
| **US-40** | Como **operador da plataforma**, quero logs estruturados, métricas e correlação de requisições para diagnosticar problemas. | **Dado** uma requisição, **então** logs trazem *correlation-id* propagado entre serviços e eventos; métricas básicas por serviço são expostas. | S | 5 | R2 |

---

## 5. Rastreabilidade: Enunciado → Histórias

| Requisito do enunciado | Histórias |
|---|---|
| Autenticação stateless via JWT com refresh tokens em chave-valor | US-02, US-03, US-04, US-08 |
| Autorização RBAC (Cliente, Organizador, Operador, Admin) | US-05 |
| Auditoria de credenciais | US-07 |
| Gestão cadastral de organizadores | US-06, US-41 |
| Criação de lotes e mapas de assentos | US-09, US-10 |
| Catálogo de eventos de alta leitura | US-11, US-12, US-42 |
| Alocação atômica de assentos | US-13 |
| Devolução atômica em cancelamento ou timeout (10 min) | US-14, US-15 |
| Eventos de expiração publicados no broker | US-16 |
| Máquina de estados do pedido | US-19 |
| Idempotency-Key | US-20 |
| Transactional Outbox + publicação no Kafka | US-22, US-23 |
| Integração com gateway financeiro simulado | US-21 |
| Consumo assíncrono de pagamentos confirmados (idempotente) | US-26, US-32 |
| Ingressos em PDF com carimbo de tempo e QR Code | US-27, US-31 |
| Prova criptográfica da integridade e procedência do histórico do ingresso | US-43 |
| Disparo multicanal e atualização de tela em tempo real | US-28, US-29, US-30 |
| Ingestão e agregação contínua de eventos | US-33, US-34 |
| Painel analítico em tempo real | US-35 |
| CI/CD e Docker Compose | US-36, US-37 |
| Testes (unitários, Testcontainers, carga) | US-19, US-38, US-39 |

---

## 6. Planejamento por Release

| Release | Histórias | Total de pontos |
|---|---|---|
| **R1 – MVP** | US-01, 02, 08, 09, 10, 11, 12, 13, 18, 19, 21, 22, 23, 25, 26, 27, 36, 37 | 3+5+5+3+8+5+5+13+5+8+8+8+8+3+8+8+8+5 = **116** |
| **R2 – Robustez** | US-03, 04, 05, 06, 07, 14, 15, 16, 17, 20, 24, 28, 29, 30, 32, 38, 40 | 5+2+5+5+5+8+3+5+5+8+3+5+5+3+5+8+5 = **85** |
| **R3 – Operação** | US-31, 33, 34, 35, 39, 41, 42, 43 (Could Have) | 5+5+8+8+8+3+3+5 = **45** |
| **Total** | 43 histórias | **246** |

> **Atenção:** o R1 está grande para um MVP. Se o tempo for curto, o grupo pode adiar US-12 (mapa visual, usando lista simples), US-37 (CI mínimo) e US-27 (PDF simples) para a segunda metade do R1, mantendo o walking skeleton: **login → reservar → pedido → pagamento simulado → Outbox → Kafka → ingresso**.

### 6.1 Sugestão de alocação por integrante (grupo de 5)

| Frente | Responsável principal | Apoio |
|---|---|---|
| E1 – IAM + front de autenticação | [nome 1] | [nome 5] |
| E2 – Inventário e concorrência | [nome 2] | [nome 3] |
| E3 – Pedidos, Outbox e checkout | [nome 3] | [nome 2] |
| E4 – Emissão e notificações + WebSocket | [nome 4] | [nome 5] |
| E5 – Telemetria, DevOps e testes | [nome 5] | [nome 4] |

---

## 7. Definition of Ready / Done

Ver [01-processo-desenvolvimento.md](01-processo-desenvolvimento.md), seções 2.2 e 2.3.