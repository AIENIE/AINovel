import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/contexts/AuthContext";
import { validateSsoState } from "@/lib/sso";

type SsoSessionResponse = {
  accessToken: string;
};

const callbackRedirectUrl = (location: Location) => {
  const url = new URL(`${window.location.origin}${location.pathname}${location.search}`);
  url.searchParams.delete("code");
  url.searchParams.delete("state");
  return url.toString();
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

  useEffect(() => {
    let cancelled = false;
    const handleCallback = async () => {
      const params = new URLSearchParams(location.search);
      const state = params.get("state");
      const code = params.get("code") || "";

      if (!validateSsoState(state)) {
        toast.error("单点登录失败：state 校验未通过");
        navigate("/login", { replace: true });
        return;
      }

      if (!code) {
        toast.error("单点登录失败：未获取到授权码");
        navigate("/login", { replace: true });
        return;
      }

      try {
        const session = await exchangeSsoCode(code, callbackRedirectUrl(window.location));
        await acceptToken(session.accessToken);
      } catch {
        if (!cancelled) {
          toast.error("单点登录失败：会话校验未通过");
          navigate("/login", { replace: true });
        }
        return;
      }

      if (cancelled) return;

      const qs = new URLSearchParams(location.search);
      const next = qs.get("next") || "/workbench";

      window.history.replaceState(null, "", callbackRedirectUrl(window.location));
      toast.success("单点登录成功");
      navigate(next, { replace: true });
    };

    handleCallback().catch(() => {
      if (!cancelled) {
        toast.error("单点登录失败：回调处理异常");
        navigate("/login", { replace: true });
      }
    });

    return () => {
      cancelled = true;
    };
  }, [acceptToken, location.pathname, location.search, navigate]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-200 flex items-center justify-center p-6">
      <div className="text-sm text-zinc-500">正在完成单点登录…</div>
    </div>
  );
};

export default SsoCallback;
