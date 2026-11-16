export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      body.message ||
        (response.status === 403
          ? "Acesso negado para esta conta."
          : "Não foi possível concluir. Tente novamente."),
    );
  }
  return response.json() as Promise<T>;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  key?: string;
  blob?: boolean;
}
export function request(
  path: string,
  options: RequestOptions,
  accessToken: string,
): Promise<Response> {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
  };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (options.key) headers["Idempotency-Key"] = options.key;
  return fetch(path, {
    method: options.method ?? "GET",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    headers,
    credentials: "same-origin",
    cache: "no-store",
  });
}
