import { FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Loader2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { adminSession, api } from "@/lib/mock-api";

const AdminLogin = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [checkingToken, setCheckingToken] = useState(true);

  const nextPath = useMemo(() => {
    const qs = new URLSearchParams(location.search);
    const value = qs.get("next") || "/admin/dashboard";
    return value.startsWith("/admin") ? value : "/admin/dashboard";
  }, [location.search]);

  useEffect(() => {
    let cancelled = false;
    const token = adminSession.getToken();
    if (!token) {
      setCheckingToken(false);
      return () => {
        cancelled = true;
      };
    }

    api.adminAuth
      .me()
      .then(() => {
        if (!cancelled) {
          navigate(nextPath, { replace: true });
        }
      })
      .catch(() => {
        adminSession.clearToken();
        if (!cancelled) {
          setCheckingToken(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [navigate, nextPath]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const result = await api.adminAuth.login(username.trim(), password);
      adminSession.setToken(result.token);
      navigate(nextPath, { replace: true });
    } catch (e: any) {
      setError(e?.message || "登录失败");
    } finally {
      setLoading(false);
    }
  };

  if (checkingToken) {
    return (
      <div className="min-h-screen bg-zinc-950 text-zinc-100 flex items-center justify-center">
        <div className="flex items-center gap-2 text-zinc-400">
          <Loader2 className="h-4 w-4 animate-spin" /> 正在验证管理员会话...
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex items-center justify-center px-4">
      <Card className="w-full max-w-md border-zinc-800 bg-zinc-900 text-zinc-100 shadow-2xl">
        <CardHeader className="space-y-2">
          <div className="flex items-center gap-2 text-red-400">
            <ShieldCheck className="h-5 w-5" />
            <span className="text-sm tracking-wide uppercase">Admin Console</span>
          </div>
          <CardTitle className="text-2xl">管理员登录</CardTitle>
          <CardDescription className="text-zinc-400">使用本地配置账号（ADMIN_USERNAME / ADMIN_PASSWORD）登录管理后台。</CardDescription>
        </CardHeader>
        <form onSubmit={onSubmit}>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="admin-username">用户名</Label>
              <Input
                id="admin-username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入管理员用户名"
                autoComplete="username"
                className="bg-zinc-950 border-zinc-700"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="admin-password">密码</Label>
              <Input
                id="admin-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入管理员密码"
                autoComplete="current-password"
                className="bg-zinc-950 border-zinc-700"
              />
            </div>
            {error && <div className="rounded border border-red-500/40 bg-red-950/40 px-3 py-2 text-sm text-red-300">{error}</div>}
          </CardContent>
          <CardFooter>
            <Button type="submit" className="w-full" disabled={loading || !username.trim() || !password}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "登录管理后台"}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
};

export default AdminLogin;
