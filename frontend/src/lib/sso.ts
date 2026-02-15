export type SsoMode = "login" | "register";

const resolveDefaultSsoBase = () => {
  if (typeof window === "undefined") return "";
  const { hostname, origin } = window.location;
  if (hostname === "ainovel.seekerhut.com" || hostname === "ainovel.aienie.com") {
    return origin;
  }
  // Local/dev fallback: use test domain userservice entry directly.
  return "http://ainovel.seekerhut.com";
};

const resolveSsoBase = () => {
  return (import.meta.env.VITE_SSO_BASE_URL as string | undefined) || resolveDefaultSsoBase();
};

const resolveCallbackUrl = (nextPath?: string) => {
  if (typeof window === "undefined") return "";
  const origin = window.location.origin;
  const callback = new URL("/sso/callback", origin);
  if (nextPath) callback.searchParams.set("next", nextPath);
  return callback.toString();
};

export const buildSsoUrl = (mode: SsoMode, nextPath?: string) => {
  const base = resolveSsoBase();
  const callback = resolveCallbackUrl(nextPath);
  const path = mode === "login" ? "/sso/login" : "/register";
  const url = new URL(path, base);
  url.searchParams.set("redirect", callback);
  return url.toString();
};
