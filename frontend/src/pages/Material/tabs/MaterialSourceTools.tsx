import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import type { EvidenceTask, EvidenceTaskRequest } from "@/lib/api/domains/material-evidence";
import type { Material } from "@/types";
import { EvidenceReportView } from "@/pages/Workbench/tabs/manuscript-writer/EvidenceReportView";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { MaterialRevisionHistory } from "@/components/materials/MaterialRevisionHistory";
import { MaterialTextDiff } from "@/components/materials/MaterialTextDiff";

type Statuses = Awaited<ReturnType<typeof api.evidence.statuses>>;
type Pair = Awaited<ReturnType<typeof api.evidence.duplicates>>[number];
type Preview = Awaited<ReturnType<typeof api.evidence.preview>>;
type Props = { materials: Material[]; statuses: Statuses; refresh: () => Promise<void> };
export function MaterialSourceTools({ materials, statuses, refresh }: Props) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState("");
  const [question, setQuestion] = useState("");
  const [name, setName] = useState("");
  const [aliases, setAliases] = useState("");
  const [tasks, setTasks] = useState<EvidenceTask[]>([]);
  const [annotations, setAnnotations] = useState<Array<{ id: string; name: string }>>([]);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [pairs, setPairs] = useState<Pair[] | null>(null);
  const [pair, setPair] = useState<Pair | null>(null);
  const [mergeTitle, setMergeTitle] = useState("");
  const [mergeContent, setMergeContent] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const request = useRef<EvidenceTaskRequest | null>(null);
  const intents = useRef(new Map<string, string>());
  const source = statuses.find(status => status.materialId === selected);
  const sourceRevision = source?.revisionId;
  const stableKey = (input: unknown) => { const hash = JSON.stringify(input); let key = intents.current.get(hash); if (!key) { key = crypto.randomUUID(); intents.current.set(hash, key); } return key; };
  const run = async (work: () => Promise<void>) => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError("");
    try { await work(); } catch (e: unknown) { setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { pending.current = false; setBusy(false); }
  };
  useEffect(() => {
    let observing = true; setPreview(null); setTasks([]); setAnnotations([]);
    if (!sourceRevision) return;
    const update = () => void Promise.all([api.evidence.tasks(), api.evidence.annotations(sourceRevision)]).then(([history, confirmed]) => {
      if (observing) { setTasks(history.filter(task => task.kind === "EXTRACT")); setAnnotations(confirmed); }
    }).catch((e: unknown) => { if (observing) setError(e instanceof Error ? e.message : t("errors.loadFailed")); });
    update(); const timer = setInterval(update, 5000);
    return () => { observing = false; clearInterval(timer); };
  }, [sourceRevision, source?.review, t]);
  const showMerge = (candidate: Pair) => {
    setPair(candidate); const first = materials.find(material => material.id === candidate.first); const second = materials.find(material => material.id === candidate.second);
    setMergeTitle(`${first?.title ?? ""} / ${second?.title ?? ""}`);
    setMergeContent(`${first?.content ?? ""}\n\n${second?.content ?? ""}`);
  };
  return <section className="space-y-4 rounded-md border p-4">
    <h2 className="font-semibold">{t("evidence.sourceTools")}</h2>
    {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}
    <Label className="block space-y-1">{t("evidence.selectSource")}<select className="w-full rounded-md border bg-background p-2" value={selected} disabled={busy} onChange={e => setSelected(e.target.value)}><option value="">—</option>{materials.map(material => <option key={material.id} value={material.id}>{material.title}</option>)}</select></Label>
    {source && <p className="text-sm text-muted-foreground">{t("evidence.version", { version: source.version })} · {t("evidence.basicState")}: {t(`evidence.basic.${source.basic}`, { defaultValue: t("evidence.status.UNKNOWN") })} · {t("evidence.semanticState")}: {source.semantic === "NOT_ENABLED" ? t("evidence.semanticOff") : source.semantic.split(",").map(state => t(`evidence.status.${state.split(":")[1]}`, { defaultValue: t("evidence.status.UNKNOWN") })).join(" / ")}</p>}
    {source && <>
      {source.semantic.split(",").filter(state => state.endsWith(":RECONCILIATION_REQUIRED")).map(state => {
        const profile = state.split(":")[0];
        return <div key={profile} className="space-y-2"><p className="text-sm text-muted-foreground">{t("evidence.indexRecoveryHelp")}</p><Button size="sm" variant="outline" disabled={busy} onClick={() => void run(async () => {
          const payload = { expectedVersion: source.version, requestKey: stableKey({ action: "resumeSemantic", revision: source.revisionId, profile, version: source.version }) };
          await api.evidence.resumeSemantic(source.revisionId, profile, payload); await refresh();
        })}>{t("evidence.resumeIndex", { model: profile.includes("flash") ? "Flash" : t("evidence.standard") })}</Button></div>;
      })}
      <MaterialRevisionHistory materialId={selected} currentRevision={source.revisionId} />
      <div className="space-y-2"><Label>{t("evidence.extractionQuestion")}<Input value={question} maxLength={2000} onChange={e => { setQuestion(e.target.value); setPreview(null); }} /></Label>
        <Button size="sm" variant="outline" disabled={busy || !question.trim()} onClick={() => void run(async () => {
          const payload: EvidenceTaskRequest = { kind: "EXTRACT", sourceRevision: source.revisionId, question, expectedVersion: source.version, requestKey: stableKey({ source: source.revisionId, question }) };
          request.current = payload; setPreview(await api.evidence.preview(payload));
        })}>{t("evidence.preview")}</Button>
        {preview && <div className="space-y-2"><p className="text-sm">{t("evidence.cap", { credits: preview.maximumCredits, calls: preview.plannedExternal })}</p>{!preview.available && <p className="text-sm text-muted-foreground">{preview.reason}</p>}
          <Button size="sm" disabled={busy || !preview.available} onClick={() => void run(async () => { if (!request.current) return; await api.evidence.submit(request.current, preview.fingerprint, preview.maximumCredits); setPreview(null); setTasks((await api.evidence.tasks()).filter(task => task.kind === "EXTRACT")); })}>{t("evidence.submit")}</Button></div>}
      </div>
      <details className="space-y-2"><summary className="cursor-pointer text-sm">{t("evidence.manualEntity")}</summary>
        <Label>{t("evidence.entityName")}<Input value={name} maxLength={255} onChange={e => setName(e.target.value)} /></Label><Label>{t("evidence.aliases")}<Input value={aliases} maxLength={2000} onChange={e => setAliases(e.target.value)} /></Label>
        <Button size="sm" disabled={busy || !name.trim()} onClick={() => void run(async () => { const input = { name: name.trim(), aliases: aliases.split(/[,，、]/).map(value => value.trim()).filter(Boolean), materials: { [selected]: source.version } }; await api.evidence.createEntity({ ...input, requestKey: stableKey(input) }); setName(""); setAliases(""); })}>{t("evidence.confirmEntity")}</Button>
      </details>
      {!!annotations.length && <p className="break-words text-sm">{t("evidence.confirmedAnnotations")}: {annotations.map(annotation => annotation.name).join("、")}</p>}
    </>}
    {sourceRevision && tasks.filter(task => task.sourceRevision === sourceRevision).map(task => <details key={task.id} className="rounded-md border p-3"><summary className="cursor-pointer text-sm">{t("evidence.taskKind.EXTRACT")} · {t(`evidence.status.${task.status}`, { defaultValue: t("evidence.status.UNKNOWN") })}</summary>
      {task.stale && <p className="text-sm text-amber-700">{t("evidence.stale")}</p>}
      {task.result != null && <EvidenceReportView task={task} pending={busy} confirm={index => void run(async () => { if (!source) return; await api.evidence.confirm(task.id, index, source.version, stableKey({ task: task.id, index, version: source.version })); setAnnotations(await api.evidence.annotations(source.revisionId)); })} />}
      {task.errorCode && <p className="text-sm text-destructive">{t("evidence.taskError", { code: task.errorCode })}</p>}
      {["QUEUED", "PROCESSING", "WAITING_CREDIT", "RESERVING_CREDIT"].includes(task.status) && <Button size="sm" variant="outline" disabled={busy} onClick={() => void run(async () => { await api.evidence.cancel(task.id); setTasks((await api.evidence.tasks()).filter(row => row.kind === "EXTRACT")); })}>{t("evidence.cancel")}</Button>}
      {["RECONCILIATION_REQUIRED", "CREDIT_RECOVERY_REQUIRED"].includes(task.status) && <Button size="sm" variant="outline" disabled={busy} onClick={() => void run(async () => { await api.evidence.resume(task.id); setTasks((await api.evidence.tasks()).filter(row => row.kind === "EXTRACT")); })}>{t("evidence.resume")}</Button>}
    </details>)}
    <div className="border-t pt-3"><Button size="sm" variant="outline" disabled={busy} onClick={() => void run(async () => setPairs(await api.evidence.duplicates()))}>{t("evidence.findDuplicates")}</Button>
      {pairs?.length === 0 && <p className="mt-2 text-sm text-muted-foreground">{t("evidence.noDuplicates")}</p>}
      {pairs?.map(candidate => <article key={`${candidate.first}:${candidate.second}`} className="space-y-2 border-b py-3">
        <h3 className="break-words text-sm font-medium">{materials.find(material => material.id === candidate.first)?.title} / {materials.find(material => material.id === candidate.second)?.title} · {t(`evidence.duplicate.${candidate.kind}`)}</h3>
        <div className="grid gap-3 sm:grid-cols-2"><pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words text-xs">{candidate.firstText}</pre><pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words text-xs">{candidate.secondText}</pre></div>
        <details><summary className="cursor-pointer text-sm">{t("evidence.rawDiff")}</summary><MaterialTextDiff before={materials.find(material => material.id === candidate.first)?.content ?? candidate.firstText} after={materials.find(material => material.id === candidate.second)?.content ?? candidate.secondText} /></details>
        <Button size="sm" variant="outline" disabled={busy} onClick={() => showMerge(candidate)}>{t("evidence.reviewMerge")}</Button>
      </article>)}
    </div>
    <Dialog open={!!pair} onOpenChange={open => { if (!open && !busy) setPair(null); }}><DialogContent className="max-h-[90dvh] overflow-y-auto"><DialogHeader><DialogTitle>{t("evidence.reviewMerge")}</DialogTitle></DialogHeader><p className="text-sm text-muted-foreground">{t("evidence.mergeHelp")}</p>
      <Label>{t("common.title")}<Input value={mergeTitle} maxLength={255} onChange={e => setMergeTitle(e.target.value)} /></Label><Label>{t("common.content")}<Textarea className="min-h-64" value={mergeContent} onChange={e => setMergeContent(e.target.value)} /></Label>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <DialogFooter><Button variant="outline" disabled={busy} onClick={() => setPair(null)}>{t("common.cancel")}</Button><Button disabled={busy || !mergeTitle.trim() || !mergeContent.trim()} onClick={() => void run(async () => {
        if (!pair) return; const first = statuses.find(status => status.materialId === pair.first); const second = statuses.find(status => status.materialId === pair.second); if (!first || !second) throw new Error(t("errors.loadFailed"));
        const input = { first: pair.first, second: pair.second, firstVersion: first.version, secondVersion: second.version, title: mergeTitle, content: mergeContent };
        await api.evidence.merge({ ...input, requestKey: stableKey(input) }); await refresh(); setPair(null); setPairs(null);
      })}>{t("evidence.confirmMerge")}</Button></DialogFooter>
    </DialogContent></Dialog>
  </section>;
}
