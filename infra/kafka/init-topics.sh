#!/usr/bin/env bash
set -euo pipefail
for topic in pulsepass.inventory.commands.v1 pulsepass.inventory.reservations.v1 pulsepass.orders.events.v1 pulsepass.delivery.events.v1; do
 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1 --config retention.ms=604800000
done
for group in inventory-commands-v1 orders-reservations-v1 delivery-ticket-issuer-v1; do
 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic "pulsepass.dlq.$group" --partitions 3 --replication-factor 1 --config retention.ms=1209600000
done
