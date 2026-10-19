import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import type { AiPendingPayment } from "@/lib/ai-model-config";
import { localizedErrorMessage } from "@/lib/error-messages";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { RefreshCw } from "lucide-react";

export function AiPendingPayments() {
  const { t } = useTranslation();
  const [pending, setPending] = useState<AiPendingPayment[]>([]);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [result, setResult] = useState("");
  const [resumed, setResumed] = useState(false);
  const load = async () => {
    try { setPending(await api.modelConfig.pending()); } catch (failure) { setError(localizedErrorMessage(failure)); }
  };
  useEffect(() => { void load(); }, []);
  const settle = async (item: AiPendingPayment) => {
    setBusy(item.id); setError(""); setResumed(false);
    try {
      const value = await api.modelConfig.settle(item);
      const text = typeof value === "string" ? value : value && typeof value === "object" ? ("content" in value ? value.content : "result" in value ? value.result : null) : null;
      if (typeof text === "string") setResult(text);
      setPending((items) => items.filter((entry) => entry.id !== item.id)); setResumed(true);
    } catch (failure) { setError(localizedErrorMessage(failure)); } finally { setBusy(""); }
  };
  if (!pending.length && !error && !result && !resumed) return null;
  return <section className="space-y-3 rounded-lg border border-amber-500/30 bg-amber-500/5 p-4">
    <div className="flex items-center justify-between gap-2"><h3 className="font-medium">{t("aiModels.pendingBilling")}</h3><Button size="sm" variant="ghost" aria-label={t("aiModels.reload")} onClick={() => { setError(""); void load(); }}><RefreshCw className="h-4 w-4" /></Button></div>
    <p className="text-sm text-muted-foreground">{t("aiModels.paymentRequired")}</p>
    {pending.map((item) => <div key={item.id} className="flex flex-wrap items-center justify-between gap-3 border-t pt-3 text-sm"><span>{item.label}</span><Button variant="outline" size="sm" disabled={Boolean(busy)} onClick={() => void settle(item)}>{t("aiModels.resumePayment")}</Button></div>)}
    {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
    {resumed ? <p role="status" className="text-sm">{t("aiModels.resumed")}</p> : null}
    {result ? <div className="space-y-2"><p className="text-sm font-medium">{t("aiModels.recoveredResult")}</p><Textarea aria-label={t("aiModels.recoveredResult")} readOnly value={result} rows={10} /></div> : null}
  </section>;
}
