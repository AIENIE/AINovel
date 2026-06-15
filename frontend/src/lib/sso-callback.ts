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

const encodeQueryValue = (value: string) => encodeURIComponent(value).replace(/%2F/g, "/");

export const buildSsoCallbackRedirectUrl = (origin: string, pathname: string, search: string) => {
  const params = new URLSearchParams(search);
  params.delete("code");
  params.delete("state");

  const query = Array.from(params.entries())
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeQueryValue(value)}`)
    .join("&");

  return query ? `${origin}${pathname}?${query}` : `${origin}${pathname}`;
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
      if (!isCancelled?.()) deps.onFailure("单点登录失败：state 校验未通过");
      return;
    }

    if (!code) {
      if (!isCancelled?.()) deps.onFailure("单点登录失败：未获取到授权码");
      return;
    }

    try {
      const session = await deps.exchangeSsoCode(code, redirect);
      await deps.acceptToken(session.accessToken);
    } catch {
      if (!isCancelled?.()) deps.onFailure("单点登录失败：会话校验未通过");
      return;
    }

    if (isCancelled?.()) return;
    deps.onSuccess(params.get("next") || "/workbench");
  };
};
