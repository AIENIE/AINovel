import { FormEvent, useEffect, useMemo, useState } from "react";
import type React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Copy, Loader2, ShieldCheck } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api-client";

type Mode = "loading" | "password" | "enrollment" | "totp" | "recovery" | "rebind" | "codes";

type AuthResult = {
  recoveryCodes?: string[];
};

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

const AdminLogin = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState<Mode>("loading");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [challengeId, setChallengeId] = useState("");
  const [manualKey, setManualKey] = useState("");
  const [otpauthUri, setOtpauthUri] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [authMode, setAuthMode] = useState<"password" | "totp">("totp");

  const nextPath = useMemo(() => {
    const value = new URLSearchParams(location.search).get("next") || "/admin/dashboard";
    return value.startsWith("/admin") ? value : "/admin/dashboard";
  }, [location.search]);

  useEffect(() => {
    let cancelled = false;
    const isRebind = new URLSearchParams(location.search).get("rebind") === "1";

    const load = async () => {
      try {
        if (isRebind) {
          const result = await api.adminAuth.startRebind();
          if (!cancelled) {
            setEnrollment(result);
            setMode("rebind");
          }
          return;
        }

        try {
          const session = await api.adminAuth.me();
          if (cancelled) return;
          if (session.sessionScope === "RECOVERY") {
            navigate("/admin/login?rebind=1", { replace: true });
          } else {
            navigate(nextPath, { replace: true });
          }
          return;
        } catch {
          // No active admin session: continue with the public bootstrap state.
        }

        const bootstrap = await api.adminAuth.bootstrap();
        if (cancelled) return;
        setAuthMode(bootstrap.authMode);
        setMode("password");
      } catch (cause: unknown) {
        if (!cancelled) {
          setError(errorMessage(cause, "无法初始化管理员认证"));
          setMode("password");
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [location.search, navigate, nextPath]);

  const setEnrollment = (result: { challengeId: string; manualKey: string; otpauthUri: string }) => {
    setChallengeId(result.challengeId);
    setManualKey(result.manualKey);
    setOtpauthUri(result.otpauthUri);
    setCode("");
  };

  const run = async (task: () => Promise<void>) => {
    setLoading(true);
    setError("");
    try {
      await task();
    } catch (cause: unknown) {
      setError(errorMessage(cause, "认证失败"));
    } finally {
      setLoading(false);
    }
  };

  const finish = (result: AuthResult) => {
    if (result.recoveryCodes?.length) {
      setRecoveryCodes(result.recoveryCodes);
      setMode("codes");
      return;
    }
    navigate(nextPath, { replace: true });
  };

  const submitPassword = (event: FormEvent) => {
    event.preventDefault();
    void run(async () => {
      const result = await api.adminAuth.login(username, password);
      setPassword("");
      if (!result.status) {
        navigate(nextPath, { replace: true });
        return;
      }
      if (!result.challengeId) throw new Error("管理员登录挑战无效");
      if (result.status === "ENROLLMENT_REQUIRED") {
        setEnrollment(await api.adminAuth.startEnrollment(result.challengeId));
        setMode("enrollment");
        return;
      }
      setChallengeId(result.challengeId);
      setCode("");
      setMode("totp");
    });
  };

  const confirmEnrollment = (event: FormEvent) => {
    event.preventDefault();
    void run(async () => {
      finish(await api.adminAuth.confirmEnrollment(challengeId, code));
    });
  };

  const submitTotp = (event: FormEvent) => {
    event.preventDefault();
    void run(async () => {
      finish(await api.adminAuth.loginTotp(challengeId, code));
    });
  };

  const submitRecovery = (event: FormEvent) => {
    event.preventDefault();
    void run(async () => {
      await api.adminAuth.loginRecovery(challengeId, code);
      navigate("/admin/login?rebind=1", { replace: true });
    });
  };

  if (mode === "loading") {
    return <LoadingScreen />;
  }

  if (mode === "codes") {
    return (
      <AuthCard title="保存恢复码" description="这些恢复码只显示这一次，请存放在安全位置。">
        <RecoveryCodeList codes={recoveryCodes} />
        <Button className="mt-4 w-full" onClick={() => navigate(nextPath, { replace: true })}>
          我已安全保存
        </Button>
      </AuthCard>
    );
  }

  if (mode === "rebind") {
    return (
      <AuthCard title="重新绑定验证器" description="恢复会话只允许完成新的验证器绑定。">
        <EnrollmentMaterial manualKey={manualKey} otpauthUri={otpauthUri} />
        <form
          className="mt-4 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            void run(async () => finish(await api.adminAuth.confirmRebind(challengeId, code)));
          }}
        >
          <CodeField id="rebind-code" label="新验证器验证码" value={code} onChange={setCode} />
          <Button className="w-full" disabled={loading || code.length !== 6}>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "确认重新绑定"}
          </Button>
        </form>
        <AuthError message={error} />
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title={mode === "enrollment" ? "绑定验证器" : mode === "recovery" ? "恢复管理员访问" : "管理员登录"}
      description={
        mode === "password"
          ? authMode === "password" ? "输入本地管理员账号和密码。" : "先验证管理员密码，再进行动态验证码验证。"
          : mode === "enrollment" ? "密码已验证，请绑定验证器。" : "使用验证器中的 6 位动态验证码。"
      }
    >
      {mode === "password" && (
        <form className="space-y-4" onSubmit={submitPassword}>
          <Field id="admin-username" label="管理员账号" value={username} onChange={setUsername} autoComplete="username" />
          <PasswordField value={password} onChange={setPassword} />
          <Button className="w-full" disabled={loading || !username || !password}>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "继续"}
          </Button>
        </form>
      )}

      {mode === "enrollment" && (
        <form className="space-y-4" onSubmit={confirmEnrollment}>
          <EnrollmentMaterial manualKey={manualKey} otpauthUri={otpauthUri} />
          <CodeField id="enrollment-code" label="首次验证码" value={code} onChange={setCode} />
          <Button className="w-full" disabled={loading || code.length !== 6}>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "确认绑定"}
          </Button>
        </form>
      )}

      {mode === "totp" && (
        <form className="space-y-4" onSubmit={submitTotp}>
          <CodeField id="totp-code" label="动态验证码" value={code} onChange={setCode} />
          <Button className="w-full" disabled={loading || code.length !== 6}>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "登录管理后台"}
          </Button>
          <Button type="button" variant="outline" className="w-full" onClick={() => setMode("recovery")}>
            使用恢复码
          </Button>
          <Button type="button" variant="ghost" className="w-full" onClick={() => { setChallengeId(""); setCode(""); setMode("password"); }}>
            返回密码验证
          </Button>
        </form>
      )}

      {mode === "recovery" && (
        <form className="space-y-4" onSubmit={submitRecovery}>
          <Field id="recovery-code" label="恢复码" value={code} onChange={setCode} />
          <Button className="w-full" disabled={loading || !code}>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : "进入受限恢复"}
          </Button>
          <Button type="button" variant="ghost" className="w-full" onClick={() => setMode("totp")}>
            返回验证码登录
          </Button>
        </form>
      )}

      <AuthError message={error} />
    </AuthCard>
  );
};

const LoadingScreen = () => (
  <div className="flex min-h-screen items-center justify-center bg-zinc-950 text-zinc-100">
    <Loader2 className="h-4 w-4 animate-spin" />
  </div>
);

const AuthCard = ({ title, description, children }: { title: string; description: string; children: React.ReactNode }) => (
  <div className="flex min-h-screen items-center justify-center bg-zinc-950 px-4 text-zinc-100">
    <Card className="w-full max-w-md border-zinc-800 bg-zinc-900 text-zinc-100 shadow-2xl">
      <CardHeader>
        <div className="flex items-center gap-2 text-red-400">
          <ShieldCheck className="h-5 w-5" />
          <span className="text-sm tracking-wide uppercase">Admin Console</span>
        </div>
        <CardTitle>{title}</CardTitle>
        <CardDescription className="text-zinc-400">{description}</CardDescription>
      </CardHeader>
      <CardContent>{children}</CardContent>
      <CardFooter />
    </Card>
  </div>
);

const EnrollmentMaterial = ({ manualKey, otpauthUri }: { manualKey: string; otpauthUri: string }) => (
  <div className="space-y-4">
    <div className="flex justify-center rounded bg-white p-3">
      <QRCodeSVG value={otpauthUri} size={176} includeMargin />
    </div>
    <div className="space-y-2">
      <Label htmlFor="manual-key">手工密钥</Label>
      <div className="flex gap-2">
        <Input id="manual-key" readOnly value={manualKey} className="bg-zinc-950 font-mono text-xs" />
        <Button
          type="button"
          size="icon"
          variant="outline"
          aria-label="复制手工密钥"
          onClick={() => void navigator.clipboard?.writeText(manualKey)}
        >
          <Copy className="h-4 w-4" />
        </Button>
      </div>
    </div>
  </div>
);

const RecoveryCodeList = ({ codes }: { codes: string[] }) => (
  <div className="grid grid-cols-2 gap-2 rounded border border-zinc-700 bg-zinc-950 p-4 font-mono text-sm">
    {codes.map((value) => <div key={value}>{value}</div>)}
  </div>
);

const AuthError = ({ message }: { message: string }) =>
  message ? <div className="mt-4 rounded border border-red-500/40 bg-red-950/40 px-3 py-2 text-sm text-red-300">{message}</div> : null;

const PasswordField = ({ value, onChange }: { value: string; onChange: (value: string) => void }) => (
  <Field id="admin-password" label="管理员密码" type="password" value={value} onChange={onChange} autoComplete="current-password" />
);

const CodeField = ({ id, label, value, onChange }: FieldProps) => (
  <Field id={id} label={label} value={value} onChange={onChange} inputMode="numeric" maxLength={6} />
);

type FieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  inputMode?: "numeric";
  maxLength?: number;
  autoComplete?: string;
};

const Field = ({ id, label, value, onChange, type = "text", inputMode, maxLength, autoComplete }: FieldProps) => (
  <div className="space-y-2">
    <Label htmlFor={id}>{label}</Label>
    <Input
      id={id}
      type={type}
      value={value}
      maxLength={maxLength}
      inputMode={inputMode}
      autoComplete={autoComplete || (type === "password" ? "current-password" : "one-time-code")}
      className="border-zinc-700 bg-zinc-950"
      onChange={(event) => onChange(event.target.value)}
    />
  </div>
);

export default AdminLogin;
