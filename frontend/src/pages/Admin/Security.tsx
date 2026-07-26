import { FormEvent, useEffect, useState } from "react";
import { AlertTriangle, Loader2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api-client";

type Status = {
  enrolled: boolean;
  recoveryCodesRemaining: number;
  lowRecoveryThreshold: number;
};

const AdminSecurity = () => {
  const [status, setStatus] = useState<Status | null>(null);
  const [code, setCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setStatus(await api.adminAuth.status());
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "无法读取安全状态");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const regenerate = (event: FormEvent) => {
    event.preventDefault();
    void (async () => {
      setSubmitting(true);
      setError("");
      try {
        const codes = await api.adminAuth.regenerateRecoveryCodes(code);
        setRecoveryCodes(codes);
        setCode("");
        await load();
      } catch (cause: unknown) {
        setError(cause instanceof Error ? cause.message : "无法重新生成恢复码");
      } finally {
        setSubmitting(false);
      }
    })();
  };

  if (loading) {
    return <div className="flex min-h-48 items-center justify-center text-zinc-400"><Loader2 className="h-4 w-4 animate-spin" /></div>;
  }

  const lowCodes = status && status.recoveryCodesRemaining <= status.lowRecoveryThreshold;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">账户安全</h1>
      </div>

      <Card className="border-zinc-800 bg-zinc-900 text-zinc-100">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base"><ShieldCheck className="h-5 w-5 text-emerald-400" />验证器</CardTitle>
          <CardDescription className="text-zinc-400">{status?.enrolled ? "已启用" : "未启用"}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-baseline justify-between border-b border-zinc-800 pb-4">
            <span className="text-sm text-zinc-400">可用恢复码</span>
            <span className="font-mono text-lg">{status?.recoveryCodesRemaining ?? 0}</span>
          </div>
          {lowCodes && (
            <div className="flex gap-2 rounded border border-amber-500/40 bg-amber-950/30 p-3 text-sm text-amber-200">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>恢复码数量偏低，请在安全位置保存重新生成后的新恢复码。</span>
            </div>
          )}
          <form className="space-y-3" onSubmit={regenerate}>
            <div className="space-y-2">
              <Label htmlFor="regenerate-totp">当前动态验证码</Label>
              <Input
                id="regenerate-totp"
                value={code}
                maxLength={6}
                inputMode="numeric"
                autoComplete="one-time-code"
                className="border-zinc-700 bg-zinc-950"
                onChange={(event) => setCode(event.target.value)}
              />
            </div>
            <Button disabled={submitting || code.length !== 6}>
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : "重新生成恢复码"}
            </Button>
          </form>
          {error && <div className="rounded border border-red-500/40 bg-red-950/40 px-3 py-2 text-sm text-red-300">{error}</div>}
        </CardContent>
      </Card>

      {recoveryCodes.length > 0 && (
        <Card className="border-amber-700/50 bg-zinc-900 text-zinc-100">
          <CardHeader>
            <CardTitle className="text-base">新的恢复码</CardTitle>
            <CardDescription className="text-zinc-400">旧恢复码已失效；这些新恢复码只显示一次。</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-2 rounded border border-zinc-700 bg-zinc-950 p-4 font-mono text-sm">
              {recoveryCodes.map((value) => <div key={value}>{value}</div>)}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default AdminSecurity;
