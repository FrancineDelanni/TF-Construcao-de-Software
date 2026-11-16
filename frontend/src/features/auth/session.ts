import type { Session } from "./types";
import {
  ApiError,
  decode,
  request,
  type RequestOptions,
} from "../../shared/http/client";
let session: Session = { authenticated: false };
let refreshInFlight: Promise<Session> | undefined;
const listeners = new Set<(value: Session) => void>();
function update(value: Session): Session {
  session = value;
  listeners.forEach((listener) => listener(value));
  return value;
}
export function onSession(listener: (value: Session) => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
export async function loadSession(): Promise<Session> {
  const current = update(
    await decode<Session>(
      await fetch("/api/iam/session", {
        credentials: "same-origin",
        cache: "no-store",
      }),
    ),
  );
  if (
    current.authenticated &&
    Date.parse(current.expiresAt) - Date.now() < 30000
  )
    return refreshSession();
  return current;
}
export async function refreshSession(): Promise<Session> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      if (!session.authenticated)
        throw new ApiError(401, "Entre para continuar.");
      try {
        const response = await fetch("/api/iam/refresh", {
          method: "POST",
          credentials: "same-origin",
          headers: { "X-CSRF-Token": session.csrfToken },
        });
        if (response.status === 409) {
          await new Promise((resolve) => setTimeout(resolve, 500));
          return update(
            await decode<Session>(
              await fetch("/api/iam/session", {
                credentials: "same-origin",
                cache: "no-store",
              }),
            ),
          );
        }
        return update(await decode<Session>(response));
      } catch (error) {
        if (error instanceof ApiError && error.status === 401)
          update({ authenticated: false });
        throw error;
      }
    })().finally(() => {
      refreshInFlight = undefined;
    });
  }
  return refreshInFlight;
}
export async function logout(): Promise<void> {
  if (session.authenticated)
    await decode(
      await fetch("/api/iam/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: { "X-CSRF-Token": session.csrfToken },
      }),
    );
  update({ authenticated: false });
}
export async function api<T>(
  path: string,
  options: RequestOptions = {},
  retry = true,
): Promise<T> {
  if (!session.authenticated) throw new ApiError(401, "Entre para continuar.");
  if (Date.parse(session.expiresAt) - Date.now() < 15000)
    await refreshSession();
  if (!session.authenticated)
    throw new ApiError(401, "Sua sessão terminou. Entre novamente.");
  const response = await request(path, options, session.accessToken);
  if (response.status === 401 && retry) {
    await refreshSession();
    return api<T>(path, options, false);
  }
  if (response.status === 401) update({ authenticated: false });
  if (options.blob && response.ok) return (await response.blob()) as T;
  return decode<T>(response);
}
