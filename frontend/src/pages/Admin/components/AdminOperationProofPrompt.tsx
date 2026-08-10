import { FormEvent, useEffect, useRef, useState } from "react";
import { ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { registerAdminOperationCodePrompt } from "@/lib/admin-operation-proof";

type Pending = { resolve: (value: string | null) => void };

const AdminOperationProofPrompt = () => {
  const [pending, setPending] = useState<Pending | null>(null);
  const [code, setCode] = useState("");
  const pendingRef = useRef<Pending | null>(null);

  useEffect(() => {
    return registerAdminOperationCodePrompt(
      () => new Promise<string | null>((resolve) => {
        const next = { resolve };
        pendingRef.current?.resolve(null);
        pendingRef.current = next;
        setCode("");
        setPending(next);
      }),
    );
  }, []);

  const finish = (value: string | null) => {
    const current = pendingRef.current;
    pendingRef.current = null;
    setPending(null);
    setCode("");
    current?.resolve(value);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (/^\d{6}$/.test(code)) finish(code);
  };

  return (
    <Dialog open={Boolean(pending)} onOpenChange={(open) => { if (!open) finish(null); }}>
      <DialogContent className="border-zinc-800 bg-zinc-900 text-zinc-100">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-amber-400" />
            确认高风险操作
          </DialogTitle>
          <DialogDescription className="text-zinc-400">
            输入验证器中的 6 位动态验证码。验证结果仅对当前操作生效一次，并在 60 秒内过期。
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={submit}>
          <div className="space-y-2">
            <Label htmlFor="admin-operation-totp">动态验证码</Label>
            <Input
              id="admin-operation-totp"
              autoFocus
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              className="border-zinc-700 bg-zinc-950 font-mono tracking-[0.3em]"
              onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => finish(null)}>取消</Button>
            <Button disabled={!/^\d{6}$/.test(code)}>验证并继续</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default AdminOperationProofPrompt;
