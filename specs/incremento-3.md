# Contratos operacionais — incremento 3

Versões: Java 21; Spring Boot 3.5.7; Maven 3.9.11 (wrapper existente); JUnit 5.13.4; Testcontainers 1.21.3; PostgreSQL 17.6; Kafka 3.9.1 KRaft; Redis 7.4.6; Keycloak 26.4.2; React 19.1.1; TypeScript 5.9.3; Vite 7.3.8; Vitest 4.1.12; Node 22.20.0. Valores locais fictícios, HTTP loopback para demonstração.

## Serviços e rotas
Todos escutam 8080 nos containers. Nginx porta 8080 local encaminha rotas sem reescrever caminho. Keycloak localhost:8081, Kafka UI localhost:8082. APIs em /api/{iam,inventory,orders,delivery}. JWK interno http://keycloak:8080/realms/pulsepass/protocol/openid-connect/certs; issuer http://localhost:8081/realms/pulsepass; audience pulsepass-api.

IAM usa Authorization Code + PKCE mediado no servidor. Sessão opaca HttpOnly SameSite=Lax; refresh cifrado AES-GCM no Redis. Access token retornado por /api/iam/session somente para memória React, nunca localStorage. POST refresh/logout exigem Origin do frontend e/ou CSRF. Papéis Cliente, Organizador, Operador, Admin. Cliente deve acessar apenas recursos próprios. Client confidential pulsepass-web, secret fictício local-web-secret; orders-service client credentials realm role ServiceOrders, secret local-orders-secret.

Inventory: GET /api/inventory/events → array de eventos; GET /api/inventory/events/{id} → evento com seats; POST /api/inventory/events/{id}/reservations {seatIds:[uuid]} + Idempotency-Key; GET /api/inventory/reservations/{id} (owner). Snapshot: {reservationId,eventId,eventName,eventStartsAt,venue,customerId,status,expiresAt,serverNow,orderId,version,currency,totalCents,items:[{reservationItemId,seatId,sector,row,number,priceCents}]}. GET /api/inventory/internal/reservations/{id} e POST /api/inventory/internal/reservations/{id}/bind {orderId,customerId}: role ServiceOrders. Bind retorna snapshot. Catálogo inclui id,name,startsAt,venue,seats:[{id,sector,row,number,priceCents,status}].

Orders: POST /api/orders {reservationId} + Idempotency-Key → pedido; GET /api/orders → array próprios; GET /api/orders/{id}; POST /api/orders/{id}/payments {outcome:"APPROVE"|"DECLINE"|"TIMEOUT"} + Idempotency-Key; POST /api/orders/{id}/cancel {} + Idempotency-Key. Pedido: {id,reservationId,status,expiresAt,serverNow,eventName,totalCents,currency,items,history,paymentStatus}. Enum API CREATED,RESERVED,AWAITING_PAYMENT,PAID,EXPIRED,CANCELLED. Writes retornam operação persistida e polling; sem transação SQL durante HTTP. Gateway POST /payments {attemptId, outcome} e GET /payments/{attemptId}; Header X-Gateway-Key local-gateway-secret; timeout simulado fica PENDING durante 8 segundos e depois APPROVED, dados duráveis em banco próprio.

Delivery: GET /api/delivery/orders/{orderId}/tickets → array próprios {id,orderId,status,seatDescription}; GET /api/delivery/tickets/{id}/pdf → PDF owner. Lista vazia significa emissão ainda pendente. PDF/QR gerado em tarefa durável, UNIQUE(order_item_id), PDF bytea.

## Contrato compartilhado Java (módulo common)
Package br.pucrs.pulsepass.common. EventMessage record: UUID eventId, String eventType, int schemaVersion, String aggregateType, UUID aggregateId, long aggregateVersion, Instant occurredAt, String producer, UUID correlationId, UUID causationId, JsonNode payload.
Outbox.add(String topic,String eventType,String aggregateType,UUID aggregateId,long version,Object payload) retorna UUID. Deve ser chamado dentro da transação do domínio. Inbox.claim(String consumer,UUID eventId) boolean dentro da transação consumidor. Json helper utiliza ObjectMapper Spring.
ApiException(int status,String message), global handler; erros {message:...} sem stack ou credenciais. SecurityConfig valida JWT e converte realm_access.roles → ROLE_; @EnableMethodSecurity. Cada app @SpringBootApplication(scanBasePackages={"br.pucrs.pulsepass.common","br.pucrs.pulsepass.<serviço>"}). @EnableScheduling.

Common migração classpath:db/common/V1__messaging.sql; serviços db/migration/V100__<serviço>.sql. spring.flyway.locations=classpath:db/common,classpath:db/migration. Apps com application.yml próprio. Common não possui application.yml.

## Kafka
Tópicos docs/08: pulsepass.inventory.commands.v1, pulsepass.inventory.reservations.v1, pulsepass.orders.events.v1, pulsepass.delivery.events.v1; DLQ por grupo (pulsepass.dlq.inventory-commands-v1, pulsepass.dlq.orders-reservations-v1, pulsepass.dlq.delivery-ticket-issuer-v1). JSON String Kafka serializers/deserializers, grupos explícitos, auto commit false, ack RECORD após retorno de método @Transactional em outro bean. Consumer valida schemaVersion=1. Retry bloqueante curto preserva ordem; falha técnica exaurida grava quarantine bloqueando agregado + outbox DLQ na mesma transação. Reprocessamento administrativo preserva eventId.

Commands payload {reservationId,orderId,customerId,paymentAttemptId} para confirm e {reservationId,orderId,customerId} cancel. Resultados reservation.confirmed/.expired/.cancelled e .confirmation-rejected/.cancellation-rejected contêm {reservationId,orderId,customerId,status,version,expiresAt,commandId}; status ACTIVE/CONFIRMED/EXPIRED/CANCELLED é autoritativo. Criada/vinculada usa snapshot. Orders aplica resultado terminal autoritativo e ignora projeções não terminais antigas; reconcilia snapshot via HTTP caso evento chegue cedo ou atrase.

order.paid payload {orderId,reservationId,customerId,eventId,eventName,eventStartsAt,venue,currency,totalCents,items:[{orderItemId,seatId,sector,row,number,priceCents}]}. Delivery não depende de outros eventos de pedido para emissão. Identificadores de item estáveis.

## Testes
Maven verify executa unidade e failsafe *IT com Testcontainers (falha explícita se Docker indisponível; -DskipITs somente para checks sem infraestrutura). JaCoCo domínio ≥70%, sem excluir regras relevantes. Cada serviço cria seus próprios testes. Não usar mocks como evidência de concorrência ou Kafka.
