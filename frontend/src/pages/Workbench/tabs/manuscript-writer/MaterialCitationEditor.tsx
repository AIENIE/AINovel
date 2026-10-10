import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import type { EvidenceHit } from "@/lib/api/domains/material-evidence";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

export function MaterialCitationEditor({ source, manuscript, scene, bodyVersion, disabled, close, saved }: {
  source: EvidenceHit; manuscript: string; scene: string; bodyVersion: string; disabled: boolean; close: () => void; saved: () => void;
}) {
  const { t } = useTranslation();
  const [body, setBody] = useState<Awaited<ReturnType<typeof api.evidence.citationBody>> | null>(null);
  const [block, setBlock] = useState("");
  const [sourceQuote, setSourceQuote] = useState(source.text);
  const [bodyQuote, setBodyQuote] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const intent = useRef<{ hash: string; key: string } | null>(null);
  useEffect(() => {
    let observing = true;
    api.evidence.citationBody(manuscript, scene).then(value => { if (observing) { setBody(value); setBlock(value.blocks[0]?.id ?? ""); } }).catch((e: unknown) => { if (observing) setError(e instanceof Error ? e.message : t("errors.loadFailed")); });
    return () => { observing = false; };
  }, [manuscript, scene, t]);
  const submit = async () => {
    if (!body || pending.current || disabled || !bodyVersion || !sourceQuote.trim() || !bodyQuote.trim()) return;
    const offset = source.text.indexOf(sourceQuote);
    if (offset < 0 || source.text.indexOf(sourceQuote, offset + 1) >= 0) { setError(t("evidence.exactQuoteRequired")); return; }
    const input = { revisionId: source.revisionId, branchId: body.branchId, bodyVersion, relationType: "CONFIRMED" as const,
      start: source.start + Array.from(source.text.slice(0, offset)).length, end: source.start + Array.from(source.text.slice(0, offset + sourceQuote.length)).length,
      quote: sourceQuote, bodyQuote, bodyBlockId: block, expectedManuscriptVersion: body.manuscriptVersion };
    const hash = JSON.stringify(input); if (intent.current?.hash !== hash) intent.current = { hash, key: crypto.randomUUID() };
    pending.current = true; setBusy(true); setError("");
    try { await api.evidence.citation(manuscript, scene, { ...input, requestKey: intent.current.key }); saved(); }
    catch (e: unknown) { setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { pending.current = false; setBusy(false); }
  };
  return <section className="space-y-3 rounded-md border p-3">
    <h4 className="font-medium">{t("evidence.confirmCitation")} · {source.title}</h4>
    <p className="text-xs text-muted-foreground">{t("evidence.citationHelp")}</p>
    {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}
    <Label className="block space-y-1">{t("evidence.sourceQuote")}<Textarea value={sourceQuote} maxLength={4000} onChange={event => setSourceQuote(event.target.value)} /></Label>
    <Label className="block space-y-1">{t("evidence.bodyBlock")}<select className="w-full rounded-md border bg-background p-2" value={block} onChange={event => setBlock(event.target.value)}>{body?.blocks.map(item => <option key={item.id} value={item.id}>{item.id} · {item.text.slice(0, 60)}</option>)}</select></Label>
    <p className="max-h-48 overflow-auto whitespace-pre-wrap break-words text-sm">{body?.blocks.find(item => item.id === block)?.text}</p>
    <Label className="block space-y-1">{t("evidence.bodyQuote")}<Textarea value={bodyQuote} maxLength={4000} onChange={event => setBodyQuote(event.target.value)} /></Label>
    {disabled && <p className="text-sm text-amber-700">{t("evidence.saveFirst")}</p>}
    {!bodyVersion && <p className="text-sm text-muted-foreground">{t("evidence.citationVersionRequired")}</p>}
    <div className="flex flex-wrap gap-2"><Button size="sm" disabled={!body || !block || !bodyQuote.trim() || !sourceQuote.trim() || !bodyVersion || busy || disabled} onClick={() => void submit()}>{t("evidence.confirmCitation")}</Button><Button size="sm" variant="outline" disabled={busy} onClick={close}>{t("common.cancel")}</Button></div>
  </section>;
}
