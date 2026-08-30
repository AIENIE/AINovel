import { useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/contexts/auth-state";
import { validateSsoState } from "@/lib/sso";
import { buildSsoCallbackRedirectUrl, createSsoCallbackProcessor, type SsoSessionResponse } from "@/lib/sso-callback";

const callbackRedirectUrl = (location: Location) => {
  return buildSsoCallbackRedirectUrl(window.location.origin, location.pathname, location.search);
};

const exchangeSsoCode = async (code: string, redirect: string): Promise<SsoSessionResponse> => {
  const res = await fetch("/api/v1/sso/session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code, redirect }),
  });
  if (!res.ok) {
    throw new Error(`SSO session exchange failed: ${res.status}`);
  }
  return res.json();
};

const SsoCallback = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const { acceptToken } = useAuth();
  const processorRef = useRef<ReturnType<typeof createSsoCallbackProcessor> | null>(null);

  if (!processorRef.current) {
    processorRef.current = createSsoCallbackProcessor({
      validateState: validateSsoState,
      exchangeSsoCode,
      acceptToken,
      onSuccess: (nextPath) => {
        window.history.replaceState(null, "", callbackRedirectUrl(window.location));
        toast.success(t("auth.ssoSuccess"));
        navigate(nextPath, { replace: true });
      },
      onFailure: (message) => {
        toast.error(message);
        navigate("/login", { replace: true });
      },
    });
  }

  useEffect(() => {
    let cancelled = false;
    processorRef.current?.({
      search: location.search,
      redirect: callbackRedirectUrl(window.location),
      isCancelled: () => cancelled,
    }).catch(() => {
      if (!cancelled) {
        toast.error(t("auth.ssoCallbackError"));
        navigate("/login", { replace: true });
      }
    });

    return () => {
      cancelled = true;
    };
  }, [location.pathname, location.search, navigate, t]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-200 flex items-center justify-center p-6">
      <div className="text-sm text-zinc-500">{t("auth.ssoRedirecting")}</div>
    </div>
  );
};

export default SsoCallback;
