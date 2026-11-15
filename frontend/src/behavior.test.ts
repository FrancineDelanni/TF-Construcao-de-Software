import { describe, expect, it } from 'vitest';
import { canPay, formatCountdown, OperationKeys, remainingSeconds, terminal } from './behavior';
describe('server-authoritative countdown', () => {
  it('starts from the server clock and deducts elapsed time, never the device clock', () => {
    expect(remainingSeconds('2026-11-09T23:10:00Z', '2026-11-09T23:00:00Z', 1250)).toBe(599);
    expect(formatCountdown(599)).toBe('09:59');
  });
  it('zero countdown does not turn pending payment into an expired order', () => {
    expect(remainingSeconds('2026-11-09T23:10:00Z', '2026-11-09T23:00:00Z', 601000)).toBe(0);
    expect(terminal('AWAITING_PAYMENT')).toBe(false);
    expect(canPay('AWAITING_PAYMENT', 'PENDING')).toBe(false);
  });
  it('only offers new payment after a definitive refusal and reserved state', () => {
    expect(canPay('RESERVED', 'DECLINED')).toBe(true);
    expect(canPay('RESERVED', 'PENDING')).toBe(false);
    expect(canPay('PAID', 'APPROVED')).toBe(false);
  });
});
describe('idempotent retries', () => {
  it('reuses a key across uncertain replies and isolates independent operations', () => {
    const keys = new OperationKeys();
    const key = keys.for('payment:42');
    expect(keys.for('payment:42')).toBe(key);
    expect(keys.for('cancel:42')).not.toBe(key);
    keys.complete('payment:42');
    expect(keys.for('payment:42')).not.toBe(key);
  });
});
