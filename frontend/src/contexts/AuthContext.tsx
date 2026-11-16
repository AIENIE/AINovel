import React, { useCallback, useEffect, useRef, useState } from "react";
import type { UserIdentity } from "@/types";
import { AuthContext } from "@/contexts/auth-state";
import { api, isApiError } from "@/lib/api-client";
import { localizedErrorMessage } from "@/lib/error-messages";
import { reportClientError } from "@/lib/client-error-reporting";

const isAuthFailure = (error: unknown) => {
  return isApiError(error) && error.status === 401;
};
const hasNewerToken = (token: string | null) => {
  const current = localStorage.getItem("token");
  return current !== null && current !== token;
};

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<UserIdentity | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [authUnavailable, setAuthUnavailable] = useState(false);
  const [authError, setAuthError] = useState("");
  const identityRequest = useRef(0);

  const loadIdentity = useCallback(async (token: string | null, exchanged: boolean) => {
    const request = ++identityRequest.current;
    setUser(null);
    setIsLoading(Boolean(token));
    setAuthUnavailable(false);
    setAuthError("");
    if (!token) return;
    try {
      const userData = await api.user.getIdentity();
      if (request !== identityRequest.current || localStorage.getItem("token") !== token) return;
      setUser(userData);
    } catch (error) {
      if (request !== identityRequest.current || hasNewerToken(token)) return;
      reportClientError("Authentication request failed", exchanged ? "auth.session-accept" : "auth.initialize");
      if (isAuthFailure(error)) {
        localStorage.removeItem("token");
        if (exchanged) throw error;
        return;
      }
      setAuthUnavailable(true);
      setAuthError(localizedErrorMessage(error));
    } finally {
      if (request === identityRequest.current && !hasNewerToken(token)) setIsLoading(false);
    }
  }, []);

  const initAuth = useCallback(() => loadIdentity(localStorage.getItem("token"), false), [loadIdentity]);

  useEffect(() => {
    void initAuth();
  }, [initAuth]);

  const acceptToken = useCallback((token: string) => {
    localStorage.setItem("token", token);
    return loadIdentity(token, true);
  }, [loadIdentity]);

  const logout = useCallback(() => {
    identityRequest.current++;
    localStorage.removeItem("token");
    setUser(null);
    setIsLoading(false);
    setAuthUnavailable(false);
    setAuthError("");
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isAdmin: user?.role === 'admin',
        isLoading,
        authUnavailable,
        authError,
        retryAuth: initAuth,
        acceptToken,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
