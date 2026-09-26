import { normalizeNextPath } from "./sso";
import { t } from "@/i18n";

export type SsoSessionResponse = {
  accessToken: string;
};

export type SsoCallbackProcessorDeps = {
  validateState: (receivedState: string | null | undefined) => boolean;
  exchangeSsoCode: (code: string, redirect: string) => Promise<SsoSessionResponse>;
  acceptToken: (token: string) => Promise<void>;
  onSuccess: (nextPath: string) => void;
  onFailure: (message: string) => void;
};

export type SsoCallbackRunArgs = {
  search: string;
  redirect: string;
  isCancelled?: () => boolean;
};

export const buildSsoCallbackRedirectUrl = (origin: string, pathname: string, search: string) => {
  // Keep the original encoded redirect byte-for-byte: the SSO code is bound to it.
  const query = search.replace(/^\?/, "").split("&").filter((part) => {
    const key = decodeURIComponent(part.split("=", 1)[0]);
    return part && key !== "code" && key !== "state";
  }).join("&");
  return `${origin}${pathname}${query ? `?${query}` : ""}`;
};

export const createSsoCallbackProcessor = (deps: SsoCallbackProcessorDeps) => {
  let handled = false;

  return async ({ search, redirect, isCancelled }: SsoCallbackRunArgs) => {
    if (handled) return;
    handled = true;

    const params = new URLSearchParams(search);
    const state = params.get("state");
    const code = params.get("code") || "";

    if (!deps.validateState(state)) {
      if (!isCancelled?.()) deps.onFailure(t("auth.ssoFailed"));
      return;
    }

    if (!code) {
      if (!isCancelled?.()) deps.onFailure(t("auth.ssoNoCode"));
      return;
    }

    try {
      const session = await deps.exchangeSsoCode(code, redirect);
      await deps.acceptToken(session.accessToken);
    } catch {
      if (!isCancelled?.()) deps.onFailure(t("auth.ssoSessionFailed"));
      return;
    }

    if (isCancelled?.()) return;
    deps.onSuccess(normalizeNextPath(params.get("next") || undefined));
  };
};
