# ADR0002 — jornada de compra e recuperação do incremento3

**Implementação:**09/11/2026; **validação final:**15/11/2026. **Status:** decisão implementada na solicitação; revisão humana do grupo pendente.

## Contexto

A arquitetura alvo do ADR0001 exige uma jornada com concorrência e eventos reais; só existia a máquina de estados de Orders. Este incremento precisa ser demonstrável sem criar aplicações vazias ou completar todas as histórias.

## Decisão

Executar IAM, Inventory, Orders, Delivery e gateway em processos separados. Bancos/usuários exclusivos PostgreSQL para serviços persistentes; IAM usa apenas Redis para sessões cifradas. Analytics permanece adiado.

PostgreSQL Inventory decide reserva/prazo após locks. Orders persiste intenções antes de HTTP, usa chave estável para o gateway e só conclui Pago após confirmação do Inventory. Commands/respostas usam outbox/Kafka; snapshot HTTP reconcilia atrasos e reinícios. Envelope e payload v1 são versionados em specs/events.

Relay com lock transacional por banco publica por sequência, conservando IDs. É mais simples que leases por agregado, à custa de throughput e bloqueio mais amplo. Consumidor usa inbox/efeitos no mesmo commit; retry3x/1s, quarentena durável, bloqueio por agregado e outbox DLQ. Offset somente depois do commit; sem promessa de exactly-once.

Delivery persiste ingresso único por item e tarefa com lease. PDF/QR Ed25519 gerado fora da transação, bytes no PostgreSQL. Chave privada runtime em volume, nunca no Git. Frontend usa polling; e-mail/WebSocket/check-in/Analytics e gestão de eventos/contas adiados.

## Alternativas e consequências

Toda interação por HTTP acoplaria emissão ao checkout; Kafka também nas leituras acrescentaria complexidade sem benefício nesta jornada. Redis como autoridade de estoque não preservaria atomicidade PostgreSQL. Mantém-se a combinação do ADR0001.

Catálogo usa seed e um lote inicial por evento, sem editor. Retenção de chaves HTTP é indefinida no incremento; payload divergente retorna409. A criação de pedido é intenção202 e pode terminar Expirado, em vez de erro síncrono em todos os casos. Essas diferenças e histórias parciais estão no checkpoint11.

O Compose é local, com broker único e sem HA. Segurança de publicação, retenção, rotação de chaves, telemetria completa e dimensionamento precisam de evolução antes de produção. Testes reais não equivalem a revisão humana ou benchmarks.
