import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { authApi } from '../api/services';
import { getStoredToken, setStoredToken } from '../api/client';
import type { AuthResponse, UserProfile } from '../types/api';

interface AuthContextValue {
  user: UserProfile | null;
  loading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  loginWithAuthResponse: (auth: AuthResponse) => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(Boolean(getStoredToken()));

  const clearSession = useCallback(() => {
    setStoredToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    const token = getStoredToken();
    if (!token) {
      setLoading(false);
      return;
    }
    authApi
      .me()
      .then((profile) => setUser(profile))
      .catch(() => clearSession())
      .finally(() => setLoading(false));
  }, [clearSession]);

  useEffect(() => {
    const handler = () => clearSession();
    window.addEventListener('auth:unauthorized', handler);
    return () => window.removeEventListener('auth:unauthorized', handler);
  }, [clearSession]);

  const loginWithAuthResponse = useCallback((auth: AuthResponse) => {
    setStoredToken(auth.accessToken);
    setUser(auth.user);
  }, []);

  const logout = useCallback(async () => {
    try {
      if (getStoredToken()) {
        await authApi.logout();
      }
    } catch {
      // Clear local session regardless of server response
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const isAdmin = Boolean(user?.roles?.includes('ADMIN'));

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        isAuthenticated: Boolean(user),
        isAdmin,
        loginWithAuthResponse,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return ctx;
}
