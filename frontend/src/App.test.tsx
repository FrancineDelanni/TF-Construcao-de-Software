import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
const session = {authenticated: true, accessToken: 'test-token', expiresAt: '2099-01-01T00:00:00Z', csrfToken: 'csrf', user: {sub: 'customer', name: 'Cliente Teste', roles: ['Cliente']}};
const seats = [{id: 'a', sector: 'Pista', row: 'A', number: '1', priceCents: 5000, status: 'AVAILABLE'}, {id: 'b', sector: 'Pista', row: 'A', number: '2', priceCents: 5000, status: 'AVAILABLE'}];
const event = {id: 'event', name: 'Encontro de teste', startsAt: '2030-01-01T20:00:00Z', venue: 'Teatro de teste', seats};
const reservation = {reservationId: 'reservation', eventId: 'event', eventName: event.name, status: 'ACTIVE', expiresAt: '2030-01-01T20:10:00Z', serverNow: '2030-01-01T20:00:00Z', totalCents: 10000, currency: 'BRL', items: seats.map(seat => ({...seat, seatId: seat.id}))};
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
function fakeServer(conflict = false) {
  const fetch = vi.fn(async (path: string, options?: RequestInit) => {
    if (path === '/api/iam/session') return Response.json(session);
    if (path === '/api/inventory/events') return Response.json([event]);
    if (path === '/api/inventory/events/event') return Response.json(event);
    if (path.endsWith('/reservations')) return conflict ? Response.json({message: 'Assento ocupado'}, {status: 409}) : Response.json(reservation);
    if (path === '/api/orders' && options?.method === 'POST') return Response.json({...reservation, id: 'order', status: 'RESERVED', paymentStatus: null, history: []});
    throw Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal('fetch', fetch); return fetch;
}
describe('real client journey controls', () => {
  it('requires selection and sends multiple seats atomically before creating an order', async () => {
    const fetch = fakeServer(); const user = userEvent.setup(); render(<App/>);
    await user.click(await screen.findByRole('button', {name: 'Ver assentos ↗'}));
    const reserve = screen.getByRole('button', {name: 'Reservar assentos →'});
    expect(reserve.hasAttribute('disabled')).toBe(true);
    await user.click(screen.getByRole('button', {name: /assento 1, Livre/}));
    await user.click(screen.getByRole('button', {name: /assento 2, Livre/}));
    await user.click(reserve);
    await screen.findByRole('heading', {name: 'Pagamento simulado'});
    const request = fetch.mock.calls.find(([path]) => path.endsWith('/reservations'))!;
    expect(JSON.parse(request[1]!.body as string)).toEqual({seatIds: ['a', 'b']});
    expect((request[1]!.headers as Record<string, string>)['Idempotency-Key']).toBeTruthy();
    expect(screen.getByRole('timer').textContent).toBe('10:00');
  });
  it('explains atomic conflict and returns to selection without inventing an order', async () => {
    const fetch = fakeServer(true); const user = userEvent.setup(); render(<App/>);
    await user.click(await screen.findByRole('button', {name: 'Ver assentos ↗'}));
    await user.click(screen.getByRole('button', {name: /assento 1, Livre/}));
    await user.click(screen.getByRole('button', {name: 'Reservar assentos →'}));
    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Nenhum assento desta tentativa foi bloqueado'));
    expect(fetch.mock.calls.some(([path]) => path === '/api/orders')).toBe(false);
    expect(screen.getByRole('button', {name: 'Reservar assentos →'}).hasAttribute('disabled')).toBe(true);
  });
});
