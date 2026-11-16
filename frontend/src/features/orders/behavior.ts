export function terminal(status: string): boolean {
  return ["PAID", "EXPIRED", "CANCELLED"].includes(status);
}
export function canPay(status: string, paymentStatus: string | null): boolean {
  return (
    status === "RESERVED" &&
    !["PENDING", "APPROVED", "PROCESSING", "CONFIRMING"].includes(
      paymentStatus ?? "",
    )
  );
}
