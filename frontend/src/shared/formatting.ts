export const money = (cents: number, currency = "BRL") =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency }).format(
    cents / 100,
  );
export const date = (value: string) =>
  new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
export const message = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "Conexão indisponível. Tente novamente.";
