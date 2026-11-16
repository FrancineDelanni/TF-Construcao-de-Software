export type Role = "Cliente" | "Organizador" | "Operador" | "Admin" | string;
export type Session =
  | { authenticated: false }
  | {
      authenticated: true;
      accessToken: string;
      expiresAt: string;
      csrfToken: string;
      user: { sub: string; name: string; roles: Role[] };
    };
