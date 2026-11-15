# PulsePass — checkpoint do Pacote 3

**Início:** 09/11/2026. **Validação final:** 15/11/2026. **Revisão humana do grupo:** pendente. Pacote 3 é o estágio corrente com documentação atualizada; não é a release interna R3 nem a conclusão das 43 histórias.

Repositório confirmado em `git remote -v`: [FrancineDelanni/TF-Construcao-de-Software](https://github.com/FrancineDelanni/TF-Construcao-de-Software). O solicitante autorizou commit/push em15/11/2026; a publicação será identificada pelo commit do incremento no histórico. Não foi criada tag acadêmica nem enviado material ao Moodle.

## Objetivo e escopo

Uma jornada integrada: login OIDC → catálogo seed → seleção/reserva multisseat → pedido → gateway simulado → decisão do Inventory → Outbox/Kafka → ingresso PDF autenticado. Foco em concorrência, prazo autoritativo, idempotência e recuperação, sem implementar toda a administração da plataforma.

Estado inicial confirmado: somente `backend/orders` com `OrderState` e 42 casos parametrizados, Maven Wrapper e documentação. Existiam alterações locais em `.gitignore`, README e pipeline, além de diretórios não rastreados; foram preservados e ampliados. Não houve reset de volumes, commit ou push.

## Componentes reais

IAM (sessões Redis), Inventory, Orders, Delivery e gateway são processos Spring Boot. Inventory/Orders/Delivery/gateway/Keycloak têm bancos e usuários PostgreSQL próprios. IAM não precisa de SQL nesta fatia; não há aplicação Analytics vazia. Frontend React/TypeScript usa APIs reais via Nginx.

Java21, Spring Boot3.5.7, Maven3.9.11, PostgreSQL17.6, Redis7.4.6, Kafka3.9.1 KRaft, Keycloak26.4.2, React19.1.1, TypeScript5.9.3, Vite7.3.8, Node22.20.0; versões de plugins/bibliotecas nos POMs/lockfile e imagens nos Dockerfiles/Compose. PDFBox3.0.6, ZXing3.5.3, Ed25519 da JDK. Testcontainers1.21.3, JUnit5.13.4, JaCoCo0.8.14. O build local usou JDK25 com release21; as imagens e CI usam Java21.

## Rastreabilidade e limites por história

“Implementada” abaixo descreve comportamento e evidência técnica desta fatia; não substitui Definition of Done com revisão humana e CI remota.

| Histórias | Situação neste incremento |
|---|---|
| US-02,03,04 | Fluxos login OIDC, refresh cifrado/rotacionado e logout implementados. Eventos de identidade habilitados no Keycloak; auditoria consultável da plataforma adiada |
| US-05,08 | JWT assinatura/issuer/audience/prazo, papéis e proprietário nas APIs implementadas; matriz administrativa completa depende das rotas futuras |
| US-09,10 | **Parciais:** eventos publicados, setores, lote inicial, assentos e preços por seed Flyway; criar/editar/publicar por Organizador não implementado |
| US-11 | **Parcial:** catálogo real e disponibilidade; paginação, cache Redis de catálogo e meta p95 não implementados/medidos |
| US-12,13 | Mapa e reserva multisseat atômica, disputa e snapshot congelado implementados |
| US-14 | Prazo de dez minutos, worker e recuperação implementados; limite de limpeza sob carga continua meta sem benchmark |
| US-15 | **Parcial:** liberação por cancelamento do pedido; endpoint direto de cancelamento de reserva sem pedido adiado |
| US-16 | Eventos de reserva por outbox implementados |
| US-18 | **Parcial em relação ao aceite original:** intenção durável e vínculo único; criação responde202 e pode convergir para Expirado, em vez de rejeição síncrona410 em todos os casos |
| US-19 | Máquina existente integrada, estados/transições e histórico persistido implementados |
| US-20 | Idempotência de operações implementada; contrato409 para payload divergente. Resposta pode trazer estado atual da operação; retenção das chaves sem limpeza automática |
| US-21–27 | Gateway simulado, outbox, relay, cancelamento autoritativo, pedidos próprios, consumidor idempotente e PDF/QR implementados |
| US-29,30 | **Parciais:** polling e download pelos pedidos; WebSocket e área completa de ingressos adiados |
| US-32 | Retry, quarentena, bloqueio por agregado, DLQ e script de replay implementados; intervenção administrativa continua manual/documentada |
| US-36 | Compose, migrações, realm, tópicos, seed e health/readiness implementados; evidências finais abaixo |
| US-37 | **Parcial:** workflow PR/push develop versionado; execução remota e proteção de branch não verificadas |
| US-38 | Testes reais PostgreSQL/Kafka/Redis implementados e executados; resultados abaixo |
| US-40 | **Parcial:** health/readiness, IDs e logs operacionais; telemetria completa e correlação ponta a ponta não implementadas |
| US-01,06,07,17,28,31,33,34,35,39,41,42,43 | **Pendentes:** cadastro, gestão/auditoria, edição/cancelamento de evento, e-mail, check-in/revogação, Analytics, carga, administração de contas, filtros e blockchain |

## Jornada demonstrável

[Guia com comandos, contas e roteiro](12-guia-execucao.md); [explicação dos fluxos e falhas](13-como-funciona.md). `scripts/smoke.py` exercita OIDC real, aprovação/PDF, recusa/cancelamento, timeout, idempotência, disputa simultânea e acesso cruzado, sem imprimir tokens.

## Testes e evidências

Validação local de15/11/2026:

| Check | Resultado |
|---|---|
| Maven `clean verify` em todos os módulos | **107 testes aprovados**:65unitários +42integrações,0falhas/erros/skips |
| Common |3unitários +2IT PostgreSQL/Kafka: rollback atômico, broker pausado/recuperado, ID estável e bloqueio/replay |
| Inventory |5unitários +25IT PostgreSQL/Kafka: concorrência, seleção tudo ou nada, relógio após lock, expiração/cancelamento/confirmar, vínculo, idempotência, lotes e envelopes inválidos |
| Orders |48unitários +7IT PostgreSQL/Kafka: estados, dono, idempotência, eventos/timeout e unicidades |
| Delivery |1unitário +2IT PostgreSQL: assinatura, ingresso único mesmo com novo ID de evento, PDF, retomada/lease, acesso401/403/404 |
| IAM |6unitários +4IT Redis: cifra, sessão/TTL, locks e corrida de refresh/logout |
| Gateway |2unitários +2IT PostgreSQL: resultado durável, recusa e pendência |
| Checkstyle / JaCoCo |Aprovados. Linhas: Inventory domínio100%, aplicação96,64%; Orders domínio96,88%, aplicação88,35%; Delivery domínio86,36%, casos de uso85,37% |
| Frontend |ESLint, TypeScript,8testes e build Vite aprovados |
| Dependências frontend |Vite atualizado7.3.8 e Vitest4.1.12; `npm audit --omit=dev` sem vulnerabilidades. Auditoria completa ainda aponta7moderadas em ferramentas de desenvolvimento transitivas |
| Docker Compose |Configuração válida; imagens construídas; serviços da jornada saudáveis; tópicos inicializados com sucesso |
| Smoke real |OIDC/PKCE, claims de usuário/papel, JWT adulterado recusado, reserva/chaves, pagamento, Kafka, PDF, acesso cruzado, recusa/cancelamento, timeout, disputa, refresh/logout |
| PDF visual |Página gerada no teste renderizada e inspecionada: dados legíveis, sem sobreposição e QR presente |
| UI visual |Browser de automação indisponível; sem alegação de QA visual completo desktop/celular. UI coberta por testes de comportamento e build |
| Git/Markdown/JSON |`git diff --check`, links locais e JSON conferidos |

Relatórios reproduzíveis ficam em `backend/*/target/{surefire-reports,failsafe-reports,site/jacoco}`; são gerados, não entram no Git. A CI os retém por14dias. O smoke cria dados fictícios persistentes sem reset. Prazo/expiração nos limites é exercitado com PostgreSQL real nos IT; o smoke não aguarda dez minutos.

Falhas intermediárias investigadas e corrigidas: cache Maven incompleto após retomada; API Docker29 não propagada ao processo Failsafe; readiness ausente no Inventory; cookies Secure de localhost no cliente Python; scope Keycloak `basic` ausente causando access token sem `sub`. A validação mantém assinatura, issuer, audience e proprietário, sem relaxá-los. O realm novo e `scripts/sync-keycloak-scopes.py` cobrem instalação nova e alinhamento não destrutivo do realm existente.

Nenhuma integração foi substituída por mocks para afirmar concorrência ou recuperação do broker. Não foi executado reset de volumes. Workflow remoto, branch protection e revisão humana não foram verificados.

## Limitações e Pacote 4

- Ambiente local acadêmico HTTP/loopback, sem HA, carga medida, TLS ou deploy de produção. Kafka único e publicação serial por banco limitam throughput.
- Catálogo/lotes iniciais simplificados; ausência de gestão por Organizador, alterações/cancelamento de evento e projeções de revogação.
- IAM não implementa gestão de contas/organizadores nem trilha SQL administrativa. Access JWT emitido continua válido até expirar após logout.
- E-mail, WebSocket, check-in e Analytics adiados. QR assinado comprova integridade do conteúdo; não equivale a validar uso único na entrada.
- Chaves HTTP persistem sem coleta; PDFs no PostgreSQL; assinatura privada no volume persistente. Retenção, rotação de chaves e políticas de arquivamento exigem evolução.
- Contratos JSON são versionados; não há schema registry. DLQ/reconciliação de conflitos terminais requer intervenção consciente do grupo.
- Revisão humana, proteção de branches, publicação do código, execução remota de Actions e submissão acadêmica continuam pendentes.

Prioridade Pacote4: revisar e apresentar esta jornada; completar gestão/publicação e cancelamento de eventos com revogação; cadastro/auditoria; e-mail/WebSocket/check-in; Analytics; medir NFRs/carga; consolidar observabilidade, retenção e segurança de publicação. Blockchain permanece opcional e separado da compra.
