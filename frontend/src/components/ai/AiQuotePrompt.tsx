import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useTranslation } from "react-i18next";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { pendingAiQuote, subscribeAiQuotes, finishAiQuote } from "@/lib/ai-quote-store";
import { featureName, type AiModelSelection, type AiQuote } from "@/lib/ai-model-config";
import { Loader2 } from "lucide-react";

const boundedCalls = (calls: string) => /^UP_TO_(\d+)$/.exec(calls);

export function ModelPrice({ option }: { option: AiModelSelection }) {
  const { t } = useTranslation();
  if (!option.billingEnabled) return <span>{t("aiModels.free")}</span>;
  return <span>{option.pricingMode === "FIXED" ? t("aiModels.fixedRate", { amount: option.fixedCredits })
    : t("aiModels.tokenRate", { input: option.inputPer1k ?? 0, output: option.outputPer1k ?? 0 })}</span>;
}
export function AiQuotePrompt() {
  const pending = useSyncExternalStore(subscribeAiQuotes, pendingAiQuote);
  const { t, i18n } = useTranslation();
  const [quote, setQuote] = useState<AiQuote | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [remember, setRemember] = useState(true);
  const revision = useRef(0);
  const callLabel = (calls: string) => {
    const bounded = boundedCalls(calls);
    return bounded ? t("aiModels.calls.UP_TO_N", { maxCalls: Number(bounded[1]) }) : t(`aiModels.calls.${calls}`);
  };
  useEffect(() => { revision.current++; setQuote(pending?.quote ?? null); setError(""); setNotice(""); setBusy(false); }, [pending]);
  const select = async (feature: string, option: string) => {
    if (!pending || !quote) return;
    const requestRevision = ++revision.current;
    setBusy(true); setError("");
    const selections = Object.fromEntries(quote.steps.map((step) => [step.step.featureKey, step.selected.optionId]));
    selections[feature] = option;
    try { const next = await pending.refresh(selections); if (requestRevision === revision.current) setQuote(next); }
    catch { if (requestRevision === revision.current) setError(t("aiModels.quoteFailed")); }
    finally { if (requestRevision === revision.current) setBusy(false); }
  };
  const confirm = async () => {
    if (!pending || !quote) return;
    setBusy(true); setError("");
    try {
      if (Date.parse(quote.expiresAt) <= Date.now()) {
        setQuote(await pending.refresh(Object.fromEntries(quote.steps.map((step) => [step.step.featureKey, step.selected.optionId]))));
        setNotice(t("aiModels.quoteRenewed")); setBusy(false); return;
      }
      if (remember) await pending.remember(Object.fromEntries(quote.steps.filter((step) => step.selected.billingEnabled).map((step) => [step.step.featureKey, step.selected.optionId])));
      finishAiQuote(pending, quote.id);
    } catch { setError(t("aiModels.saveFailed")); setBusy(false); }
  };
  return <Dialog open={Boolean(pending)} onOpenChange={(open) => { if (!open && pending && !busy) finishAiQuote(pending); }}>
    <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
      <DialogHeader><DialogTitle>{t("aiModels.confirmTitle")}</DialogTitle><DialogDescription>{t("aiModels.confirmDescription")}</DialogDescription></DialogHeader>
      <div className="space-y-3">
        {quote?.steps.map(({ step, feature, selected, estimatedCredits }) => <section key={step.featureKey} className="rounded-lg border p-3 sm:p-4">
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2"><span className="font-medium">{featureName(feature, i18n.language)}</span>
            <Badge variant="outline">{t(step.conditional ? "aiModels.conditional" : "aiModels.required")}</Badge></div>
          {feature.billingEnabled ? <select aria-label={featureName(feature, i18n.language)} value={selected.optionId} disabled={busy} onChange={(e) => void select(step.featureKey, e.target.value)} className="h-10 w-full min-w-0 rounded-md border bg-background px-3 text-sm">
            {feature.options.map((option) => <option key={option.optionId} value={option.optionId} disabled={feature.unavailableOptions.includes(option.optionId)}>{option.label}</option>)}
          </select> : <div className="text-sm">{selected.label} · {t("aiModels.free")}</div>}
          <p className="mt-2 break-all text-xs text-muted-foreground">{selected.modelKey}</p>
          <p className="mt-2 break-words text-sm text-muted-foreground"><ModelPrice option={selected} /> · {t(boundedCalls(step.calls) ? "aiModels.boundedEstimate" : "aiModels.estimate", { amount: estimatedCredits })}</p>
          <p className="mt-1 text-xs text-muted-foreground">{callLabel(step.calls)} · {t(`aiModels.thinking.${selected.thinkingMode}`)}{selected.thinkingEffort ? ` · ${selected.thinkingEffort}` : ""}</p>
        </section>)}
      </div>
      <p className="text-sm font-medium">{t(quote?.steps.some(({ step }) => boundedCalls(step.calls)) ? "aiModels.totalBoundedEstimate" : "aiModels.totalEstimate", { amount: quote?.estimatedCredits ?? 0 })}</p>
      <p className="text-xs leading-relaxed text-muted-foreground">{t("aiModels.settlementNotice")}</p>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />{t("aiModels.remember")}</label>
      {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
      {notice ? <p role="status" className="text-sm text-muted-foreground">{notice}</p> : null}
      <DialogFooter><Button variant="outline" disabled={busy} onClick={() => pending && finishAiQuote(pending)}>{t("common.cancel")}</Button>
        <Button disabled={busy || !quote || Boolean(error)} onClick={() => void confirm()}>{busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}{t("aiModels.confirm")}</Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
