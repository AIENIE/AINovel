import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/contexts/AuthContext";
import { validateSsoState } from "@/lib/sso";

const SsoCallback = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { acceptToken, refreshProfile } = useAuth();

  useEffect(() => {
    const params = new URLSearchParams(location.hash.replace(/^#/, ""));
    const state = params.get("state");
    const accessToken = params.get("access_token") || "";

    if (!validateSsoState(state)) {
      toast.error("单点登录失败：state 校验未通过");
      navigate("/login", { replace: true });
      return;
    }

    if (!accessToken) {
      toast.error("单点登录失败：未获取到 token");
      navigate("/login", { replace: true });
      return;
    }

    acceptToken(accessToken);
    // Best-effort: refresh profile after storing token
    refreshProfile().catch(() => undefined);

    const qs = new URLSearchParams(location.search);
    const next = qs.get("next") || "/workbench";

    window.history.replaceState(null, "", `${location.pathname}${location.search}`);
    toast.success("单点登录成功");
    navigate(next, { replace: true });
  }, [acceptToken, refreshProfile, location.hash, location.pathname, location.search, navigate]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-200 flex items-center justify-center p-6">
      <div className="text-sm text-zinc-500">正在完成单点登录…</div>
    </div>
  );
};

export default SsoCallback;
