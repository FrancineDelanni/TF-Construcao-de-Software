#!/usr/bin/env python3
"""Smoke real via Keycloak Authorization Code + PKCE/IAM. Não imprime tokens/senhas.
Requer Python 3 e Compose ativo; cria compras fictícias persistentes, sem reset.
"""
import concurrent.futures
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from html.parser import HTMLParser

BASE = os.environ.get('PULSEPASS_URL', 'http://localhost:8080').rstrip('/')

class LoginForm(HTMLParser):
    def __init__(self):
        super().__init__(); self.action = None; self.values = {}; self.inside = False
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'form' and a.get('id') == 'kc-form-login':
            self.action = a.get('action'); self.inside = True
        if self.inside and tag == 'input' and a.get('name') and a.get('type') == 'hidden':
            self.values[a['name']] = a.get('value', '')
    def handle_endtag(self, tag):
        if tag == 'form': self.inside = False

class LoopbackCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    # Browsers treat localhost as a secure context even over HTTP; urllib does not.
    # Match that behavior only for the named local demo host, never other origins.
    def return_ok_secure(self, cookie, request):
        if (urllib.parse.urlsplit(request.full_url).hostname == 'localhost'
                and cookie.domain in ('localhost', 'localhost.local')):
            return True
        return super().return_ok_secure(cookie, request)

class Client:
    def __init__(self, username):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar(policy=LoopbackCookiePolicy())))
        self.token = None; self.csrf = None
        page = self.http.open(BASE + '/api/iam/login').read().decode()
        form = LoginForm(); form.feed(page)
        assert form.action, 'Formulário Keycloak não encontrado'
        form.values.update(username=username, password='PulsePass123!', credentialId='')
        request = urllib.request.Request(form.action, data=urllib.parse.urlencode(form.values).encode())
        self.http.open(request).read()
        session = self.call('/api/iam/session')
        assert session.get('authenticated'), 'Sessão OIDC não estabelecida'
        assert session['user'].get('sub') and 'Cliente' in session['user']['roles'], 'Claims de identidade/papel ausentes'
        self.token = session['accessToken']; self.csrf = session['csrfToken']
    def call(self, path, body=None, key=None, expected=200, binary=False):
        headers = {'Origin': BASE}
        if self.token: headers['Authorization'] = 'Bearer ' + self.token
        if key: headers['Idempotency-Key'] = key
        if self.csrf: headers['X-CSRF-Token'] = self.csrf
        data = None
        if body is not None:
            data = json.dumps(body).encode(); headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(BASE + path, data=data, headers=headers)
        try:
            with self.http.open(request, timeout=20) as response:
                code = response.status; result = response.read()
        except urllib.error.HTTPError as error:
            code = error.code; result = error.read()
        assert code == expected, f'{path}: esperado HTTP {expected}; recebido {code}: {result[:250]!r}'
        if binary: return result
        return json.loads(result) if result else None

def wait_for(fetch, predicate, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = fetch()
        if predicate(result): return result
        time.sleep(.4)
    raise AssertionError('Prazo do smoke excedido')

def checkout(client, event_id, seat, outcome):
    key = str(uuid.uuid4())
    path = f'/api/inventory/events/{event_id}/reservations'
    reservation = client.call(path, {'seatIds': [seat]}, key, 201)
    repeated = client.call(path, {'seatIds': [seat]}, key, 201)
    assert repeated['reservationId'] == reservation['reservationId']
    assert repeated['expiresAt'] == reservation['expiresAt']
    client.call(path, {'seatIds': [seat, str(uuid.uuid4())]}, key, 409)
    order = client.call('/api/orders', {'reservationId': reservation['reservationId']}, str(uuid.uuid4()), 202)
    order = wait_for(lambda: client.call('/api/orders/' + order['id']), lambda o: o['status'] == 'RESERVED')
    key = str(uuid.uuid4())
    client.call(f"/api/orders/{order['id']}/payments", {'outcome': outcome}, key, 202)
    client.call(f"/api/orders/{order['id']}/payments", {'outcome': outcome}, key, 202)
    client.call(f"/api/orders/{order['id']}/payments", {'outcome': 'DECLINE' if outcome != 'DECLINE' else 'APPROVE'}, key, 409)
    return order

def main():
    first, second = Client('cliente1'), Client('cliente2')
    valid_token = first.token
    parts = valid_token.split('.')
    parts[2] = ('A' if parts[2][0] != 'A' else 'B') + parts[2][1:]
    first.token = '.'.join(parts)
    first.call('/api/inventory/events', expected=401)
    first.token = valid_token
    events = first.call('/api/inventory/events'); assert events
    event_id = events[0]['id']
    seats = [s['id'] for s in first.call('/api/inventory/events/' + event_id)['seats'] if s['status'] == 'AVAILABLE']
    assert len(seats) >= 5, 'Smoke precisa de cinco assentos livres; não faz reset automático'
    approved = checkout(first, event_id, seats[0], 'APPROVE')
    approved = wait_for(lambda: first.call('/api/orders/' + approved['id']), lambda o: o['status'] == 'PAID')
    second.call('/api/orders/' + approved['id'], expected=404)
    tickets = wait_for(lambda: first.call(f"/api/delivery/orders/{approved['id']}/tickets"), lambda rows: bool(rows) and all(t['status'] == 'ISSUED' for t in rows))
    pdf = first.call(f"/api/delivery/tickets/{tickets[0]['id']}/pdf", binary=True)
    assert pdf.startswith(b'%PDF-')
    second.call(f"/api/delivery/tickets/{tickets[0]['id']}/pdf", expected=404)
    print('PASS: login OIDC, reserva idempotente, pedido, aprovação, Kafka, PDF e isolamento entre clientes')
    declined = checkout(first, event_id, seats[1], 'DECLINE')
    wait_for(lambda: first.call('/api/orders/' + declined['id']), lambda o: o['status'] == 'RESERVED' and o.get('paymentStatus') == 'DECLINED')
    first.call(f"/api/orders/{declined['id']}/cancel", {}, str(uuid.uuid4()), 202)
    wait_for(lambda: first.call('/api/orders/' + declined['id']), lambda o: o['status'] == 'CANCELLED')
    print('PASS: recusa definitiva, prazo preservado e cancelamento autoritativo')
    timeout = checkout(first, event_id, seats[2], 'TIMEOUT')
    wait_for(lambda: first.call('/api/orders/' + timeout['id']), lambda o: o['status'] == 'PAID')
    print('PASS: timeout e reconciliação da mesma tentativa')
    path = f'/api/inventory/events/{event_id}/reservations'
    def compete(client):
        try:
            return client.call(path, {'seatIds': [seats[3]]}, str(uuid.uuid4()), 201)
        except AssertionError as error:
            assert 'recebido 409' in str(error), str(error)
            return None
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(compete, [first, second]))
    assert sum(r is not None for r in results) == 1
    print('PASS: dois clientes disputando o mesmo assento, um único vencedor')
    before = first.call('/api/iam/session')
    refreshed = first.call('/api/iam/refresh', {})
    assert refreshed.get('authenticated') and refreshed['user']['sub'] == before['user']['sub']
    first.csrf = refreshed['csrfToken']
    first.call('/api/iam/logout', {})
    assert not first.call('/api/iam/session').get('authenticated')
    print('PASS: renovação mediada por IAM e logout')
    print('Smoke concluído. Dados fictícios preservados; expiração de 10 min coberta nos testes de integração.')

if __name__ == '__main__': main()
