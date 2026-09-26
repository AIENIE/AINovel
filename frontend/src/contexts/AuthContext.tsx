import React, { useCallback, useState, useEffect } from "react";
import { User } from "@/types";
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
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [authUnavailable, setAuthUnavailable] = useState(false);
  const [authError, setAuthError] = useState("");

  const initAuth = useCallback(async () => {
    setIsLoading(true);
    setAuthUnavailable(false);
    const token = localStorage.getItem("token");
    if (token) {
      try {
        const userData = await api.user.getProfile();
        if (localStorage.getItem("token") !== token) return;
        setUser(userData);
        setAuthUnavailable(false);
      } catch (error) {
        if (hasNewerToken(token)) return;
        reportClientError("Authentication request failed", "auth.initialize");
        if (isAuthFailure(error)) {
          localStorage.removeItem("token");
          setUser(null);
        } else {
          setAuthUnavailable(true);
          setAuthError(localizedErrorMessage(error));
        }
      }
    }
    if (!hasNewerToken(token)) setIsLoading(false);
  }, []);

  useEffect(() => {
    initAuth();
  }, [initAuth]);

  const acceptToken = useCallback(async (token: string) => {
    localStorage.setItem("token", token);
    setIsLoading(true);
    try {
      const userData = await api.user.getProfile();
      if (localStorage.getItem("token") !== token) return;
      setUser(userData);
      setAuthUnavailable(false);
    } catch (error) {
      if (hasNewerToken(token)) return;
      reportClientError("Authentication request failed", "auth.session-accept");
      if (isAuthFailure(error)) {
        localStorage.removeItem("token");
        setUser(null);
        throw error;
      }
      setAuthUnavailable(true);
      setAuthError(localizedErrorMessage(error));
    } finally {
      if (!hasNewerToken(token)) setIsLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("token");
    setUser(null);
    setAuthUnavailable(false);
  }, []);

  const refreshProfile = useCallback(async () => {
    const token = localStorage.getItem("token");
    try {
      const updatedUser = await api.user.getProfile();
      if (localStorage.getItem("token") !== token) return;
      setUser(updatedUser);
    } catch (error) {
      if (hasNewerToken(token)) return;
      reportClientError("Authentication request failed", "auth.profile-refresh");
      if (isAuthFailure(error)) {
        localStorage.removeItem("token");
        setUser(null);
      }
    }
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
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
