import { createContext, useContext } from "react";
import type { UserIdentity } from "@/types";

export type AuthContextValue = {
  user: UserIdentity | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isLoading: boolean;
  authUnavailable?: boolean;
  authError?: string;
  retryAuth?: () => Promise<void>;
  acceptToken: (token: string) => Promise<void>;
  logout: () => void;
};

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === undefined) throw new Error("useAuth must be used within an AuthProvider");
  return context;
}
