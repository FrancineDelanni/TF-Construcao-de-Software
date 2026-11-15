# PulsePass — guia de execução do Pacote 3

Data: 09/11/2026. Valores e usuários deste guia são exclusivamente fictícios e locais.

## Pré-requisitos e configuração

Docker Engine/Desktop ativo com Docker Compose v2+; internet para baixar imagens/dependências. Reserve aproximadamente 8 GB de memória para Docker e portas locais livres. Java e Node não são necessários para subir a aplicação. Python 3 é opcional para os scripts de smoke e replay.

Na raiz do repositório:

```sh
# Executar apenas se ainda não existir .env; não substitui configuração pessoal.
test -f .env || cp .env.example .env
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 300
docker compose ps
```

O primeiro build demora mais. Flyway cria os dados de cada serviço e aplica o seed uma única vez por banco. Subir novamente preserva reservas, pedidos, ingressos, chaves de assinatura, sessões e mensagens. A inicialização PostgreSQL cria bancos/usuários exclusivos e revoga acesso público entre bancos. Não altere senhas do `.env` depois da primeira inicialização esperando migrar credenciais existentes automaticamente.

O realm importado usa os segredos fictícios de `.env.example`. O client web precisa dos scopes `basic`, `roles`, `profile` e `email`; `basic` fornece o `sub` usado na autorização por proprietário. Em volume que já tinha um realm anterior, alinhe os scopes sem apagar dados:

```sh
python3 scripts/sync-keycloak-scopes.py
```

O script usa as credenciais administrativas fictícias padrão; para valores personalizados, forneça `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` como variáveis de ambiente. Se decidir alterá-los, alinhe também os clientes do realm; o import não sobrescreve realm já existente. O usuário administrativo do Keycloak é independente do usuário `admin` do realm PulsePass.

## URLs e portas

| Componente | URL/porta local | Uso |
|---|---|---|
| Aplicação React/Nginx | http://localhost:8080 | Entrada da jornada |
| Keycloak | http://localhost:8081 | Login hospedado; console `/admin` |
| Kafka UI | http://localhost:8082 | Tópicos, grupos, mensagens e DLQs |
| Inventory | http://localhost:8083/actuator/health/readiness | API `/api/inventory` |
| Orders | http://localhost:8084/actuator/health/readiness | API `/api/orders` |
| Delivery | http://localhost:8085/actuator/health/readiness | API `/api/delivery` |
| IAM | http://localhost:8086/actuator/health/readiness | API `/api/iam` |
| PostgreSQL | localhost:5432 | Administração/desenvolvimento local |
| Redis | localhost:6379 | Sessões IAM cifradas |
| Kafka externo | localhost:29092 | Clientes de desenvolvimento |
| Gateway simulado | somente rede Compose, gateway:8080 | Chamadas autenticadas do Orders |

Portas são vinculadas a `127.0.0.1`. Abra a aplicação usando **localhost**, pois issuer, audience e callbacks OIDC são validados. Não misture `127.0.0.1` e `localhost` no navegador. O Compose acadêmico usa HTTP em loopback; uma publicação exigiria TLS e configuração de segredos própria.

## Usuários de demonstração

Todos usam a senha fictícia `PulsePass123!`.

| Login | Papel | O que demonstrar |
|---|---|---|
| `cliente1` | Cliente | Jornada de compra |
| `cliente2` | Cliente | Outra sessão, conflito e isolamento |
| `organizador` | Organizador | Identidade/papel; editor de eventos adiado |
| `operador` | Operador | Identidade/papel; check-in adiado |
| `admin` | Admin | Identidade/papel; gestão da plataforma adiada |

Console administrativo Keycloak: usuário `admin`, senha `local-keycloak-admin`, conforme `.env.example`. Não é a senha do Admin do realm.

## Roteiro de apresentação

1. Abra `localhost:8080`, clique Entrar; o formulário de senha pertence ao Keycloak.
2. Entre como `cliente1`. Escolha um dos eventos publicados pelo seed, um setor e vários assentos livres. Reserve; mostre preço congelado e contador do servidor.
3. No checkout escolha **aprovar**. Acompanhe Reservado → Aguardando → Pago e emissão pendente → PDF. Baixe e abra o ingresso: evento, assento, horário e QR assinado.
4. Em outra compra escolha **recusar**. Aguarde voltar a Reservado; o prazo não reinicia. Tente aprovar novamente ou cancele.
5. Em outra compra escolha **timeout**. A tentativa permanece pendente por cerca de oito segundos; a reconciliação consulta a mesma tentativa e conclui sem criar outra cobrança fictícia.
6. Para expiração real, reserve e aguarde os dez minutos. O contador zero consulta o servidor. O worker libera ocupação e o pedido converge para Expirado; atraso Kafka pode postergar a exibição, nunca permitir confirmação fora do prazo.
7. Para disputa, use dois navegadores/perfis (ou janela anônima) como `cliente1`/`cliente2`. Selecione o mesmo assento antes de reservar. Um vence e o outro recebe conflito sem reservar parte da seleção.
8. Abra Kafka UI e mostre os tópicos `pulsepass.inventory.commands.v1`, `pulsepass.inventory.reservations.v1`, `pulsepass.orders.events.v1` e `pulsepass.delivery.events.v1`. Não confunda `eventId` da mensagem com `payload.eventId` do show.

Smoke automatizado, com login OIDC real e sem imprimir credenciais/tokens:

```sh
python3 scripts/smoke.py
```

Esse roteiro cria dados fictícios persistentes e exige pelo menos cinco assentos livres no primeiro evento. Não limpa bancos. Testa aprovação/PDF, recusa/cancelamento, timeout, idempotência, concorrência e acesso entre usuários. Expiração nos limites do prazo é testada automaticamente com PostgreSQL nos testes de integração, sem esperar dez minutos.

## Falhas, logs e diagnóstico

```sh
docker compose logs --tail=100 inventory orders delivery iam gateway
docker compose logs --tail=100 postgres kafka keycloak
docker compose logs -f orders inventory delivery
```

- **Cannot connect to Docker**: iniciar Docker Desktop/Engine antes do Compose/Testcontainers.
- **Port already allocated**: conferir processo/container existente; não parar serviços pessoais automaticamente. Ajustar portas requer alinhar callbacks/issuer e URLs.
- **401/invalid issuer/audience**: entrar por localhost, conferir realm `pulsepass`, mapeador `pulsepass-api`, relógio local e configurações JWT. Não desativar validação para contornar erro.
- **Login indisponível após perda Redis**: refazer login; inventário e pedidos permanecem no PostgreSQL.
- **Aguardando/emissão pendente**: conferir saúde do Kafka, backlog de outbox e logs. As tarefas retomam após recuperação. Não repetir pagamento com nova chave enquanto resultado é ambíguo.
- **Flyway checksum mismatch**: uma migração aplicada foi modificada. Preservar dados e corrigir com nova migração; não apagar volumes como correção automática.
- **Docker 29 + Testcontainers retorna HTTP 400**: o projeto fixa API Docker `1.44` em `backend/.mvn/jvm.config`; em execução local fora do Wrapper, usar `-Dapi.version=1.44`. No macOS, se necessário definir `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`.

Para demonstrar recuperação do broker local (pode interromper temporariamente todas as emissões):

```sh
docker compose stop kafka
# Fazer uma compra/acompanhar pendências; o prazo do Inventory continua valendo.
docker compose start kafka
```

A confirmação pode expirar se o comando só chegar após o prazo; isso é comportamento esperado. Há teste automatizado de indisponibilidade/recuperação da outbox com Kafka real.

## Inspeção e replay de DLQ

Consumidores tentam uma vez e repetem três vezes com intervalo de 1 s. Após falha técnica persistente, gravam `consumer_quarantine` e intenção DLQ na mesma transação; o offset só avança depois. Eventos seguintes do mesmo agregado ficam bloqueados/retidos para preservar ordem. Mensagens não relacionadas podem prosseguir.

Na Kafka UI, inspecione `pulsepass.dlq.inventory-commands-v1`, `pulsepass.dlq.orders-reservations-v1` e `pulsepass.dlq.delivery-ticket-issuer-v1`. Consulte também a pendência local, por exemplo:

```sh
docker compose exec postgres psql -U postgres -d delivery_db -c \
  "SELECT consumer,event_id,aggregate_id,topic,offset_id,created_at,resolved_at FROM consumer_quarantine ORDER BY created_at,offset_id;"
```

Corrija a causa, registre o motivo da intervenção e reenvie primeiro a mensagem mais antiga de cada agregado, preservando IDs:

```sh
python3 scripts/replay.py delivery UUID-DA-MENSAGEM
# Se o conteúdo exige correção consciente de contrato, preservar eventId/aggregateId:
python3 scripts/replay.py delivery UUID-DA-MENSAGEM --payload-file mensagem-corrigida.json
```

O consumidor resolve a quarentena no commit de sucesso, junto com inbox/efeitos. Depois reenvie as mensagens adiadas em ordem. Não apague inbox para forçar efeito, não use replay para inventar pagamento e não remova bloqueio de um conflito terminal em `orders_quarantine` sem reconciliação manual com Inventory. Registro de intervenção e aprovação operacional são responsabilidades do grupo; nenhum replay é disparado automaticamente pela UI.

## Testes e desenvolvimento com Java/Node

JDK 21 e Node 22.20.0; Docker ativo para Testcontainers:

```sh
cd backend
./mvnw -B -ntp verify
cd ../frontend
npm ci
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

`verify` executa Checkstyle, JUnit, Failsafe e JaCoCo; falha se Docker não estiver disponível. `./mvnw -DskipITs verify` é somente unidade/build e não comprova integração.

Para executar uma API no host, mantenha infraestrutura/serviços de apoio Compose e use banco e porta próprios. Exemplo concreto Inventory, após parar somente seu container para liberar a porta:

```sh
docker compose stop inventory
cd backend
DB_URL=jdbc:postgresql://localhost:5432/inventory_db \
DB_USER=inventory DB_PASSWORD=local-inventory \
KAFKA_BOOTSTRAP_SERVERS=localhost:29092 SERVER_PORT=8083 \
./mvnw -pl inventory -am package -DskipTests
DB_URL=jdbc:postgresql://localhost:5432/inventory_db \
DB_USER=inventory DB_PASSWORD=local-inventory \
KAFKA_BOOTSTRAP_SERVERS=localhost:29092 SERVER_PORT=8083 \
java -jar inventory/target/inventory-0.1.0-SNAPSHOT.jar
```

Nginx do Compose aponta ao nome do container, portanto esse exemplo serve para desenvolvimento direto em `localhost:8083`, não para substituir transparentemente o backend da SPA Compose. Para frontend de desenvolvimento com APIs Compose já ativas, alinhe o callback IAM à origem do Vite:

```sh
# Na raiz:
APP_PUBLIC_URL=http://localhost:5173 docker compose up -d --no-deps iam
cd frontend
npm ci
npm run dev
```

Abra `http://localhost:5173`. O proxy Vite encaminha `/api` para o Nginx em `localhost:8080`, que chega às APIs. O callback 5173 já está permitido no realm. Ao retornar à apresentação, restaure IAM com `APP_PUBLIC_URL=http://localhost:8080 docker compose up -d --no-deps iam`. O caminho de apresentação validado é o Compose completo em `localhost:8080`.

## Parada e reset

Parada preservando os dados:

```sh
docker compose down
```

Reset **destrutivo**, somente quando o grupo decidir descartar todos os dados locais:

```sh
# APAGA reservas, pedidos, ingressos, sessões, realm, mensagens e chave privada local.
docker compose down --volumes
```

O reset destrutivo não foi executado durante a implementação. Não apaga imagens nem volumes de outros projetos. Perder a chave privada antiga impede manter a identidade de assinatura de ingressos antigos; preserve o volume para continuidade.
