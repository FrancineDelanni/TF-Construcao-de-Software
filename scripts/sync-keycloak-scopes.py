#!/usr/bin/env python3
"""Alinha scopes dos clientes fictícios em realm local já importado, sem reset de dados."""
import json
import os
import urllib.parse
import urllib.request

base = os.environ.get('KEYCLOAK_URL', 'http://localhost:8081').rstrip('/')
realm = 'pulsepass'
form = urllib.parse.urlencode({
    'client_id': 'admin-cli', 'grant_type': 'password',
    'username': os.environ.get('KEYCLOAK_ADMIN', 'admin'),
    'password': os.environ.get('KEYCLOAK_ADMIN_PASSWORD', 'local-keycloak-admin'),
}).encode()
with urllib.request.urlopen(urllib.request.Request(base + '/realms/master/protocol/openid-connect/token', data=form), timeout=15) as response:
    token = json.load(response)['access_token']

def call(path, method='GET'):
    request = urllib.request.Request(base + '/admin/realms/' + realm + path,
        headers={'Authorization': 'Bearer ' + token}, method=method)
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.load(response) if method == 'GET' else None

scopes = {scope['name']: scope['id'] for scope in call('/client-scopes')}
for client_id, required in {
    'pulsepass-web': ['basic', 'roles', 'profile', 'email'],
    'orders-service': ['basic', 'roles', 'service_account'],
}.items():
    clients = call('/clients?clientId=' + urllib.parse.quote(client_id))
    if len(clients) != 1:
        raise SystemExit('Cliente de demonstração ausente: ' + client_id)
    path = '/clients/' + clients[0]['id'] + '/default-client-scopes'
    attached = {scope['name'] for scope in call(path)}
    for name in required:
        if name not in scopes:
            raise SystemExit('Scope padrão Keycloak ausente: ' + name)
        if name not in attached:
            call(path + '/' + scopes[name], 'PUT')
            print(client_id + ': scope adicionado ' + name)
    print(client_id + ': scopes conferidos; dados preservados')
