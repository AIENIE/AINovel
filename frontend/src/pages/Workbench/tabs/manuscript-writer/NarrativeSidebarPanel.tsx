import { useCallback, useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TabsContent } from "@/components/ui/tabs";
import { api, isApiError } from "@/lib/api-client";
import { localizedErrorMessage } from "@/lib/error-messages";
import { useAuth } from "@/contexts/auth-state";
import type { Manuscript } from "@/types";
import type { NarrativeAssertion, NarrativeEvidence, NarrativeExtraction, NarrativeReview, NarrativeSource, NarrativeState } from "@/types/narrative";
import { NarrativeAssertionEditor } from "./NarrativeAssertionEditor";
import { NarrativeEvidenceDialog } from "./NarrativeEvidenceDialog";

const running = (status: string) => ["QUEUED", "RUNNING", "STREAMING"].includes(status);
const blankAssertion = (): NarrativeAssertion => ({
  subject: "", statement: "", kind: "FACT", characterId: null, holderCharacterId: null,
  worldTime: null, uncertainty: "", evidence: [{ blockId: "", quote: "" }], supersedesId: null,
});
type Characters = Array<{ id: string; name: string }>;
type Props = {
  active: boolean; manuscript: Manuscript | null | undefined; sceneId: string; dirty: boolean;
  busy: boolean; structureKey: string; characters: Characters; onManuscript: (manuscript: Manuscript) => void;
};

export function NarrativeSidebarPanel({ active, manuscript, sceneId, dirty, busy, structureKey, characters, onManuscript }: Props) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const client = useQueryClient();
  const [error, setError] = useState("");
  const [working, setWorking] = useState(false);
  const [view, setView] = useState("pending");
  const [selection, setSelection] = useState("");
  const [source, setSource] = useState<NarrativeSource | null>(null);
  const [highlight, setHighlight] = useState<NarrativeEvidence[]>([]);
  const approvalKey = useRef<{ signature: string; key: string } | null>(null);
  const manuscriptId = manuscript?.id || "";
  const branchId = manuscript?.currentBranchId || "";
  const queryKey = ["narrative", manuscriptId, branchId] as const;
  const state = useQuery({
    queryKey,
    queryFn: () => api.narrative.state(manuscriptId, branchId),
    enabled: active && !!manuscriptId && !!branchId,
    retry: false,
    refetchInterval: query => active && query.state.data?.extractions.some(e => running(e.status)) ? 2000 : false,
  });
  useEffect(() => {
    if (active && branchId) void state.refetch();
  // Refetch after saved text or outline topology changes; never reset a draft on polling.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, manuscript?.version, structureKey, branchId]);
  useEffect(() => {
    setSelection(""); setSource(null); setError(""); approvalKey.current = null;
  }, [manuscriptId, branchId, sceneId]);
  const refresh = useCallback(async () => {
    await client.invalidateQueries({ queryKey: ["narrative", manuscriptId, branchId] });
  }, [client, manuscriptId, branchId]);
  const batches = state.data?.extractions.filter(e => e.sceneId === sceneId) || [];
  const selected = batches.find(e => e.id === selection) || batches.find(e => !e.reviewed) || batches[0];
  const loading = state.isFetching && !state.data;
  const locked = dirty || busy || working || loading;

  const initialize = async () => {
    setWorking(true); setError("");
    try {
      await api.v2.version.listVersions(manuscriptId);
      onManuscript(await api.manuscripts.get(manuscriptId));
    } catch (e) { setError(localizedErrorMessage(e)); }
    finally { setWorking(false); }
  };
  const approve = async () => {
    if (!manuscript || !state.data || locked) return;
    setWorking(true); setError("");
    const request = { sceneId, expectedManuscriptVersion: manuscript.version, expectedCanonRevision: state.data.canonRevision };
    const signature = JSON.stringify([manuscriptId, branchId, request]);
    if (approvalKey.current?.signature !== signature) approvalKey.current = { signature, key: crypto.randomUUID() };
    try {
      const result = await api.narrative.approve(manuscriptId, branchId, request, approvalKey.current.key);
      setSelection(result.extractionId); setView("pending"); approvalKey.current = null;
      await refresh();
    } catch (e) { setError(localizedErrorMessage(e)); await refresh(); }
    finally { setWorking(false); }
  };
  const openEvidence = async (approvalId: string, evidence: NarrativeEvidence[]) => {
    setError("");
    try {
      const result = await api.narrative.evidence(manuscriptId, branchId, approvalId);
      setSource(result); setHighlight(evidence);
    } catch (e) { setError(localizedErrorMessage(e)); }
  };
  const taskAction = async (retry: boolean) => {
    if (!selected?.operationId) return;
    setWorking(true); setError("");
    try {
      if (retry) await api.aiOperations.retry(selected.operationId);
      else await api.aiOperations.cancel(selected.operationId);
      await refresh();
    } catch (e) { setError(localizedErrorMessage(e)); }
    finally { setWorking(false); }
  };
  const visibleRecords = (state.data?.records || []).filter(r => view === "stale" ? r.status === "STALE" : r.status === "CONFIRMED");
  return <TabsContent value="narrative" className="m-0 mt-2 flex-1 min-h-0 overflow-y-auto p-3 space-y-3">
    <p className="text-sm text-muted-foreground">{t("narrative.intro")}</p>
    {!manuscriptId || !sceneId ? <p>{t("narrative.selectScene")}</p> : <>
      {!branchId ? <Button disabled={working || dirty || busy} onClick={() => void initialize()}>{t("narrative.initialize")}</Button> : <>
        <div className="flex flex-wrap gap-2 items-center">
          <Button size="sm" className="h-auto whitespace-normal text-left" disabled={locked || !state.data || batches.some(e => running(e.status))} onClick={() => void approve()}>{t("narrative.approve")}</Button>
          <Button size="sm" variant="outline" disabled={working} onClick={() => void refresh()}>{t("narrative.refresh")}</Button>
        </div>
        {dirty && <p className="text-sm">{t("narrative.saveFirst")}</p>}
        {state.data && <p className="text-xs text-muted-foreground">{t("narrative.revision", { revision: state.data.canonRevision })}</p>}
        {loading && <p role="status">{t("narrative.loading")}</p>}
        <div className="flex flex-wrap gap-1" aria-label={t("narrative.views")}>
          {["pending", "confirmed", "stale"].map(tab => <Button key={tab} size="sm" variant={view === tab ? "secondary" : "ghost"} aria-pressed={view === tab} onClick={() => setView(tab)}>{t("narrative." + tab)}</Button>)}
        </div>
        {view === "pending" ? <>
          {!batches.length && !loading && <p className="text-sm">{t("narrative.notAnalyzed")}</p>}
          {batches.length > 0 && <label className="block text-xs space-y-1">{t("narrative.extractions")}
            <select className="w-full rounded border bg-background p-2 text-sm" value={selected?.id || ""} onChange={e => setSelection(e.target.value)}>
              {batches.map((batch, index) => <option key={batch.id} value={batch.id}>{batches.length - index} · {t("narrative.status." + (batch.reviewed ? "REVIEWED" : batch.status))}</option>)}
            </select>
          </label>}
          {selected && <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{t("narrative.status." + (selected.reviewed ? "REVIEWED" : selected.status))}</Badge>
              <Button size="sm" variant="outline" onClick={() => void openEvidence(selected.approvalId, [])}>{t("narrative.evidence")}</Button>
            </div>
            {selected.usage && <p className="text-xs text-muted-foreground">{t("narrative.usage", { input: selected.usage.inputTokens, output: selected.usage.outputTokens, cost: selected.usage.cost })}</p>}
            {selected.stale && <p role="status" className="rounded border p-2 text-sm">{t("narrative.staleHint")}</p>}
            {selected.error && <p role="alert">{t("narrative.invalidOutput")}</p>}
            {running(selected.status) && <Button size="sm" variant="outline" disabled={working} onClick={() => void taskAction(false)}>{t("narrative.cancel")}</Button>}
            {!selected.stale && ["FAILED", "RECOVERY_REQUIRED"].includes(selected.status) && <Button size="sm" disabled={working} onClick={() => void taskAction(true)}>{t("narrative.retry")}</Button>}
            {selected.status === "READY" && state.data && manuscript && <NarrativeReviewForm
              key={selected.id} extraction={selected} state={state.data} manuscript={manuscript}
              characters={characters} disabled={locked || selected.stale || selected.reviewed}
              storageKey={["ainovel.narrative.review", user?.id, manuscriptId, branchId, selected.id].join(":")}
              refresh={refresh} openEvidence={openEvidence} />}
          </div>}
        </> : <>
          <p className="text-xs text-muted-foreground">{t("narrative.wholeBranch")}</p>
          {!visibleRecords.length && <p className="text-sm">{t(view === "stale" ? "narrative.noStale" : "narrative.noRecords")}</p>}
          {visibleRecords.map(record => <article key={record.id} className="rounded border p-3 space-y-2 text-sm">
            <div className="flex flex-wrap gap-2"><Badge variant="outline">{t("narrative.kind." + record.assertion.kind)}</Badge><span>{record.assertion.subject}</span></div>
            <p className="whitespace-pre-wrap break-words">{record.assertion.statement}</p>
            <p className="text-xs text-muted-foreground">{record.disclosedAt.chapterTitle} · {record.disclosedAt.sceneTitle}</p>
            {record.assertion.uncertainty && <p className="text-xs">{record.assertion.uncertainty}</p>}
            <Button size="sm" variant="outline" onClick={() => void openEvidence(record.approvalId, record.assertion.evidence)}>{t("narrative.evidence")}</Button>
          </article>)}
        </>}
      </>}
    </>}
    {(error || state.error) && <p role="alert" className="text-sm text-destructive break-words">{error || localizedErrorMessage(state.error)}</p>}
    <NarrativeEvidenceDialog source={source} evidence={highlight} onClose={() => setSource(null)} />
  </TabsContent>;
}

type ReviewDraft = { decisions: Record<string, "ACCEPT" | "REJECT">; edited: Record<string, NarrativeAssertion>; additions: NarrativeAssertion[]; key: string; pendingRequest?: NarrativeReview };
function loadDraft(key: string): ReviewDraft {
  try {
    const data = JSON.parse(sessionStorage.getItem(key) || "null");
    if (data?.decisions && data?.edited && Array.isArray(data?.additions) && typeof data.key === "string") return data;
  } catch { /* Session storage is optional; the server remains authoritative. */ }
  return { decisions: {}, edited: {}, additions: [], key: crypto.randomUUID() };
}
function NarrativeReviewForm({ extraction, state, manuscript, characters, disabled, storageKey, refresh, openEvidence }: {
  extraction: NarrativeExtraction; state: NarrativeState; manuscript: Manuscript; characters: Characters;
  disabled: boolean; storageKey: string; refresh: () => Promise<void>;
  openEvidence: (approvalId: string, evidence: NarrativeEvidence[]) => Promise<void>;
}) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(() => loadDraft(storageKey));
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState("");
  const source = useQuery({ queryKey: ["narrative-source", manuscript.id, manuscript.currentBranchId, extraction.approvalId],
    queryFn: () => api.narrative.evidence(manuscript.id, manuscript.currentBranchId!, extraction.approvalId), retry: false });
  useEffect(() => {
    try {
      if (submitted || extraction.reviewed) sessionStorage.removeItem(storageKey);
      else sessionStorage.setItem(storageKey, JSON.stringify(draft));
    } catch { /* Private browsing may disable storage. */ }
  }, [draft, storageKey, submitted, extraction.reviewed]);
  const update = (patch: Partial<ReviewDraft>) => setDraft(previous => ({ ...previous, ...patch, key: crypto.randomUUID(), pendingRequest: undefined }));
  const allDecided = extraction.candidates.every(c => draft.decisions[c.id]);
  const submit = async () => {
    setSubmitting(true); setError("");
    const request: NarrativeReview = draft.pendingRequest || {
      expectedManuscriptVersion: manuscript.version, expectedCanonRevision: state.canonRevision,
      decisions: extraction.candidates.map(candidate => ({ candidateId: candidate.id, decision: draft.decisions[candidate.id], edited: draft.edited[candidate.id] || null })),
      additions: draft.additions,
    };
    setDraft(previous => ({ ...previous, pendingRequest: request }));
    try {
      await api.narrative.review(manuscript.id, manuscript.currentBranchId!, extraction.id, request, draft.key);
      setSubmitted(true); await refresh();
    } catch (e) {
      if (isApiError(e) && e.status >= 400 && e.status < 500) update({ pendingRequest: undefined });
      setError(localizedErrorMessage(e)); await refresh();
    }
    finally { setSubmitting(false); }
  };
  const locked = disabled || submitting || submitted;
  if (extraction.reviewed || submitted) return <div className="space-y-2">
    <p role="status">{t("narrative.reviewedHint")}</p>
    {extraction.candidates.map(c => <div key={c.id} className="rounded border p-2 text-sm">
      <p>{extraction.review?.decisions.find(d => d.candidateId === c.id)?.edited?.statement || c.assertion?.statement || t("narrative.invalidCandidate")}</p>
      <p className="text-xs text-muted-foreground">{t("narrative.decision." + (extraction.review?.decisions.find(d => d.candidateId === c.id)?.decision || "REJECT"))}</p>
    </div>)}
  </div>;
  return <div className="space-y-3">
    {!extraction.candidates.length && <p className="text-sm">{t("narrative.noChanges")}</p>}
    {extraction.candidates.length > 0 && <div className="flex flex-wrap gap-2">
      {(["ACCEPT", "REJECT"] as const).map(decision => <Button key={decision} variant="outline" size="sm" disabled={locked} onClick={() => update({ decisions: Object.fromEntries(extraction.candidates.map(c => [c.id, decision])) })}>{t("narrative.all." + decision)}</Button>)}
    </div>}
    {extraction.candidates.map((candidate, index) => <article key={candidate.id} className="rounded border p-3 space-y-2">
      <p className="text-xs text-muted-foreground">{t("narrative.candidate", { number: index + 1 })}</p>
      {candidate.validationError && <p className="text-sm text-destructive">{t("narrative.invalidCandidate")}</p>}
      <p className="text-sm whitespace-pre-wrap break-words">{candidate.assertion?.statement}</p>
      {candidate.assertion && <p className="text-xs">{t("narrative.kind." + candidate.assertion.kind)} · {candidate.assertion.uncertainty || t("narrative.authorCheck")}</p>}
      <Button size="sm" variant="ghost" onClick={() => void openEvidence(extraction.approvalId, candidate.assertion?.evidence || [])}>{t("narrative.evidence")}</Button>
      <label className="block text-xs">{t("narrative.decision")}
        <select className="w-full rounded border bg-background p-2 text-sm" disabled={locked} value={draft.decisions[candidate.id] || ""} onChange={e => update({ decisions: { ...draft.decisions, [candidate.id]: e.target.value as "ACCEPT" | "REJECT" } })}>
          <option value="">{t("narrative.undecided")}</option>
          <option value="ACCEPT">{t("narrative.decision.ACCEPT")}</option>
          <option value="REJECT">{t("narrative.decision.REJECT")}</option>
        </select>
      </label>
      <details>
        <summary className="cursor-pointer text-sm">{t("narrative.editCandidate")}</summary>
        <NarrativeAssertionEditor value={draft.edited[candidate.id] || candidate.assertion || blankAssertion()}
          onChange={value => update({ edited: { ...draft.edited, [candidate.id]: value } })}
          characters={characters} records={state.records} source={source.data} disabled={locked} />
      </details>
    </article>)}
    {draft.additions.map((addition, index) => <article key={index} className="rounded border p-3 space-y-2">
      <NarrativeAssertionEditor value={addition} onChange={value => update({ additions: draft.additions.map((v, i) => i === index ? value : v) })} characters={characters} records={state.records} source={source.data} disabled={locked} />
      <Button size="sm" variant="ghost" disabled={locked} onClick={() => update({ additions: draft.additions.filter((_, i) => i !== index) })}>{t("narrative.removeAddition")}</Button>
    </article>)}
    <Button size="sm" variant="outline" disabled={locked || draft.additions.length >= 100} onClick={() => update({ additions: [...draft.additions, blankAssertion()] })}>{t("narrative.addRecord")}</Button>
    <p className="text-xs text-muted-foreground">{t("narrative.reviewHint")}</p>
    <Button size="sm" disabled={locked || !allDecided || !source.data} onClick={() => void submit()}>{t("narrative.submit")}</Button>
    {(error || source.error) && <p role="alert" className="text-sm text-destructive">{error || localizedErrorMessage(source.error)}</p>}
  </div>;
}
