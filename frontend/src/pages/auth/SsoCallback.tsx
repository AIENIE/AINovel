import { useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/contexts/AuthContext";
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
  const { acceptToken } = useAuth();
  const processorRef = useRef<ReturnType<typeof createSsoCallbackProcessor> | null>(null);

  if (!processorRef.current) {
    processorRef.current = createSsoCallbackProcessor({
      validateState: validateSsoState,
      exchangeSsoCode,
      acceptToken,
      onSuccess: (nextPath) => {
        window.history.replaceState(null, "", callbackRedirectUrl(window.location));
        toast.success("单点登录成功");
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
        toast.error("单点登录失败：回调处理异常");
        navigate("/login", { replace: true });
      }
    });

    return () => {
      cancelled = true;
    };
  }, [location.pathname, location.search, navigate]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-200 flex items-center justify-center p-6">
      <div className="text-sm text-zinc-500">正在完成单点登录…</div>
    </div>
  );
};

export default SsoCallback;
