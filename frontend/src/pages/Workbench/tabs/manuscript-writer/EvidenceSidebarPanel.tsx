import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import type { EvidenceHit, EvidenceSettings, EvidenceTask, EvidenceTaskRequest, ReferencePackage } from "@/lib/api/domains/material-evidence";
import { EvidenceReportView } from "./EvidenceReportView";
import { MaterialCitationEditor } from "./MaterialCitationEditor";
import type { Chapter, Manuscript } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type Props = { storyId: string; manuscript?: Manuscript | null; sceneId: string; content: string; dirty: boolean; active: boolean; chapters?: Chapter[] };
export function EvidenceSidebarPanel({ storyId, manuscript, sceneId, content, dirty, active, chapters = [] }: Props) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<EvidenceHit[]>([]);
  const [pack, setPack] = useState<ReferencePackage | null>(null);
  const [pinnedSources, setPinnedSources] = useState<Record<string, EvidenceHit | null>>({});
  const [automatic, setAutomatic] = useState(false);
  const [settings, setSettings] = useState<EvidenceSettings | null>(null);
  const [hintBusy, setHintBusy] = useState(false);
  const [question, setQuestion] = useState("");
  const [bodyVersion, setBodyVersion] = useState("");
  const [branch, setBranch] = useState(manuscript?.currentBranchId ?? "");
  const [branches, setBranches] = useState<Array<{ id: string; name: string }>>([]);
  const [versions, setVersions] = useState<Array<{ id: string; label: string; branchId: string }>>([]);
  const [range, setRange] = useState("current");
  const [chapterSelection,setChapterSelection] = useState<string[]>([]);
  const [tasks, setTasks] = useState<EvidenceTask[]>([]);
  const [focusedReport, setFocusedReport] = useState("");
  const viewScope = useRef(""); viewScope.current = `${manuscript?.id ?? ""}:${sceneId}:${active}`;
  const [citationSource, setCitationSource] = useState<EvidenceHit | null>(null);
  const [citations, setCitations] = useState<Awaited<ReturnType<typeof api.evidence.citations>>>([]);
  const [preview, setPreview] = useState<Awaited<ReturnType<typeof api.evidence.preview>> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const searchControl = useRef<AbortController | null>(null);
  const lastAutomatic = useRef(0);
  const pending = useRef(false);
  const submitIntent = useRef<EvidenceTaskRequest | null>(null);
  const packageIntent = useRef<{ hash: string; key: string } | null>(null);
  const preferencesIntent = useRef<{ hash: string; key: string } | null>(null);
  const hintPending = useRef(false);
  const manuscriptId = manuscript?.id || "";
  useEffect(() => {
    setHits([]); setPack(null); setAutomatic(false); setPreview(null); setBodyVersion(""); setTasks([]); setError(""); setCitationSource(null); setCitations([]); lastAutomatic.current = 0;
  }, [manuscriptId, sceneId]);
  useEffect(() => { setPreview(null); },[manuscript?.version,dirty]);
  useEffect(() => {
    if (!focusedReport) return;
    const report = document.getElementById(`evidence-report-${focusedReport}`) as HTMLDetailsElement | null;
    if (report) { report.open = true; report.scrollIntoView?.({ behavior: "smooth", block: "nearest" }); setFocusedReport(""); }
  }, [focusedReport, tasks]);
  const openLinkedReport = async (id: string) => {
    const scope = viewScope.current;
    try {
      const report = await api.evidence.task(id);
      if (scope !== viewScope.current) return;
      setTasks(rows => rows.some(row => row.id === id) ? rows.map(row => row.id === id ? report : row) : [report, ...rows]); setFocusedReport(id);
    } catch (e: unknown) { if (scope === viewScope.current) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
  };
  useEffect(() => {
    let observing = true;
    if (!active || !manuscriptId || !sceneId) return;
    Promise.all([api.evidence.package(manuscriptId, sceneId), api.evidence.settings(storyId)]).then(([selection, preferences]) => {
      if (observing) { setPack(selection); setSettings(preferences); setAutomatic(preferences.hints); }
    }).catch((e: unknown) => { if (observing) setError(e instanceof Error ? e.message : t("errors.loadFailed")); });
    const refresh = () => void Promise.all([api.evidence.tasks(manuscriptId), api.evidence.citations(manuscriptId)]).then(([rows, links]) => { if (observing) { setTasks(rows); setCitations(links); } }).catch((e: unknown) => { if (observing) setError(e instanceof Error ? e.message : t("errors.loadFailed")); });
    refresh(); const timer = setInterval(refresh, 5000);
    return () => { observing = false; clearInterval(timer); searchControl.current?.abort(); };
  }, [active, manuscriptId, sceneId, storyId, t]);
  useEffect(() => {
    let observing = true;
    if (!active || !pack) return;
    Promise.all(pack.pinned.map(async id => { try { return [id, await api.evidence.open(id)] as const; } catch { return [id, null] as const; } })).then(rows => { if (observing) setPinnedSources(Object.fromEntries(rows)); });
    return () => { observing = false; };
  }, [active, pack]);
  useEffect(() => {
    let observing = true;
    if (!active || !manuscriptId) return;
    Promise.all([api.v2.version.listVersions(manuscriptId), api.v2.version.listBranches(manuscriptId)]).then(([rows, items]) => {
      if (observing) { setVersions(rows.map(row => ({ id: row.id, label: row.label || String(row.versionNumber), branchId: row.branchId }))); setBranches(items.map(item => ({ id: item.id, name: item.name }))); }
    }).catch((e: unknown) => { if (observing) setError(e instanceof Error ? e.message : t("errors.loadFailed")); });
    return () => { observing = false; };
  }, [active, manuscriptId, manuscript?.currentBranchId, manuscript?.version, t]);
  useEffect(() => {
    if (!active || !automatic || !storyId || !content.trim()) return;
    const timer = setTimeout(() => {
      if (Date.now() - lastAutomatic.current < 30000) return;
      lastAutomatic.current = Date.now(); void find(content.replace(/<[^>]*>/g, " ").trim().slice(-1200), true);
    }, 1500);
    return () => clearTimeout(timer);
    // A request captures this scene. Closing the panel or switching scenes aborts it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [content, automatic, active, storyId, sceneId]);
  const find = async (text = query, hint = false) => {
    if (!text.trim() || !storyId) return;
    searchControl.current?.abort(); const controller = new AbortController(); searchControl.current = controller;
    try { const response = hint ? await api.evidence.hints(manuscriptId, sceneId, text, controller.signal) : await api.evidence.search({ storyId, query: text, mode: "fact", scope: "bound", limit: 8 }, controller.signal); if (!controller.signal.aborted) setHits(response.items); }
    catch (e: unknown) { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
  };
  const savePreference = async (field: "hints" | "checks", enabled: boolean) => {
    if (!settings || hintPending.current) return;
    hintPending.current = true;
    setHintBusy(true); if (field === "hints" && !enabled) searchControl.current?.abort();
    const { version, ...preferences } = settings;
    const payload = { ...preferences, [field]: enabled, expectedVersion: version }; const hash = JSON.stringify({ storyId, payload });
    if (preferencesIntent.current?.hash !== hash) preferencesIntent.current = { hash, key: crypto.randomUUID() };
    try { const saved = await api.evidence.saveSettings(storyId, { ...payload, requestKey: preferencesIntent.current.key }); setSettings(saved); setAutomatic(saved.hints); preferencesIntent.current = null; }
    catch (e: unknown) { setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { hintPending.current = false; setHintBusy(false); }
  };
  const savePackage = async () => {
    if (!pack || pending.current) return; pending.current = true; setBusy(true); setError("");
    const input = { expectedVersion: pack.version, pinned: pack.pinned, excluded: pack.excluded };
    const hash = JSON.stringify({ manuscriptId, sceneId, input }); if (packageIntent.current?.hash !== hash) packageIntent.current = { hash, key: crypto.randomUUID() };
    try { setPack(await api.evidence.savePackage(manuscriptId, sceneId, { ...input, requestKey: packageIntent.current.key })); packageIntent.current = null; }
    catch (e: unknown) { setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { pending.current = false; setBusy(false); }
  };
  const costPreview = async () => {
    if (!manuscript || dirty || pending.current || !bodyVersion || !question.trim()) return;
    pending.current = true; setBusy(true); setError("");
    const input: EvidenceTaskRequest = { kind: "CHECK", manuscriptId, branchId: branch || undefined, bodyVersion, scenes: range === "current" ? [sceneId] : range === "whole" ? [] : range === "custom" ? chapters.filter(chapter => chapterSelection.includes(chapter.id)).flatMap(chapter => chapter.scenes.map(scene => scene.id)) : chapters.find(chapter => chapter.id === range)?.scenes.map(scene => scene.id) ?? [], question, expectedVersion: manuscript.version ?? 0, requestKey: crypto.randomUUID() };
    if(range === "custom" && !input.scenes?.length) { pending.current=false; setBusy(false); return; }
    submitIntent.current = input;
    try { setPreview(await api.evidence.preview(input)); } catch (e: unknown) { setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { pending.current = false; setBusy(false); }
  };
  const submit = async () => {
    if (!preview?.available || !submitIntent.current || pending.current || dirty) return;
    pending.current = true; setBusy(true); setError("");
    try { await api.evidence.submit(submitIntent.current, preview.fingerprint, preview.maximumCredits); setPreview(null); setTasks(await api.evidence.tasks(manuscriptId)); }
    catch (e: unknown) { setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { pending.current = false; setBusy(false); }
  };
  const taskAction = async (id: string, action: "cancel" | "resume") => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError("");
    try { await api.evidence[action](id); setTasks(await api.evidence.tasks(manuscriptId)); }
    catch (e: unknown) { setError(e instanceof Error ? e.message : t("errors.loadFailed")); }
    finally { pending.current = false; setBusy(false); }
  };
  if (!manuscript || !sceneId) return null;
  return <section className="min-w-0 space-y-3 border-t pt-5">
    <h3 className="font-semibold">{t("evidence.panel")}</h3>
    {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}
    <form className="flex gap-2" onSubmit={e => { e.preventDefault(); void find(); }}><Input aria-label={t("evidence.query")} value={query} onChange={e => setQuery(e.target.value)} maxLength={2000} /><Button size="sm" type="submit" disabled={!query.trim()}>{t("evidence.explicit")}</Button></form>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={automatic} disabled={hintBusy || !settings} onChange={e => void savePreference("hints", e.target.checked)} />{t("evidence.auto")}</label><p className="text-xs text-muted-foreground">{t("evidence.autoHelp")}</p>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={settings?.checks ?? false} disabled={hintBusy || !settings} onChange={e => void savePreference("checks", e.target.checked)} />{t("evidence.autoCheck")}</label><p className="text-xs text-muted-foreground">{t("evidence.autoCheckHelp")}</p>
    {hits.map(hit => <article key={hit.chunkId} className="space-y-2 border-b pb-3 text-sm"><h4 className="font-medium break-words">{hit.title} · {t("evidence.version", { version: hit.sourceVersion })}</h4><p className="whitespace-pre-wrap break-words">{hit.text}</p><p className="text-xs text-muted-foreground">{t("evidence.position", { start: hit.start, end: hit.end })}</p>
      <div className="flex flex-wrap gap-2"><Button size="sm" variant="outline" disabled={!pack || busy || (pack.pinned.length >= 8 && !pack.pinned.includes(hit.chunkId))} onClick={() => setPack(p => p && ({ ...p, pinned: p.pinned.includes(hit.chunkId) ? p.pinned.filter(id => id !== hit.chunkId) : [...p.pinned, hit.chunkId], excluded: p.excluded.filter(id => id !== hit.chunkId) }))}>{pack?.pinned.includes(hit.chunkId) ? t("evidence.unpin") : t("evidence.pin")}</Button>
        <Button size="sm" variant="outline" disabled={!pack || busy} onClick={() => setPack(p => p && ({ ...p, pinned: p.pinned.filter(id => id !== hit.chunkId), excluded: [...new Set([...p.excluded, hit.chunkId])] }))}>{t("evidence.exclude")}</Button></div>
      <Button size="sm" variant="outline" disabled={busy || dirty} onClick={() => setCitationSource(hit)}>{t("evidence.confirmCitation")}</Button>
    </article>)}
    {pack && <div className="space-y-2">{pack.pinned.map((id, index) => <div key={id} className="flex items-start justify-between gap-2 text-sm"><span className="min-w-0 break-words">{pinnedSources[id]?.title ?? t("evidence.pinnedUnavailable", { number: index + 1 })}</span><Button size="sm" variant="ghost" disabled={busy} onClick={() => setPack(previous => previous && ({ ...previous, pinned: previous.pinned.filter(value => value !== id) }))}>{t("evidence.unpin")}</Button></div>)}
      {!!pack.excluded.length && <Button size="sm" variant="outline" disabled={busy} onClick={() => setPack(previous => previous && ({ ...previous, excluded: [] }))}>{t("evidence.resetExclusions", { count: pack.excluded.length })}</Button>}
      <Button size="sm" variant="outline" disabled={busy} onClick={() => void savePackage()}>{t("evidence.packageSave")} ({pack.pinned.length})</Button>
    </div>}
    {citationSource && <MaterialCitationEditor key={`${sceneId}:${citationSource.chunkId}`} source={citationSource} manuscript={manuscriptId} scene={sceneId} bodyVersion={bodyVersion} disabled={dirty} close={() => setCitationSource(null)} saved={() => { setCitationSource(null); void api.evidence.citations(manuscriptId).then(setCitations).catch((e: unknown) => setError(e instanceof Error ? e.message : t("errors.loadFailed"))); }} />}
    <div className="space-y-3 border-t pt-4"><Label className="space-y-1">{t("evidence.question")}<Input value={question} disabled={busy} maxLength={2000} onChange={e => { setQuestion(e.target.value); setPreview(null); }} /></Label>
      <Label className="block space-y-1">{t("evidence.branch")}<select disabled={busy} className="w-full rounded-md border bg-background p-2" value={branch} onChange={e => { setBranch(e.target.value); setBodyVersion(""); setPreview(null); }}>{branches.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Label>
      <Label className="block space-y-1">{t("evidence.versionSelect")}<select disabled={busy} className="w-full rounded-md border bg-background p-2" value={bodyVersion} onChange={e => { setBodyVersion(e.target.value); setPreview(null); }}><option value="">—</option>{versions.filter(version => version.branchId === branch).map(v => <option key={v.id} value={v.id}>{v.label}</option>)}</select></Label>
      <Label className="block space-y-1">{t("evidence.range")}<select disabled={busy} className="w-full rounded-md border bg-background p-2" value={range} onChange={e => { setRange(e.target.value); setPreview(null); }}><option value="current">{t("evidence.current")}</option><option value="whole">{t("evidence.whole")}</option><option value="custom">{t("evidence.customChapters")}</option>{chapters.map(chapter => <option key={chapter.id} value={chapter.id}>{chapter.title}</option>)}</select></Label>
      {range === "custom" && <fieldset className="max-h-48 space-y-2 overflow-auto"><legend className="mb-2 text-sm">{t("evidence.chooseChapters")}</legend>{chapters.map(chapter => <label key={chapter.id} className="flex items-start gap-2 text-sm"><input type="checkbox" disabled={busy} checked={chapterSelection.includes(chapter.id)} onChange={e => { setChapterSelection(previous => e.target.checked ? [...previous,chapter.id] : previous.filter(id => id !== chapter.id)); setPreview(null); }} /><span className="break-words">{chapter.title}</span></label>)}</fieldset>}
      {dirty && <p className="text-sm text-amber-700">{t("evidence.saveFirst")}</p>}
      <Button size="sm" disabled={busy || dirty || !bodyVersion || !question.trim() || (range === "custom" && !chapterSelection.length)} onClick={() => void costPreview()}>{t("evidence.preview")}</Button>
      {preview && <div className="space-y-2"><p className="text-sm">{t("evidence.cap", { credits: preview.maximumCredits, calls: preview.plannedExternal })}</p>{!preview.available && <p className="text-sm text-muted-foreground">{preview.reason || t("evidence.unavailable")}</p>}<Button size="sm" disabled={!preview.available || busy || dirty} onClick={() => void submit()}>{t("evidence.submit")}</Button></div>}
    </div>
    <details className="space-y-2"><summary className="cursor-pointer font-medium">{t("evidence.citationHistory")}</summary>{citations.filter(link => link.scene_id === sceneId).map(link => <article key={link.id} className="space-y-1 border-b py-2 text-sm"><p>{t(`evidence.relation.${link.relation_type}`)} · {t(`evidence.link.${link.state}`)}</p><p className="whitespace-pre-wrap break-words">{link.quote ?? t("evidence.sourceRevoked")}</p>{link.report_task_id && <Button size="sm" variant="link" onClick={() => void openLinkedReport(link.report_task_id!)}>{t("evidence.openLinkedReport")}</Button>}</article>)}</details>
    <h4 className="font-medium">{t("evidence.history")}</h4><p className="text-xs text-muted-foreground">{t("evidence.candidate")}</p>
    {!tasks.length && <p className="text-sm text-muted-foreground">{t("evidence.emptyHistory")}</p>}
    {tasks.map(task => <details key={task.id} id={`evidence-report-${task.id}`} className="min-w-0 rounded-md border p-3"><summary className="cursor-pointer break-words text-sm">{t(`evidence.taskKind.${task.kind}`, { defaultValue: t("evidence.panel") })} · {t(`evidence.status.${task.status}`, { defaultValue: t("evidence.status.UNKNOWN") })}</summary>{task.stale && <p className="mt-2 text-sm text-amber-700">{t("evidence.stale")}</p>}{task.errorCode && <p role="alert" className="text-sm text-destructive">{t("evidence.taskError", { code: task.errorCode })}</p>}
      {task.result != null && <EvidenceReportView task={task} />}
      {["QUEUED", "RECOVERY_QUEUED", "PROCESSING", "WAITING_CREDIT", "RESERVING_CREDIT", "CREDIT_RECOVERY_REQUIRED"].includes(task.status) && <Button size="sm" variant="outline" disabled={busy} onClick={() => void taskAction(task.id, "cancel")}>{t("evidence.cancel")}</Button>}
      {["RECONCILIATION_REQUIRED", "CREDIT_RECOVERY_REQUIRED"].includes(task.status) && <Button size="sm" variant="outline" disabled={busy} onClick={() => void taskAction(task.id, "resume")}>{t("evidence.resume")}</Button>}
    </details>)}
  </section>;
}
