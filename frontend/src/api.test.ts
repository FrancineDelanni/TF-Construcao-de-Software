import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, loadSession, logout } from './api';
const authenticated = {authenticated: true, accessToken: 'only-in-memory', expiresAt: '2099-01-01T00:00:00Z', csrfToken: 'csrf', user: {sub: '1', name: 'Cliente', roles: ['Cliente']}};
afterEach(() => vi.unstubAllGlobals());
describe('session and API', () => {
  it('refreshes once after 401 and retries with same body and idempotency key', async () => {
    const stored = {setItem: vi.fn()}; vi.stubGlobal('localStorage', stored); vi.stubGlobal('sessionStorage', stored);
    const fetch = vi.fn().mockResolvedValueOnce(Response.json(authenticated))
      .mockResolvedValueOnce(Response.json({message: 'expired'}, {status: 401}))
      .mockResolvedValueOnce(Response.json({...authenticated, accessToken: 'renewed'}))
      .mockResolvedValueOnce(Response.json({id: 'order'}));
    vi.stubGlobal('fetch', fetch);
    await loadSession();
    expect(await api('/api/orders', {method: 'POST', body: {reservationId: 'r'}, key: 'stable-key'})).toEqual({id: 'order'});
    const initial = fetch.mock.calls[1][1]; const retried = fetch.mock.calls[3][1];
    expect(retried.body).toBe(initial.body);
    expect(retried.headers['Idempotency-Key']).toBe('stable-key');
    expect(retried.headers.Authorization).toBe('Bearer renewed');
    expect(fetch.mock.calls[2][1].headers['X-CSRF-Token']).toBe('csrf');
    expect(stored.setItem).not.toHaveBeenCalled();
  });
  it('clears in-memory session after logout', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(Response.json(authenticated)).mockResolvedValueOnce(Response.json({authenticated: false})));
    await loadSession(); await logout();
    await expect(api('/api/orders')).rejects.toMatchObject({status: 401});
  });
});
