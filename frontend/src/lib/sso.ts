export type SsoMode = "login" | "register";

const SSO_STATE_KEY = "ainovel.sso.state";
const DEFAULT_NEXT_PATH = "/workbench";

const resolveBackendBase = () => {
  if (typeof window === "undefined") return "http://127.0.0.1";
  return window.location.origin;
};

const normalizeNextPath = (nextPath?: string) => {
  if (!nextPath) return DEFAULT_NEXT_PATH;
  if (!nextPath.startsWith("/") || nextPath.startsWith("//")) return DEFAULT_NEXT_PATH;
  return nextPath;
};

const randomState = () => {
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    const bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    return Array.from(bytes)
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
  }
  return `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`;
};

export const issueSsoState = () => {
  const state = randomState();
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(SSO_STATE_KEY, state);
  }
  return state;
};

export const validateSsoState = (receivedState: string | null | undefined) => {
  if (typeof window === "undefined") return false;
  const expectedState = window.sessionStorage.getItem(SSO_STATE_KEY);
  window.sessionStorage.removeItem(SSO_STATE_KEY);
  if (!expectedState || !receivedState) return false;
  return expectedState === receivedState;
};

export const buildSsoUrl = (mode: SsoMode, nextPath: string | undefined, state: string) => {
  if (!state.trim()) {
    throw new Error("sso state is required");
  }
  const path = mode === "login" ? "/api/v1/sso/login" : "/api/v1/sso/register";
  const base = resolveBackendBase();
  const url = new URL(path, base);
  url.searchParams.set("next", normalizeNextPath(nextPath));
  url.searchParams.set("state", state);
  return url.toString();
};
