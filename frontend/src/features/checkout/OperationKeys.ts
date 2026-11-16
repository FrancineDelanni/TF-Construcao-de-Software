export class OperationKeys {
  private keys = new Map<string, string>();
  for(operation: string): string {
    let key = this.keys.get(operation);
    if (!key) {
      key = crypto.randomUUID();
      this.keys.set(operation, key);
    }
    return key;
  }
  complete(operation: string): void {
    this.keys.delete(operation);
  }
}
