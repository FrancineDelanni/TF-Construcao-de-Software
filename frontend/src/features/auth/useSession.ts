import { useEffect, useState } from "react";
import { loadSession, onSession } from "./session";
import type { Session } from "./types";
import { message } from "../../shared/formatting";
export function useSession() {
  const [session, setSession] = useState<Session>({ authenticated: false });
  const [booting, setBooting] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    const unsubscribe = onSession(setSession);
    loadSession()
      .catch((e) => setError(message(e)))
      .finally(() => setBooting(false));
    return unsubscribe;
  }, []);
  return { session, booting, error, clearError: () => setError("") };
}
