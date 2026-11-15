export function remainingSeconds(expiresAt: string, serverNow: string, elapsedMilliseconds: number): number {
  const remaining = Date.parse(expiresAt) - Date.parse(serverNow) - Math.max(0, elapsedMilliseconds);
  return Number.isFinite(remaining) ? Math.max(0, Math.ceil(remaining / 1000)) : 0;
}
export function formatCountdown(seconds: number): string {
  return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`;
}
export function terminal(status: string): boolean { return ['PAID', 'EXPIRED', 'CANCELLED'].includes(status); }
export function canPay(status: string, paymentStatus: string | null): boolean {
  return status === 'RESERVED' && !['PENDING', 'APPROVED', 'PROCESSING', 'CONFIRMING'].includes(paymentStatus ?? '');
}
export function available(status: string): boolean { return ['AVAILABLE', 'FREE'].includes(status); }
export class OperationKeys {
  private keys = new Map<string, string>();
  for(operation: string): string {
    let key = this.keys.get(operation);
    if (!key) { key = crypto.randomUUID(); this.keys.set(operation, key); }
    return key;
  }
  complete(operation: string): void { this.keys.delete(operation); }
}
