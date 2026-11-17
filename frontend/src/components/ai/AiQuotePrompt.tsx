import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useTranslation } from "react-i18next";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { pendingAiQuote, subscribeAiQuotes, finishAiQuote } from "@/lib/ai-quote-store";
import { roleName, type AiModelSelection, type AiQuote } from "@/lib/ai-model-config";
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
  const [choices, setChoices] = useState<Record<string, string>>({});
  const revision = useRef(0);
  const estimateLabel = (estimates: AiQuote["roles"][number]["estimates"]) => {
    const labels = new Set<string>();
    let callCount = 0;
    let bounded = false;
    for (const { calls, conditional } of estimates) {
      const match = boundedCalls(calls);
      if (calls === "ONE") callCount++;
      else if (calls === "UP_TO_TWO") { callCount += 2; bounded = true; }
      else if (match) { callCount += Number(match[1]); bounded = true; }
      else labels.add(t("aiModels.calls." + calls));
      bounded ||= conditional;
    }
    if (callCount > 0) labels.add(t(bounded ? "aiModels.calls.UP_TO_N" : "aiModels.calls.EXACT", { count: callCount, maxCalls: callCount }));
    return [...labels].join(" · ");
  };
  useEffect(() => {
    revision.current++;
    setQuote(pending?.quote ?? null); setChoices({}); setError(""); setNotice(""); setBusy(false);
  }, [pending]);
  const select = async (roleKey: string, option: string) => {
    if (!pending || !quote) return;
    const requestRevision = ++revision.current;
    const selections = { ...choices, [roleKey]: option };
    setBusy(true); setError("");
    try {
      const next = await pending.refresh(selections);
      if (requestRevision === revision.current) { setQuote(next); setChoices(selections); }
    } catch {
      if (requestRevision === revision.current) setError(t("aiModels.quoteFailed"));
    } finally {
      if (requestRevision === revision.current) setBusy(false);
    }
  };
  const confirm = async () => {
    if (!pending || !quote) return;
    setBusy(true); setError("");
    try {
      if (Date.parse(quote.expiresAt) <= Date.now()) {
        setQuote(await pending.refresh(choices));
        setNotice(t("aiModels.quoteRenewed")); setBusy(false); return;
      }
      if (remember && Object.keys(choices).length) await pending.remember(choices);
      finishAiQuote(pending, quote.id);
    } catch { setError(t("aiModels.saveFailed")); setBusy(false); }
  };
  return <Dialog open={Boolean(pending)} onOpenChange={(open) => { if (!open && pending && !busy) finishAiQuote(pending); }}>
    <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
      <DialogHeader><DialogTitle>{t("aiModels.confirmTitle")}</DialogTitle><DialogDescription>{t("aiModels.confirmDescription")}</DialogDescription></DialogHeader>
      <div className="space-y-3">
        {quote?.roles.map(({ role, estimates, selected, estimatedCredits }) => <section key={role.definition.key} className="rounded-lg border p-3 sm:p-4">
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2"><span className="font-medium">{roleName(role, i18n.language)}</span>
            <Badge variant="outline">{t(estimates.every((estimate) => estimate.conditional) ? "aiModels.conditional" : "aiModels.required")}</Badge></div>
          {role.billingEnabled ? <select aria-label={roleName(role, i18n.language)} value={selected.optionId} disabled={busy} onChange={(e) => void select(role.definition.key, e.target.value)} className="h-10 w-full min-w-0 rounded-md border bg-background px-3 text-sm">
            {role.options.map((option) => <option key={option.optionId} value={option.optionId} disabled={role.unavailableOptions.includes(option.optionId)}>{option.label}</option>)}
          </select> : <div className="text-sm">{selected.label} · {t("aiModels.free")}</div>}
          <p className="mt-2 break-all text-xs text-muted-foreground">{selected.modelKey}</p>
          <p className="mt-2 break-words text-sm text-muted-foreground"><ModelPrice option={selected} /> · {t("aiModels.estimate", { amount: estimatedCredits })}</p>
          <p className="mt-1 text-xs text-muted-foreground">{estimateLabel(estimates)} · {t(`aiModels.thinking.${selected.thinkingMode}`)}{selected.thinkingEffort ? ` · ${selected.thinkingEffort}` : ""}</p>
        </section>)}
      </div>
      <p className="text-sm font-medium">{t("aiModels.totalEstimate", { amount: quote?.estimatedCredits ?? 0 })}</p>
      <p className="text-xs leading-relaxed text-muted-foreground">{t("aiModels.settlementNotice")}</p>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />{t("aiModels.remember")}</label>
      {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
      {notice ? <p role="status" className="text-sm text-muted-foreground">{notice}</p> : null}
      <DialogFooter><Button variant="outline" disabled={busy} onClick={() => pending && finishAiQuote(pending)}>{t("common.cancel")}</Button>
        <Button disabled={busy || !quote || Boolean(error)} onClick={() => void confirm()}>{busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}{t("aiModels.confirm")}</Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
