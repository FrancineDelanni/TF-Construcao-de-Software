# PulsePass

Plataforma distribuída de bilhetagem, reserva e acesso para megaeventos, projetada para suportar tráfego massivo em períodos de alta demanda (*flash crowd traffic*), garantindo tolerância zero à sobre-venda (*zero overselling*), expiração estrita de reservas temporárias e emissão segura de ingressos.

## 📋 Sobre o Projeto

O projeto foi desenvolvido para a disciplina de **Construção de Software – 2026/2**, da Escola Politécnica da Pontifícia Universidade Católica do Rio Grande do Sul (PUCRS).

A solução aborda os desafios de sistemas distribuídos submetidos a altos níveis de concorrência, evitando a dependência exclusiva de operações HTTP síncronas e utilizando comunicação assíncrona, mensageria e mecanismos de resiliência.

## 🎯 Objetivos

O sistema deve:

- Suportar picos de tráfego durante a abertura das vendas;
- Garantir **zero overselling** na reserva de ingressos e assentos;
- Gerenciar reservas temporárias com expiração estrita de **10 minutos**;
- Garantir a emissão segura de ingressos;
- Utilizar arquitetura baseada em **microsserviços**;
- Utilizar comunicação assíncrona por meio do **Apache Kafka**;
- Garantir idempotência no processamento de mensagens;
- Tratar o problema de **Dual-Write**;
- Disponibilizar autenticação e autorização seguras;
- Possibilitar observabilidade e análise operacional em tempo real.

## 🏗️ Módulos

### 🔐 Identidade e Governança

Módulo responsável por identidade, autenticação e autorização (IAM).

Principais funcionalidades:

- Autenticação stateless utilizando **JWT**;
- Refresh tokens armazenados em estrutura chave-valor;
- Autorização baseada em papéis (**RBAC**);
- Gestão de credenciais;
- Gestão cadastral de organizadores;
- Auditoria.

Papéis previstos:

- Cliente
- Organizador
- Operador
- Administrador

### 🎟️ Inventário e Concorrência

Módulo responsável pelo catálogo de eventos, assentos e controle de concorrência.

Principais funcionalidades:

- Criação de lotes;
- Criação e gerenciamento de mapas de assentos;
- Catálogo de eventos com alta capacidade de leitura;
- Alocação atômica de assentos;
- Devolução atômica de assentos em cancelamentos ou expirações;
- Controle de locks concorrentes;
- Publicação de eventos relacionados à expiração de reservas.

### 💳 Pedidos, Checkout e Transações

Módulo responsável pelo ciclo de vida dos pedidos e pelo processamento resiliente das transações.

O pedido possui uma máquina de estados:

```text
Criado → Reservado → Aguardando → Pago
                         ↓
                      Expirado
