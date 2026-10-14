# PulsePass – Requisitos Não-Funcionais (NFRs)

**PUCRS – Escola Politécnica | Construção de Software – 2026/2 | Projeto Integrador – Pacote 1**

**Grupo:** [nome do grupo] | **Versão:** 1.0 | **Data:** 12/10/2026

---

## 1. Introdução

Os requisitos não-funcionais definem **como bem** o PulsePass deve operar. Cada requisito tem um identificador, uma **métrica verificável**, o **método de verificação** e as histórias/objetivos relacionados. Valores entre colchetes **[ ]** devem ser calibrados pelo grupo conforme a capacidade do ambiente de testes.

**Prioridade:** **M** = Must, **S** = Should, **C** = Could.

---

## 2. Consistência e Concorrência

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-CON-01 | **Zero overselling**: um assento nunca pode ser vendido ou reservado para mais de um cliente ao mesmo tempo | 0 duplicidades em teste com **≥ 1.000** requisições simultâneas ao mesmo assento (exatamente 1 sucesso) | Teste de integração concorrente (Testcontainers) + teste de carga | M | OBJ-01, US-13 |
| NFR-CON-02 | Reserva de múltiplos assentos é **atômica** (tudo ou nada) | Reserva parcial: 0 ocorrências em testes | Teste de integração | M | US-13 |
| NFR-CON-03 | **Expiração estrita** de reservas em 10 minutos | Assentos liberados entre 10:00 e 10:05 min após a reserva (tolerância [± 5 s]) | Teste de integração com relógio controlado + teste de ponta a ponta | M | OBJ-02, US-14 |
| NFR-CON-04 | Devolução de assentos é **atômica** e idempotente (cancelamento ou timeout) | Devolução repetida não altera estoque além do correto | Teste unitário e de integração | M | US-14, US-15 |
| NFR-CON-05 | Transições do pedido seguem a máquina de estados; transições inválidas são rejeitadas | 100% das transições inválidas rejeitadas | Testes unitários da máquina de estados | M | US-19 |
| NFR-CON-06 | Expiração resiliente a falhas: reservas expiradas após reinício de serviço também são liberadas | Job de reconciliação libera reservas vencidas em até [60 s] após recuperação | Teste de integração com reinício de contêiner | S | R-09 |

## 3. Idempotência e Resiliência de Mensageria

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-IDP-01 | **Idempotency-Key** em operações de escrita do checkout | Mesma chave + mesmo corpo = mesmo resultado, 0 cobranças duplicadas; mesma chave + corpo diferente = 422 | Testes de API e de integração | M | OBJ-05, US-20 |
| NFR-IDP-02 | **Consumidores idempotentes** | Mensagem entregue N vezes produz efeito 1 vez (0 ingressos duplicados) | Teste de integração com Kafka real reenviando mensagens | M | US-26 |
| NFR-RES-01 | **Sem escrita dupla**: eventos são gravados via Transactional Outbox na mesma transação ACID | 0 pedidos alterados sem evento correspondente e 0 eventos sem alteração | Teste de falha (derrubar Kafka/serviço no meio do fluxo) | M | OBJ-04, US-22 |
| NFR-RES-02 | Entrega **at-least-once** com recuperação após indisponibilidade do Kafka | Eventos pendentes publicados em até [30 s] após retorno do Kafka; 0 perdas | Teste de integração (Testcontainers pausando/retomando o broker) | M | US-23 |
| NFR-RES-03 | **Tolerância a falhas parciais**: falha em emissão/e-mail não afeta checkout | Checkout mantém disponibilidade com serviço de emissão indisponível | Teste de caos básico (parar contêiner) | S | US-26, US-28 |
| NFR-RES-04 | **Retry com backoff e DLQ** para mensagens com falha | Mensagem com erro vai à DLQ após [N] tentativas, sem bloquear a partição | Teste de integração | S | US-32 |
| NFR-RES-05 | **Timeout e circuit breaker** nas chamadas ao gateway financeiro | Timeout configurado ([3 s]); falhas repetidas abrem o circuito | Teste com gateway simulado lento/instável | S | US-21 |
| NFR-RES-06 | **Ordenação por entidade**: eventos do mesmo pedido/assento mantêm ordem | Chave de partição = id do pedido/assento | Revisão de design + teste | S | Estrutura Kafka |

## 4. Desempenho e Escalabilidade

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-PER-01 | Leitura do catálogo de eventos (alta leitura) | p95 < **[200 ms]**, p99 < **[500 ms]** sob **[X] req/s** | Teste de carga (k6/Gatling) | M | OBJ-03, US-11 |
| NFR-PER-02 | Reserva de assentos sob rajada | p95 < **[500 ms]** com **[Y] reservas/s** e 0 overselling | Teste de carga | M | US-13 |
| NFR-PER-03 | Atraso de propagação no mapa de assentos | Disponibilidade atualizada em até **[2 s]** | Teste de integração/E2E | S | US-12 |
| NFR-PER-04 | Latência de emissão de ingresso após pagamento | p95 < **[10 s]** do evento de pagamento ao PDF disponível | Métrica no pipeline de eventos | S | US-26, US-27 |
| NFR-PER-05 | Uso eficiente do pool de conexões ao banco | Nenhum esgotamento do pool durante o teste de rajada; filas de espera < **[1 s]** | Teste de carga + métricas | M | Contexto do enunciado |
| NFR-ESC-01 | **Escalabilidade horizontal** de serviços sem estado | Escalar réplicas do serviço (ex.: 1 → 3) sem alteração de código e sem perda de correção | Teste com `docker compose --scale` | S | RES-01 |
| NFR-ESC-02 | Consumidores Kafka escaláveis por partições | Consumer group com N instâncias processa em paralelo sem duplicidade de efeito | Teste de integração | S | Estrutura Kafka |

## 5. Segurança

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-SEG-01 | Todas as rotas (exceto públicas explícitas) exigem **JWT válido** | 100% das rotas protegidas; token inválido/expirado = 401 | Testes de API automatizados por rota | M | OBJ-06, US-08 |
| NFR-SEG-02 | **RBAC** em todas as rotas conforme matriz de permissões | Acesso indevido = 403; matriz documentada e testada | Testes de autorização | M | US-05 |
| NFR-SEG-03 | Tokens com **assinatura criptográfica** (algoritmo assimétrico, ex.: RS256) e curta duração | Access token ≤ **[15 min]**; refresh token com rotação e revogação | Revisão de configuração + testes | M | US-02, US-03 |
| NFR-SEG-04 | Refresh tokens em **armazenamento chave-valor** (Redis) com TTL e revogação | Token revogado não gera novo access token | Teste de integração | M | US-03, US-04 |
| NFR-SEG-05 | **Auditoria** de eventos de autenticação e gestão de papéis | 100% dos logins, falhas, refresh, logout e mudanças de papel registrados, de forma imutável | Teste + revisão | S | US-07 |
| NFR-SEG-06 | Senhas armazenadas apenas pelo provedor de identidade (hash forte); nunca em logs | 0 senhas ou tokens completos em logs | Revisão + varredura automática de logs | M | US-01 |
| NFR-SEG-07 | **QR Code** do ingresso é assinado e não forjável | QR adulterado é rejeitado na validação | Teste unitário/integração | S | US-27, US-31 |
| NFR-SEG-08 | Proteção contra abuso: limite de taxa (rate limiting) em rotas sensíveis | Limite por usuário/IP configurado ([N] req/min) em login e reserva | Teste de API | S | US-02, US-13 |
| NFR-SEG-09 | Segredos fora do repositório | 0 segredos no Git; varredura no CI | Ferramenta de secret scanning no pipeline | M | US-37 |
| NFR-SEG-10 | Validação de entrada e proteção contra OWASP Top 10 relevantes | Entradas validadas; sem SQL injection (queries parametrizadas) | Revisão + análise estática | S | — |
| NFR-SEG-11 | Isolamento de dados: Cliente acessa apenas seus pedidos/ingressos; Organizador apenas seus eventos | 0 acessos cruzados em teste | Testes de autorização por recurso | M | US-25, US-35 |

## 6. Disponibilidade e Tolerância

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-DIS-01 | **Serviços independentes**: falha de um serviço não derruba os demais | Catálogo continua operando com Emissão e Telemetria fora do ar | Teste de caos básico | S | RES-01 |
| NFR-DIS-02 | **Degradação graciosa** | Telemetria indisponível não impede compras; WebSocket indisponível cai para consulta periódica (polling) | Teste manual/automatizado | S | US-29, US-33 |
| NFR-DIS-03 | **Healthchecks** em todos os contêineres | Todos os serviços expõem `/health`; Compose usa `depends_on` com condição de saúde | Verificação no Compose | M | US-36 |
| NFR-DIS-04 | Reconexão de clientes WebSocket | Cliente reconecta e ressincroniza estado em até **[10 s]** | Teste E2E | C | US-29 |

## 7. Observabilidade

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-OBS-01 | **Logs estruturados** (JSON) com nível, serviço e correlation-id | 100% dos serviços; correlation-id propagado em HTTP e Kafka | Inspeção de logs | S | US-40 |
| NFR-OBS-02 | **Métricas** técnicas por serviço (latência, taxa de erro, throughput, lag de consumidores Kafka) | Endpoint de métricas disponível | Verificação no painel | S | US-34, US-40 |
| NFR-OBS-03 | **Painel em tempo real** de métricas de negócio | Atualização a cada **[≤ 5 s]** | Teste manual/E2E | S | OBJ-09, US-35 |
| NFR-OBS-04 | UI de mensageria (Kafka) disponível no ambiente | Interface acessível no Compose para inspecionar tópicos e consumidores | Verificação no Compose | M | US-36 |
| NFR-OBS-05 | **Tracing distribuído** (opcional) | Traço ponta a ponta de uma compra | Demonstração | C | — |

## 8. Qualidade, Testabilidade e Manutenibilidade

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-TST-01 | Cobertura de **testes unitários do domínio** | **≥ 70%** (inclui todas as máquinas de estado) | Relatório de cobertura no CI | M | OBJ-07 |
| NFR-TST-02 | **Testes de integração com Testcontainers** reais (PostgreSQL, Kafka e Redis) | Cenários: reserva concorrente, Outbox com falha do Kafka, consumo idempotente, expiração | Execução no CI | M | US-38 |
| NFR-TST-03 | **Testes de carga** da abertura de vendas | Relatório com throughput, p95/p99 e 0 overselling | Execução e relatório | C | US-39 |
| NFR-TST-04 | Testes de contrato (OpenAPI/AsyncAPI) | Contratos validados no CI; quebra de contrato falha o build | Linter/validador de contrato | S | — |
| NFR-MAN-01 | **Linters e formatação** automáticos | 0 erros de lint em PRs integrados | CI | M | US-37, OBJ-10 |
| NFR-MAN-02 | **Pipeline de CI** em todo PR e merge na `develop` | 100% dos merges com pipeline verde | Regras de proteção de branch | M | US-37 |
| NFR-MAN-03 | **Modularidade**: cada microsserviço com seu próprio banco/schema (database per service) | Nenhum acesso direto ao banco de outro serviço | Revisão arquitetural | M | RES-01 |
| NFR-MAN-04 | **Documentação viva**: C4, contratos e README atualizados a cada entrega | Docs revisadas nos PRs relevantes | Checklist do PR | S | R-08 |
| NFR-MAN-05 | Versionamento de schemas de eventos (evolução compatível) | Mudança incompatível exige nova versão | Revisão + validação de schema | S | US-16 |

## 9. Reprodutibilidade e DevOps

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-REP-01 | **Ambiente completo com um comando** | `docker compose up` sobe backend, frontend, PostgreSQL, Redis, Kafka e UI de mensageria em máquina limpa | Execução em máquina limpa pelo revisor | M | OBJ-08, US-36 |
| NFR-REP-02 | **Configuração por variáveis de ambiente** (12-factor) | Sem valores fixos de ambiente no código | Revisão + CI | M | US-36 |
| NFR-REP-03 | **Dados de demonstração (seed)** automáticos | Evento com mapa de assentos e usuários de teste criados no startup | Execução do Compose | S | US-36 |
| NFR-REP-04 | **Migrações de banco versionadas** (ex.: Flyway/Liquibase) | Banco criado do zero apenas por migrações | CI + Compose | M | — |
| NFR-REP-05 | **Builds reprodutíveis** (imagens versionadas, dependências travadas) | Lockfiles versionados; imagens com tag explícita | Revisão | S | — |

## 10. Usabilidade e Acessibilidade

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-USA-01 | **Contador visível** da expiração da reserva (10 min) | Exibido em todas as telas do fluxo de reserva/checkout, sincronizado com o horário do servidor | Teste E2E / revisão de UI | M | US-13, US-14 |
| NFR-USA-02 | Interface **responsiva** (desktop e celular) | Layout funcional em larguras de 360 px a 1920 px | Teste manual | S | — |
| NFR-USA-03 | Mensagens de erro claras para falhas esperadas (assento indisponível, reserva expirada, pagamento recusado) | Todas as falhas do fluxo têm mensagem e ação sugerida | Revisão de UI | S | US-13, US-21 |
| NFR-USA-04 | Acessibilidade básica (contraste, navegação por teclado, rótulos) | Atende nível **[WCAG 2.1 AA]** nas telas principais | Auditoria (ex.: Lighthouse/axe) | C | — |
| NFR-USA-05 | Feedback imediato durante filas de pico (estado "processando") | Usuário sempre vê estado de carregamento; sem tela em branco | Teste manual | S | US-29 |

## 11. Conformidade e Dados

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-LGP-01 | Minimização de dados pessoais (alinhado à LGPD) | Apenas dados necessários (nome, e-mail); sem dados reais de pagamento armazenados | Revisão de modelo de dados | S | — |
| NFR-LGP-02 | Dados de teste fictícios | 0 dados reais em repositório e ambientes de demonstração | Revisão | M | Processo com IA |
| NFR-LGP-03 | Retenção de logs e auditoria definida | Política de retenção documentada ([90 dias]) | Revisão | C | US-07 |

## 12. Prova de procedência em blockchain (Could Have)

Estes requisitos aplicam-se somente se o grupo priorizar a US-43 na R3. A blockchain é uma camada de prova complementar; não substitui a fonte operacional, as garantias existentes de assinatura, concorrência e validação, nem se torna dependência síncrona do fluxo principal.

| ID | Requisito | Métrica / Critério | Verificação | Prior. | Relacionados |
|---|---|---|---|---|---|
| NFR-BLK-01 | **Minimização e privacidade on-chain** | 0 dados pessoais, conteúdo do ingresso, QR/PDF, número de pedido ou identificador de assento em transações, eventos ou estado do contrato | Inspeção do contrato e testes dos payloads publicados | M | US-43, NFR-LGP-01 |
| NFR-BLK-02 | **Integridade verificável** | 100% dos registros de prova válidos conferem com o compromisso on-chain; qualquer alteração nos dados apresentados é detectada e reportada como não correspondente | Testes unitários com vetores válidos e dados adulterados; teste de integração com a rede selecionada | M | OBJ-11, US-43 |
| NFR-BLK-03 | **Idempotência da ancoragem** | Reentregar N vezes o mesmo evento de domínio produz exatamente um compromisso lógico para aquele evento | Teste de integração reprocessando mensagens e simulando retry após timeout/ack perdido | M | US-22, US-23, US-26, US-32, US-43 |
| NFR-BLK-04 | **Degradação graciosa e recuperação** | Blockchain fora do ar não impede nem altera o resultado de compra, emissão ou validação convencional; 0 eventos aceitos são perdidos e a fila pendente é drenada em até [5 min] após recuperação sob carga nominal | Teste de falha/recuperação da rede e comparação dos resultados dos fluxos principais | M | OBJ-11, US-43 |
| NFR-BLK-05 | **Autorização e auditoria operacional** | 100% das operações de gravação são feitas por identidade autorizada; apenas responsáveis autorizados podem consultar os dados operacionais da prova; operações não autorizadas são recusadas e registradas | Testes de autorização do serviço/contrato e revisão da gestão de chaves e logs | M | US-43, NFR-SEG-05 |
| NFR-BLK-06 | **Sem bloqueio síncrono por blockchain** | 0 chamadas à rede blockchain ou espera por finalização no caminho síncrono de checkout, emissão ou leitura/validação do QR; variação de p95 desses fluxos com a integração habilitada ≤ 5% em teste comparável | Revisão de arquitetura e teste de carga com a rede acessível e indisponível | M | OBJ-11, US-43 |

---

## 13. Matriz de Rastreabilidade: Objetivos → NFRs

| Objetivo (Visão) | NFRs |
|---|---|
| OBJ-01 Zero overselling | NFR-CON-01, 02, 05; NFR-PER-02 |
| OBJ-02 Expiração em 10 min | NFR-CON-03, 04, 06; NFR-USA-01 |
| OBJ-03 Resiliência a picos | NFR-PER-01 a 05; NFR-ESC-01, 02 |
| OBJ-04 Evitar escrita dupla | NFR-RES-01, 02 |
| OBJ-05 Idempotência | NFR-IDP-01, 02 |
| OBJ-06 Segurança | NFR-SEG-01 a 11 |
| OBJ-07 Qualidade | NFR-TST-01 a 04 |
| OBJ-08 Reprodutibilidade | NFR-REP-01 a 05; NFR-DIS-03 |
| OBJ-09 Observabilidade | NFR-OBS-01 a 05 |
| OBJ-10 Governança de código | NFR-MAN-01, 02; NFR-SEG-09 |
| OBJ-11 Prova opcional de procedência | NFR-BLK-01 a 06 |

---

## 14. Parâmetros a Definir pelo Grupo

| Parâmetro | Sugestão inicial | Decisão do grupo |
|---|---|---|
| [X] req/s de leitura do catálogo | 500 a 2.000 req/s (ambiente local) | |
| [Y] reservas/s | 100 a 500 reservas/s | |
| p95 do catálogo | 200 ms | |
| Tolerância de expiração | ± 5 s | |
| Duração do access token | 15 min | |
| Nº de tentativas antes da DLQ | 3 a 5 | |
| Timeout do gateway | 3 s | |
| Janela de agregação do painel | 5 s | |
| Drenagem da fila de ancoragem após recuperação | 5 min (sob carga nominal) | |