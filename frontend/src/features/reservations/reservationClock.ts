export function remainingSeconds(
  expiresAt: string,
  serverNow: string,
  elapsedMilliseconds: number,
): number {
  const remaining =
    Date.parse(expiresAt) -
    Date.parse(serverNow) -
    Math.max(0, elapsedMilliseconds);
  return Number.isFinite(remaining)
    ? Math.max(0, Math.ceil(remaining / 1000))
    : 0;
}
export function formatCountdown(seconds: number): string {
  return `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
}
