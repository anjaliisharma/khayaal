import { useEffect, useMemo, useState, type PropsWithChildren } from 'react';
import { establishSession, logout as logoutRequest, restoreSession } from '../api/client';
import { AuthContext, type AuthContextValue } from './authContext';

export function AuthProvider({ children }: PropsWithChildren) {
  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    void restoreSession()
      .then(setAuthenticated)
      .finally(() => setLoading(false));
  }, []);
  const value = useMemo<AuthContextValue>(
    () => ({
      authenticated,
      loading,
      async login(email, password) {
        await establishSession('/api/auth/login', { email, password });
        setAuthenticated(true);
      },
      async register(displayName, email, password) {
        await establishSession('/api/auth/register', { displayName, email, password });
        setAuthenticated(true);
      },
      async logout() {
        await logoutRequest();
        setAuthenticated(false);
      }
    }),
    [authenticated, loading]
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
