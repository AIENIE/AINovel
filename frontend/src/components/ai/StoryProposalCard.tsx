import { useContext, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { api, isApiError } from "@/lib/api-client";
import type { StoryAssistantCandidate, StoryAssistantProposal } from "@/lib/api/domains/story-assistant";
import { localizedErrorMessage } from "@/lib/error-messages";
import { notifyStoryResourceChanged } from "@/lib/story-assistant-events";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { StoryDiscussionWorkspaceContext, type StoryProposalDraft } from "./story-discussion-context";
import { StoryDiscussionSources } from "./StoryDiscussionSources";

type Field = { path: string[]; value: unknown };
const characterFields = ["name", "role", "archetype", "synopsis", "details", "relationships"];
const planningFields = ["purpose", "informationRelease", "twistRole", "selectedTwistId", "revealFocus", "tensionShift", "minHan", "maxHan", "sceneType", "goal", "conflict", "infoRelease", "foreshadowId", "revealFor", "foreshadowHint", "misdirectionAction", "revealTrigger", "payoffPlan", "memeUsage"];
const text = (value: unknown) => value == null ? "" : typeof value === "object" ? JSON.stringify(value) : String(value);
const readPath = (source: unknown, path: string[]): unknown => path.reduce<unknown>((value, key) => value && typeof value === "object" ? (value as Record<string, unknown>)[key] : undefined, source);
function fieldsFor(candidate: StoryAssistantCandidate, kind: StoryAssistantProposal["resourceType"], prefix: string[] = []): Field[] {
  if (kind === "CHARACTER") return characterFields.map(key => ({ path: [key], value: candidate[key] }));
  const fields: Field[] = ["title", "summary"].map(key => ({ path: [...prefix, key], value: candidate[key] }));
  const planning = candidate.planning && typeof candidate.planning === "object" ? candidate.planning as Record<string, unknown> : {};
  for (const key of planningFields) if (key in planning) fields.push({ path: [...prefix, "planning", key], value: planning[key] });
  if (Array.isArray(candidate.scenes)) candidate.scenes.forEach((scene, index) => fields.push(...fieldsFor(scene as StoryAssistantCandidate, "SCENE", [...prefix, "scenes", String(index)])));
  return fields;
}
type Receipt = { action: "edit" | "accept" | "reject" | "undo"; key: string; revision: number; resourceRevision: number | null; candidate?: StoryAssistantCandidate };

export function StoryProposalCard({ storyId, proposalId, working, onRefine }: { storyId: string; proposalId: string; working: boolean; onRefine: (proposal: StoryAssistantProposal) => void }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const workspace = useContext(StoryDiscussionWorkspaceContext);
  const queryKey = ["story-proposal", storyId, proposalId];
  const query = useQuery({ queryKey, queryFn: ({ signal }) => api.storyAssistant.proposal(storyId, proposalId, signal), retry: false, staleTime: 0 });
  const proposal = query.data;
  const draftKey = `${storyId}:${proposalId}`;
  const [draft, setDraft] = useState<StoryProposalDraft | null>(() => workspace?.proposalDrafts?.get(draftKey) || null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState<Receipt | null>(null);
  const lock = useRef(false);
  useEffect(() => { if (proposal && !draft) setDraft({ revision: proposal.revision, candidate: structuredClone(proposal.candidate) }); }, [proposal, draft]);
  useEffect(() => { if (draft) workspace?.proposalDrafts?.set(draftKey, draft); }, [draft, draftKey, workspace?.proposalDrafts]);
  const candidate = useMemo(() => draft?.candidate || proposal?.candidate || {}, [draft?.candidate, proposal?.candidate]);
  const fields = useMemo(() => proposal ? fieldsFor(candidate, proposal.resourceType) : [], [candidate, proposal]);
  if (query.error) return <div role="alert" className="text-sm text-destructive">{localizedErrorMessage(query.error)}<Button size="sm" variant="outline" onClick={() => void query.refetch()}>{t("common.retry")}</Button></div>;
  if (!proposal || !draft) return <p className="text-sm">{t("assistant.loading")}</p>;
  const changed = JSON.stringify(candidate) !== JSON.stringify(proposal.candidate);
  const candidateStale = draft.revision !== proposal.revision;
  const editorDirty = workspace?.dirtyTargets.has(proposal.resourceType === "CHARACTER" ? `CHARACTER:${proposal.targetId}` : `OUTLINE:${proposal.outlineId}`) || workspace?.dirtyTargets.has(`STORY:${storyId}`);
  const unavailable = working || saving || !!pending;
  const actionable = !unavailable && !editorDirty && !candidateStale && proposal.applicable;
  const label = (path: string[]) => `${path[0] === "scenes" ? `${t("storyAssistant.resource.SCENE")} ${Number(path[1]) + 1} · ` : ""}${t(`storyAssistant.field.${path[path.length - 1]}`, { defaultValue: path[path.length - 1] })}`;
  const update = (path: string[], value: string) => {
    setDraft(previous => {
      if (!previous) return previous;
      const next = structuredClone(previous.candidate);
      let current: Record<string, unknown> = next;
      for (const key of path.slice(0, -1)) current = current[key] as Record<string, unknown>;
      const key = path[path.length - 1];
      current[key] = ["minHan", "maxHan"].includes(key) ? value === "" ? null : Number(value) : value;
      return { ...previous, candidate: next };
    });
  };
  const execute = async (receipt: Receipt) => {
    if (lock.current) return;
    lock.current = true; setSaving(true); setError("");
    try {
      const updated = receipt.action === "edit"
        ? await api.storyAssistant.editProposal(storyId, proposal.id, receipt.revision, receipt.candidate!, receipt.key)
        : (await api.storyAssistant.decide(storyId, proposal.id, receipt.action, receipt.revision, receipt.resourceRevision, receipt.key)).proposal;
      setPending(null);
      setDraft({ revision: updated.revision, candidate: structuredClone(updated.candidate) });
      client.setQueryData(queryKey, updated);
      if (receipt.action === "accept" || receipt.action === "undo") {
        notifyStoryResourceChanged({ storyId, resourceType: updated.resourceType, targetId: updated.targetId, outlineId: updated.outlineId });
        await client.invalidateQueries({ queryKey: ["workbench"] });
      }
      await client.invalidateQueries({ queryKey: ["story-proposal", storyId] });
    } catch (failure) {
      setError(localizedErrorMessage(failure));
      const terminal = isApiError(failure) && failure.status >= 400 && failure.status < 500 && failure.status !== 408;
      setPending(terminal ? null : receipt);
      if (terminal) await query.refetch();
    } finally { lock.current = false; setSaving(false); }
  };
  const act = (action: Receipt["action"]) => execute({ action, key: crypto.randomUUID(), revision: proposal.revision, resourceRevision: action === "reject" ? null : proposal.resourceRevision, ...(action === "edit" ? { candidate: structuredClone(candidate) } : {}) });
  const params = new URLSearchParams({ storyId, tab: proposal.resourceType === "CHARACTER" ? "stories" : "outline" });
  if (proposal.outlineId) params.set("outlineId", proposal.outlineId);
  if (proposal.targetId) params.set(proposal.resourceType === "CHARACTER" ? "characterId" : proposal.resourceType === "CHAPTER" ? "chapterId" : "sceneId", proposal.targetId);
  return <section aria-label={t("storyAssistant.proposal")} className="min-w-0 space-y-3 rounded border p-3 text-sm">
    <div className="flex flex-wrap items-center gap-2"><strong className="break-words">{text(candidate.name || candidate.title)}</strong><span className="text-xs">{t(`storyAssistant.action.${proposal.action}`)} · {t(`storyAssistant.resource.${proposal.resourceType}`)} · {t(`assistant.proposal.${proposal.status}`)}</span></div>
    {proposal.rationale && <p className="whitespace-pre-wrap">{proposal.rationale}</p>}
    <p className="text-xs text-muted-foreground">{t(proposal.resourceType === "CHARACTER" ? "storyAssistant.sharedCharacter" : "storyAssistant.sharedOutline")}</p>
    {proposal.action === "CREATE" && <p className="text-xs">{t(proposal.resourceType === "CHAPTER" ? "storyAssistant.chapterGroup" : "storyAssistant.appendOnly")}</p>}
    {editorDirty && <p role="status" className="text-xs text-amber-700">{t("storyAssistant.targetDirty")}</p>}
    {((proposal.status === "READY" && !proposal.applicable) || candidateStale) && <div className="space-y-2 text-xs text-amber-700"><p>{t("storyAssistant.stale")}</p>{proposal.staleReason && <p>{t(`storyAssistant.reason.${proposal.staleReason}`, { defaultValue: proposal.staleReason })}</p>}<details><summary>{t("storyAssistant.current")}</summary><dl className="space-y-2 mt-2">{fields.map(field => <div key={field.path.join(".")}><dt className="font-medium">{label(field.path)}</dt><dd className="whitespace-pre-wrap">{text(readPath(proposal.current, field.path)) || "—"}</dd></div>)}</dl></details></div>}
    <details open><summary className="cursor-pointer font-medium">{t("storyAssistant.fieldDiff")}</summary><div className="mt-2 space-y-3">{fields.filter(field => text(readPath(proposal.before, field.path)) !== text(field.value)).map(field => <div key={field.path.join(".")} className="space-y-1"><p className="text-xs font-medium">{label(field.path)}</p><p className="whitespace-pre-wrap break-words border-l-2 border-red-300 pl-2 text-muted-foreground"><span className="sr-only">{t("storyAssistant.before")}: </span>{text(readPath(proposal.before, field.path)) || t("storyAssistant.empty")}</p><p className="whitespace-pre-wrap break-words border-l-2 border-green-500 pl-2"><span className="sr-only">{t("storyAssistant.after")}: </span>{text(field.value) || t("storyAssistant.empty")}</p></div>)}</div></details>
    <details><summary className="cursor-pointer">{t(proposal.status === "READY" ? "storyAssistant.editCandidate" : "storyAssistant.fullCandidate")}</summary><div className="mt-3 space-y-3">{fields.map(field => <label key={field.path.join(".")} className="block text-xs space-y-1"><span>{label(field.path)}</span>{["minHan", "maxHan"].includes(field.path[field.path.length - 1] || "") ? <Input aria-label={label(field.path)} type="number" value={text(field.value)} disabled={proposal.status !== "READY" || unavailable || candidateStale} onChange={event => update(field.path, event.target.value)} /> : <Textarea aria-label={label(field.path)} value={text(field.value)} disabled={proposal.status !== "READY" || unavailable || candidateStale} onChange={event => update(field.path, event.target.value)} rows={field.path[field.path.length - 1] === "name" || field.path[field.path.length - 1] === "title" ? 1 : 3} />}</label>)}</div></details>
    <StoryDiscussionSources sources={proposal.sources} />
    {changed && <p className="text-xs">{t("storyAssistant.candidateUnsaved")}</p>}
    {proposal.status === "READY" && !changed && <p className="text-xs text-muted-foreground">{t("storyAssistant.keepProposal")}</p>}
    {error && <p role="alert" className="text-xs text-destructive">{error}</p>}
    {pending && <div className="text-xs space-y-2"><p>{t("assistant.receiptPending")}</p><Button size="sm" variant="outline" disabled={saving} onClick={() => void execute(pending)}>{t("assistant.checkSave")}</Button></div>}
    <div className="flex flex-wrap gap-2">
      {proposal.status === "READY" && <><Button size="sm" disabled={!actionable || changed} onClick={() => void act("accept")}>{t("assistant.accept")}</Button><Button size="sm" variant="outline" disabled={unavailable || candidateStale} onClick={() => void act("reject")}>{t("assistant.reject")}</Button>{changed && <Button size="sm" variant="secondary" disabled={unavailable || candidateStale} onClick={() => void act("edit")}>{t("storyAssistant.saveCandidate")}</Button>}</>}
      {proposal.action === "UPDATE" && proposal.status === "ACCEPTED" && <Button size="sm" variant="outline" disabled={unavailable || editorDirty || candidateStale || !proposal.undoAllowed} onClick={() => void act("undo")}>{t("assistant.undo")}</Button>}
      {proposal.status === "READY" && <Button size="sm" variant="outline" disabled={unavailable || changed || editorDirty} onClick={() => onRefine(proposal)}>{t("assistant.refine")}</Button>}
      {(changed || candidateStale) && <Button size="sm" variant="ghost" disabled={unavailable} onClick={() => { if (!changed || window.confirm(t("storyAssistant.discardEdit"))) setDraft({ revision: proposal.revision, candidate: structuredClone(proposal.candidate) }); }}>{t("storyAssistant.reloadCandidate")}</Button>}
      {proposal.targetId && (proposal.action === "UPDATE" || proposal.status === "ACCEPTED") && <Button asChild size="sm" variant="ghost"><Link to={`/workbench?${params}`}>{t("storyAssistant.openTarget")}</Link></Button>}
    </div>
    {proposal.action === "CREATE" && proposal.status === "ACCEPTED" && <p className="text-xs text-muted-foreground">{t("storyAssistant.createNoUndo")}</p>}
    {proposal.action === "UPDATE" && proposal.status === "ACCEPTED" && !proposal.undoAllowed && <p className="text-xs text-muted-foreground">{t("storyAssistant.undoUnavailable")}</p>}
  </section>;
}
