#!/usr/bin/env python3
"""Reenvia uma mensagem em quarentena mantendo IDs. Não apaga inbox nem altera domínio."""
import argparse
import json
import subprocess
import uuid

GROUPS = {'inventory': 'inventory-commands-v1', 'orders': 'orders-reservations-v1', 'delivery': 'delivery-ticket-issuer-v1'}
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('service', choices=GROUPS)
p.add_argument('event_id', type=uuid.UUID)
p.add_argument('--payload-file', help='JSON corrigido após análise; IDs originais devem ser mantidos')
a = p.parse_args()
query = ("SELECT json_build_object('topic',topic,'aggregateId',aggregate_id,'payload',payload)::text "
         f"FROM consumer_quarantine WHERE consumer='{GROUPS[a.service]}' AND event_id='{a.event_id}' AND resolved_at IS NULL")
raw = subprocess.check_output(['docker','compose','exec','-T','postgres','psql','-U','postgres','-d',a.service+'_db','-At','-c',query], text=True).strip()
if not raw: raise SystemExit('Mensagem pendente não encontrada')
record = json.loads(raw)
payload = json.loads(open(a.payload_file).read() if a.payload_file else record['payload'])
if payload.get('eventId') != str(a.event_id) or payload.get('aggregateId') != record['aggregateId']:
    raise SystemExit('IDs originais devem ser preservados; payload inválido exige correção manual com rastreabilidade')
line = record['aggregateId'] + '\t' + json.dumps(payload, separators=(',',':')) + '\n'
subprocess.run(['docker','compose','exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh',
    '--bootstrap-server','kafka:9092','--topic',record['topic'],'--property','parse.key=true','--property','key.separator=\t'], input=line, text=True, check=True)
print('Mensagem reenviada; confira resolved_at/inbox após o commit do consumidor. Reprocesse os próximos registros na ordem original.')
