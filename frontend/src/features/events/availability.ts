export function available(status: string): boolean {
  return ["AVAILABLE", "FREE"].includes(status);
}
