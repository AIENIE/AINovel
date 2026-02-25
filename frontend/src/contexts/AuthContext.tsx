import React, { createContext, useContext, useState, useEffect } from "react";
import { User } from "@/types";
import { api } from "@/lib/mock-api";

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isLoading: boolean;
  acceptToken: (token: string) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>; // V2: Refresh credits/check-in status
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const isAuthFailure = (error: unknown) => {
    if (!(error instanceof Error)) return false;
    return error.message.includes("401") || error.message.includes("403");
  };

  const initAuth = async () => {
    const token = localStorage.getItem("token");
    if (token) {
      try {
        const userData = await api.user.getProfile();
        setUser(userData);
      } catch (error) {
        console.error("Auth validation failed", error);
        if (isAuthFailure(error)) {
          localStorage.removeItem("token");
        }
      }
    }
    setIsLoading(false);
  };

  useEffect(() => {
    initAuth();
  }, []);

  const acceptToken = async (token: string) => {
    localStorage.setItem("token", token);
    setIsLoading(true);
    try {
      const userData = await api.user.getProfile();
      setUser(userData);
    } catch (error) {
      console.error("Token accept validation failed", error);
      localStorage.removeItem("token");
      setUser(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem("token");
    setUser(null);
  };

  const refreshProfile = async () => {
    try {
      const updatedUser = await api.user.getProfile();
      setUser(updatedUser);
    } catch (error) {
      console.error("Failed to refresh profile", error);
      if (isAuthFailure(error)) {
        localStorage.removeItem("token");
        setUser(null);
      }
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isAdmin: user?.role === 'admin',
        isLoading,
        acceptToken,
        logout,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
