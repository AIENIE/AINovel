import { WorldSources } from '@/components/ai/WorldContextPreview';
import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { api } from "@/lib/api-client";
import { localizedErrorMessage } from "@/lib/error-messages";
import { materialTextDiff } from "@/lib/material-text-diff";
import { SCENE_CANDIDATES_EVENT, type SceneGenerationOutcome } from "@/lib/scene-candidates";
import type { Manuscript } from "@/types";
import { stripHtml } from "./shared";

const candidateText = (html: string) => stripHtml(html.replace(/<\/p>/gi, "\n\n").replace(/<br\s*\/?>/gi, "\n"));

type Props = {
  manuscript?: Manuscript | null; sceneId: string; dirty: boolean; busy: boolean; revision: number;
  onApplied: (manuscript: Manuscript, sceneId: string) => void;
  onLoadDraft: (html: string) => void; onRegenerate: () => Promise<void>;
};
export function SceneCandidatesPanel({ manuscript, sceneId, dirty, busy, revision, onApplied, onLoadDraft, onRegenerate }: Props) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState("");
  const [compare, setCompare] = useState("");
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const id = manuscript?.id || "", branch = manuscript?.currentBranchId || "";
  const identity = `${id}:${branch}:${sceneId}`;
  const latest = useRef({ identity, dirty }); latest.current = { identity, dirty };
  const list = useQuery({ queryKey: ["scene-candidates", identity, revision],
    queryFn: async () => (await api.manuscripts.listSceneCandidates(id, sceneId, branch)).filter(candidate => candidate.sourceKind !== "AGENT"), enabled: open && !!id && !!branch && !!sceneId, retry: false });
  const candidate = list.data?.find(c => c.id === selected);
  const detail = useQuery({ queryKey: ["scene-candidate", identity, selected],
    queryFn: () => api.manuscripts.sceneCandidate(id, sceneId, branch, selected), enabled: open && !!candidate, retry: false });
  const comparison = useQuery({ queryKey: ["scene-candidate", identity, compare],
    queryFn: () => api.manuscripts.sceneCandidate(id, sceneId, branch, compare), enabled: open && !!compare, retry: false });
  const usage = useMemo(() => {
    if (!detail.data?.sourceFingerprint) return { data: null, error: false };
    try {
      const data: unknown = JSON.parse(detail.data.sourceFingerprint);
      if (!data || typeof data !== "object" || Array.isArray(data)) return { data: null, error: true };
      return { data: data as Record<string, unknown>, error: false };
    } catch { return { data: null, error: true }; }
  }, [detail.data?.sourceFingerprint]);
  const changes = useMemo(() => comparison.data && detail.data
    ? materialTextDiff(candidateText(comparison.data.content), candidateText(detail.data.content)) : [], [comparison.data, detail.data]);
  useEffect(() => { setSelected(""); setCompare(""); setError(""); }, [identity]);
  const refreshCandidates = list.refetch;
  useEffect(() => {
    const listener = (event: Event) => {
      const result = (event as CustomEvent<SceneGenerationOutcome>).detail;
      if (result?.manuscriptId === id && result.sceneId === sceneId) {
        setOpen(true); if (branch) void refreshCandidates();
        if (result.outcome === "SAVED") void api.manuscripts.get(id).then(saved => {
          if (latest.current.identity === identity && !latest.current.dirty) onApplied(saved, sceneId);
        }).catch(e => setError(localizedErrorMessage(e)));
      }
    };
    window.addEventListener(SCENE_CANDIDATES_EVENT, listener);
    return () => window.removeEventListener(SCENE_CANDIDATES_EVENT, listener);
  }, [id, branch, sceneId, identity, onApplied, refreshCandidates]);
  const historical = candidate?.sourceKind !== "GENERATED";
  const outside = candidate?.actualHan != null && candidate.minHan != null && candidate.maxHan != null
    && (candidate.actualHan < candidate.minHan || candidate.actualHan > candidate.maxHan);
  const act = async (action: () => Promise<void>) => {
    setWorking(true); setError("");
    try { await action(); } catch (e) { if (latest.current.identity === identity) setError(localizedErrorMessage(e)); }
    finally { setWorking(false); void list.refetch(); }
  };
  const disabled = busy || working || dirty;
  return <Dialog open={open} onOpenChange={value => { setOpen(value); if (value) void list.refetch(); }}>
    <DialogTrigger asChild><Button size="sm" variant="outline" disabled={!id || !sceneId || !branch}>{t("sceneCandidates.title")}</Button></DialogTrigger>
    <DialogContent className="flex max-h-[92dvh] w-[96vw] max-w-5xl flex-col overflow-hidden">
      <DialogHeader><DialogTitle>{t("sceneCandidates.title")}</DialogTitle><DialogDescription>{t("sceneCandidates.help")}</DialogDescription></DialogHeader>
      <div className="min-h-0 space-y-4 overflow-y-auto pr-1">
        <div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={() => void list.refetch()}>{t("common.refresh")}</Button>
          <Button variant="outline" size="sm" disabled={disabled} onClick={() => {
            if (window.confirm(t("sceneCandidates.regenerateCost"))) void act(async () => { setOpen(false); await onRegenerate(); });
          }}>{t("sceneCandidates.regenerate")}</Button></div>
        {dirty && <p role="status" className="text-sm text-amber-700">{t("sceneCandidates.saveFirst")}</p>}
        {(error || list.error || detail.error || comparison.error) && <p role="alert" className="text-sm text-destructive">{error || localizedErrorMessage(list.error || detail.error || comparison.error)}</p>}
        {list.isFetching && <p>{t("common.loading")}</p>}
        {list.data?.length === 0 && <p>{t("sceneCandidates.empty")}</p>}
        <div className="grid gap-2 sm:grid-cols-2">{list.data?.map(c => <button type="button" key={c.id}
          className={`min-w-0 rounded border p-3 text-left text-sm ${selected === c.id ? "border-primary bg-muted" : ""}`}
          onClick={() => { setSelected(c.id); setCompare(""); setError(""); }}>
          <span className="block font-medium">{c.sourceKind === "RECOVERED" ? t("sceneCandidates.recovered") : c.generationSequence === 1 ? t("sceneCandidates.initial") : t("sceneCandidates.revision")}
            {c.sourceKind === "RECOVERED" ? ` ${c.generationSequence}` : ""} · {new Date(c.createdAt).toLocaleString()}</span>
          <span>{t("sceneCandidates.count", { actual: c.actualHan ?? "?", min: c.minHan ?? "?", max: c.maxHan ?? "?" })}</span>
          {c.actualHan != null && c.minHan != null && c.maxHan != null && <span className="block">{t("sceneCandidates.delta", { delta: Math.floor((c.minHan + c.maxHan) / 2) - c.actualHan })}</span>}
          {c.status === "APPLIED" && <span className="block">{t("sceneCandidates.adopted")}</span>}
        </button>)}</div>
        {candidate && detail.data && <section className="space-y-3">
          {detail.data.worldContext&&<details><summary>{t("world4.actual")}</summary><WorldSources selection={detail.data.worldContext}/></details>}
          {usage.error && <p role="alert" className="text-sm text-destructive">{t("errors.loadFailed")}</p>}
          {historical && <p className="text-sm">{t("sceneCandidates.incompleteSource")}</p>}
          {historical && typeof usage.data?.completion_tokens === "number" && <p className="text-sm">{t("sceneCandidates.historicalUsage", { input: usage.data?.prompt_tokens, output: usage.data?.completion_tokens, credits: usage.data?.settled_amount })}</p>}
          {outside && <p className="text-sm font-medium text-amber-700">{t("sceneCandidates.outside")}</p>}
          <p className="text-xs text-muted-foreground">{t("sceneCandidates.quality")}: {candidate.qualityStatus === "PROCESSED" ? t("sceneCandidates.processed") : t("sceneCandidates.unchecked")}</p>
          <div className="flex flex-wrap gap-2">
            {historical ? <Button disabled={disabled} onClick={() => { onLoadDraft(detail.data.content); setOpen(false); }}>{t("sceneCandidates.loadDraft")}</Button>
              : <Button disabled={disabled || candidate.manuscriptVersion !== manuscript?.version} onClick={() => void act(async () => {
                if (!manuscript || (outside && !window.confirm(t("sceneCandidates.adoptOutside")))) return;
                const saved = await api.manuscripts.adoptSceneCandidate(id, sceneId, selected, manuscript.version, branch);
                if (latest.current.identity === identity && !latest.current.dirty) onApplied(saved, sceneId);
              })}>{t("sceneCandidates.adopt")}</Button>}
            <Button variant="outline" onClick={() => setOpen(false)}>{t("sceneCandidates.defer")}</Button>
          </div>
          {!historical && candidate.manuscriptVersion !== manuscript?.version && candidate.status !== "APPLIED" && <p className="text-sm">{t("sceneCandidates.stale")}</p>}
          <pre className="whitespace-pre-wrap break-words rounded border p-4 font-sans text-sm leading-7">{candidateText(detail.data.content)}</pre>
          <label className="block text-sm">{t("sceneCandidates.compare")}
            <select className="mt-1 block w-full rounded border bg-background p-2" value={compare} onChange={e => setCompare(e.target.value)}>
              <option value="">{t("sceneCandidates.chooseCompare")}</option>
              {list.data?.filter(c => c.id !== selected).map(c => <option key={c.id} value={c.id}>{new Date(c.createdAt).toLocaleString()} · {c.generationSequence} · {c.actualHan}</option>)}
            </select>
          </label>
          {!!compare && <div className="space-y-2">{changes.map((change, index) => <div key={index} className={`whitespace-pre-wrap break-words rounded border p-3 text-sm ${change.kind === "removed" ? "bg-red-50 text-red-950" : change.kind === "added" ? "bg-green-50 text-green-950" : ""}`}>
            <p className="text-xs">{t(`evidence.diff.${change.kind}`)}</p>{change.text}</div>)}</div>}
        </section>}
      </div>
    </DialogContent>
  </Dialog>;
}
