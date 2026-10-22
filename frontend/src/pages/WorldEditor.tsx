import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useBlocker, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, isApiError } from "@/lib/api-client";
import { refreshTrackedAiOperation, retryTrackedAiOperation, runTrackedAiOperation, useTrackedAiOperation } from "@/lib/ai-operation-store";
import { WORLD_UPDATED, type WorldUpdatedDetail } from "@/lib/world-operation-events";
import type { AiOperationProgress, WorldGenerationStatus, WorldModuleDefinition } from "@/types";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Loader2, ArrowLeft, Save, UploadCloud } from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import { localizedErrorMessage, localizedOperationFailure } from "@/lib/error-messages";
import WorldMetadataForm from "./WorldBuilder/components/WorldMetadataForm";
import WorldModuleEditor from "./WorldBuilder/components/WorldModuleEditor";
import { WorldRefineReview, type WorldRefineTarget } from "./WorldBuilder/components/WorldRefineReview";
import { WorldConflictReview } from "./WorldBuilder/components/WorldConflictReview";
import { WorldGeneratedResultsDialog } from "./WorldBuilder/components/WorldGeneratedResultsDialog";
import { useWorldDraft } from "./WorldBuilder/useWorldDraft";
import { useAuth } from "@/contexts/auth-state";

const runningStates = ["QUEUED", "RUNNING", "STREAMING"];

const WorldEditor = () => {
  const [params] = useSearchParams();
  const id = params.get("id") || "";
  const { toast } = useToast();
  const { t } = useTranslation();
  const { refreshProfile } = useAuth();
  const editor = useWorldDraft(id);
  const { draft, dirty, saving, remote, conflict, refresh } = editor;
  const latest = useRef({ id, editor }); latest.current = { id, editor };
  const epoch = useRef(0);
  const [definitions, setDefinitions] = useState<WorldModuleDefinition[] | null>(null);
  const [definitionError, setDefinitionError] = useState("");
  const [definitionAttempt, setDefinitionAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const actionLock = useRef(false);
  const [actionError, setActionError] = useState("");
  const [reviewRemote, setReviewRemote] = useState(false);
  const [reviewGenerated, setReviewGenerated] = useState(false);
  const [endedTaskId, setEndedTaskId] = useState<string | null>(null);
  const [checkingTask, setCheckingTask] = useState(true);
  const [taskLookupError, setTaskLookupError] = useState(false);
  const [target, setTarget] = useState<WorldRefineTarget | null>(null);
  const [generation, setGeneration] = useState<WorldGenerationStatus | null>(null);
  const [localTask, setLocalTask] = useState<AiOperationProgress | null>(null);
  const taskUpdateVersion = useRef(0);
  const tracked = useTrackedAiOperation();
  const task = tracked?.scopeType === "WORLD" && tracked.scopeId === id ? tracked : localTask?.scopeId === id ? localTask : null;
  const running = !!task && runningStates.includes(task.status);
  const recoverable = !!task && task.id !== endedTaskId && ["FAILED", "RECOVERY_REQUIRED"].includes(task.status);
  const taskOutdated = task?.errorCode === "WORLD_VERSION_CONFLICT" || task?.errorCode === "WORLD_TASK_BASELINE_REQUIRED";

  useEffect(() => {
    let cancelled = false;
    setDefinitionError("");
    api.worlds.getDefinitions().then(value => { if (!cancelled) setDefinitions(value); })
      .catch(error => { if (!cancelled) setDefinitionError(localizedErrorMessage(error)); });
    return () => { cancelled = true; };
  }, [definitionAttempt]);

  const refreshStatus = useCallback(async () => {
    if (!id) return;
    const scope = epoch.current;
    try {
      const status = await api.worlds.generationStatus(id);
      if (scope === epoch.current && latest.current.id === id) setGeneration(status);
    } catch (error) { if (scope === epoch.current && latest.current.id === id) setActionError(localizedErrorMessage(error)); }
  }, [id]);

  const checkActiveTask = useCallback(async () => {
    if (!id || latest.current.id !== id) return;
    const scope = epoch.current, taskVersion = ++taskUpdateVersion.current;
    const isCurrent = () => scope === epoch.current && latest.current.id === id && taskVersion === taskUpdateVersion.current;
    setCheckingTask(true); setTaskLookupError(false);
    try {
      const operation = await api.aiOperations.active("WORLD", id);
      if (isCurrent()) setLocalTask(operation);
    } catch (error) {
      if (isCurrent() && !(isApiError(error) && error.status === 404)) {
        setTaskLookupError(true); setActionError(localizedErrorMessage(error));
      }
    } finally { if (isCurrent()) setCheckingTask(false); }
  }, [id]);

  useEffect(() => {
    const lifecycle = epoch;
    epoch.current++;
    setTarget(null); setLocalTask(null); setGeneration(null); setActionError(""); setBusy(false); setReviewRemote(false); setReviewGenerated(false); setEndedTaskId(null); actionLock.current = false;
    void refreshStatus();
    void checkActiveTask();
    return () => { lifecycle.current++; };
  }, [id, refreshStatus, checkActiveTask]);

  useEffect(() => {
    if (!id) return;
    const changed = (event: Event) => {
      if ((event as CustomEvent<WorldUpdatedDetail>).detail.worldId !== id) return;
      void refresh(); void refreshStatus();
    };
    window.addEventListener(WORLD_UPDATED, changed);
    return () => window.removeEventListener(WORLD_UPDATED, changed);
  }, [id, refresh, refreshStatus]);

  useEffect(() => {
    if (tracked?.scopeType === "WORLD" && tracked.scopeId === id) { taskUpdateVersion.current++; setLocalTask(tracked); setCheckingTask(false); setTaskLookupError(false); }
  }, [tracked, id]);

  // A reopened editor can find a server-side task even when this tab did not start it.
  useEffect(() => {
    if (!localTask || !runningStates.includes(localTask.status) || tracked?.id === localTask.id) return;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      void api.aiOperations.get(localTask.id).then(operation => {
        if (cancelled) return;
        setLocalTask(operation);
        if (operation.status === "SUCCEEDED") { void refresh(); void refreshStatus(); }
      }).catch(error => { if (!cancelled) setActionError(localizedErrorMessage(error)); });
    }, 3000);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [localTask, tracked?.id, refresh, refreshStatus]);

  const blocker = useBlocker(({ currentLocation, nextLocation }) => dirty &&
    (currentLocation.pathname !== nextLocation.pathname || new URLSearchParams(nextLocation.search).get("id") !== id));
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const ready = () => {
    if (!latest.current.editor.canAct()) { setActionError(t("worlds.saveBeforeAction")); return false; }
    if (checkingTask || taskLookupError) { setActionError(t("worlds.checkTaskFirst")); return false; }
    if (running || recoverable) { setActionError(t("worlds.recoverBeforeNew")); return false; }
    return true;
  };
  const run = async (work: () => Promise<void>) => {
    if (actionLock.current) return;
    actionLock.current = true; setBusy(true); setActionError("");
    const scope = epoch.current;
    try { await work(); }
    catch (error) { if (scope === epoch.current && !(error instanceof Error && error.message === "AI_QUOTE_CANCELLED")) setActionError(localizedErrorMessage(error)); }
    finally { if (scope === epoch.current) { actionLock.current = false; setBusy(false); void refreshStatus(); } }
  };
  const updateField = (moduleKey: string, fieldKey: string, value: string) => editor.edit(current => {
    const modules = current.modules.some(module => module.key === moduleKey)
      ? current.modules.map(module => module.key === moduleKey ? { ...module, fields: { ...module.fields, [fieldKey]: value } } : module)
      : [...current.modules, { key: moduleKey, fields: { [fieldKey]: value } }];
    return { ...current, modules };
  });
  const save = async () => { const clean = await editor.save(); if (clean) toast({ title: t("worlds.saved") }); return clean; };
  const refineStale = !!target && (target.worldId !== id || target.revision !== draft?.revision || dirty || !!remote || conflict || draft?.modules.find(module => module.key === target.moduleKey)?.fields[target.fieldKey] !== target.original);

  if (!id) return <div className="p-6"><p>{t("worlds.missingId")}</p><Button asChild className="mt-4"><Link to="/worlds">{t("worlds.backToList")}</Link></Button></div>;
  return <div className="min-h-screen bg-background flex flex-col">
    <header className="border-b flex flex-wrap items-center justify-between gap-3 p-4 lg:px-8 bg-background/95 backdrop-blur sticky top-0 z-10">
      <div className="flex min-w-0 items-center gap-3"><Button asChild variant="ghost" size="icon"><Link to="/worlds" aria-label={t("worlds.backToList")}><ArrowLeft className="h-4 w-4" /></Link></Button><span className="font-semibold break-words">{draft?.name || t("worlds.editorTitle")}</span></div>
      <div className="flex flex-wrap items-center gap-2">
        <span role="status" className="text-xs text-muted-foreground">{saving ? t("common.saving") : dirty ? t("worlds.unsaved") : draft ? t("worlds.saved") : ""}</span>
        <Button variant="outline" onClick={() => void save()} disabled={saving || busy || !draft || !!remote || conflict}><Save className="mr-2 h-4 w-4" />{t("common.save")}</Button>
        <Button disabled={!draft || saving || busy || running} onClick={() => { if (!ready()) return; void run(async () => {
          const revision = draft!.revision, scope = epoch.current;
          const preview = await api.worlds.publishPreview(id);
          if (scope !== epoch.current) return;
          if (!latest.current.editor.canAct() || latest.current.editor.draft?.revision !== revision) { setActionError(t("worlds.actionStale")); return; }
          const modules: string[] = preview.modulesToGenerate || [];
          const names = modules.map(key => definitions?.find(module => module.key === key)?.label || key);
          if (!confirm(modules.length ? t("worlds.publishNeeds", { count: modules.length, modules: names.join("、") }) : t("worlds.publishReady"))) return;
          if (modules.length) await runTrackedAiOperation(api.worlds.startPublish(id));
          else { await api.worlds.publish(id); if (scope === epoch.current) await refresh(); }
          if (scope === epoch.current) toast({ title: t("worlds.publishDone") });
        }); }}><UploadCloud className="mr-2 h-4 w-4" />{t("worlds.publishCheck")}</Button>
      </div>
    </header>
    <main className="flex-1 p-4 md:p-6 space-y-6">
      {!!editor.error && <div role="alert" className="space-y-2 text-destructive"><p>{conflict ? t("worlds.saveConflict") : localizedErrorMessage(editor.error)}</p><Button variant="outline" onClick={() => void refresh()}>{t("common.retry")}</Button></div>}
      {actionError && <p role="alert" className="text-sm text-destructive">{actionError}</p>}
      {checkingTask && <p role="status" className="text-sm text-muted-foreground">{t("worlds.checkingTask")}</p>}
      {taskLookupError && <Button variant="outline" onClick={() => void checkActiveTask()}>{t("worlds.refreshTask")}</Button>}
      {remote && <div role="status" className="rounded border p-3 space-y-2"><p>{t("worlds.remoteChanged")}</p><Button variant="outline" onClick={() => setReviewRemote(true)}>{t("worlds.compareServer")}</Button></div>}
      {draft && <p className="text-sm text-muted-foreground">{t(`worlds.status.${draft.status}`)} · v{draft.version}</p>}
      {task && <section aria-label={t("worlds.task")} className="rounded border p-3 space-y-2 text-sm">
        <p>{t("worlds.task")} · {t(`worlds.taskStatus.${task.status}`)} · {task.completedSteps}/{task.totalSteps}</p>
        <details><summary>{t("worlds.taskDetails")}</summary><p className="break-all">{task.id}</p></details>
        {task.errorCode && <p className="text-destructive">{taskOutdated ? t(task.errorCode === "WORLD_VERSION_CONFLICT" ? "worlds.taskConflict" : "worlds.taskBaselineMissing") : localizedOperationFailure(task.errorCode)}</p>}
        {recoverable && <p>{taskOutdated ? t("worlds.endOutdatedTaskHelp") : t("worlds.recoverBeforeNew")}</p>}
        {endedTaskId === task.id && <p>{t("worlds.taskEnded")}</p>}
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setReviewGenerated(true)}>{t("worlds.viewGenerated")}</Button>
          {recoverable && !taskOutdated && <Button disabled={busy || dirty || !!remote || conflict} variant="outline" onClick={() => void run(() => retryTrackedAiOperation(task.id))}>{t("worlds.recoverTask")}</Button>}
          {recoverable && task.status === "FAILED" && <Button disabled={busy} variant="outline" onClick={() => { if (confirm(t("worlds.endTaskConfirm"))) { setEndedTaskId(task.id); setActionError(""); } }}>{t("worlds.endTask")}</Button>}
          {running && <Button variant="outline" disabled={busy} onClick={() => void run(async () => {
            if (tracked?.id === task.id) await refreshTrackedAiOperation(task.id);
            else {
              const scope = epoch.current;
              const operation = await api.aiOperations.get(task.id);
              if (scope !== epoch.current || latest.current.id !== id) return;
              taskUpdateVersion.current++; setLocalTask(operation);
              if (operation.status === "SUCCEEDED") await refresh();
            }
          })}>{t("worlds.refreshTask")}</Button>}
        </div>
      </section>}
      {!!generation?.queue.length && <details className="text-sm"><summary>{t("worlds.moduleState")}</summary><ul className="mt-2 space-y-1">{generation.queue.map(module => <li key={module.moduleKey}>{definitions?.find(def => def.key === module.moduleKey)?.label || module.moduleKey} · {t(`worlds.moduleStatus.${module.status}`, { defaultValue: module.status })}{module.error && <p className="text-destructive">{module.error}</p>}</li>)}</ul></details>}
      {editor.loading && <div className="flex items-center gap-2"><Loader2 className="h-5 w-5 animate-spin" />{t("common.loading")}</div>}
      {draft && <WorldMetadataForm data={draft} onChange={(field, value) => editor.edit(current => ({ ...current, [field]: value }))} />}
      {definitionError ? <div role="alert" className="space-y-2"><p>{t("worlds.definitionsFailed")} {definitionError}</p><Button variant="outline" onClick={() => setDefinitionAttempt(value => value + 1)}>{t("common.retry")}</Button></div> : definitions === null ? <p>{t("worlds.definitionsLoading")}</p> : draft && <WorldModuleEditor key={id} worldId={id} definitions={definitions} moduleData={draft.modules} busy={busy || running || saving} onUpdateModule={updateField}
        onAutoGenerate={moduleKey => { if (ready()) void run(async () => { await runTrackedAiOperation(api.worlds.startGenerateModule(id, moduleKey)); }); }}
        onRefineField={(moduleKey, fieldKey, original) => { if (ready()) setTarget({ worldId: id, revision: draft.revision, moduleKey, fieldKey, original, label: definitions.find(module => module.key === moduleKey)?.fields.find(field => field.key === fieldKey)?.label || fieldKey }); }} />}
    </main>
    <Dialog open={blocker.state === "blocked"} onOpenChange={open => { if (!open && blocker.state === "blocked") blocker.reset(); }}><DialogContent><DialogHeader><DialogTitle>{t("worlds.unsaved")}</DialogTitle><DialogDescription>{t("worlds.leaveHelp")}</DialogDescription></DialogHeader><DialogFooter className="gap-2"><Button variant="outline" onClick={() => { if (blocker.state === "blocked") blocker.reset(); }}>{t("common.cancel")}</Button><Button variant="outline" disabled={saving} onClick={() => { if (blocker.state === "blocked") blocker.proceed(); }}>{t("worlds.discardAndLeave")}</Button><Button disabled={saving || !!remote || conflict} onClick={async () => { if (await save() && blocker.state === "blocked") blocker.proceed(); }}>{t("worlds.saveAndLeave")}</Button></DialogFooter></DialogContent></Dialog>
    {draft && remote && <WorldConflictReview local={draft} server={remote} definitions={definitions || []} open={reviewRemote} onOpenChange={setReviewRemote} onResolve={keepLocal => { editor.resolveRemote(keepLocal); setReviewRemote(false); }} />}
    {task && <WorldGeneratedResultsDialog worldId={id} operationId={task.id} definitions={definitions || []} open={reviewGenerated} onOpenChange={setReviewGenerated} />}
    {target && <WorldRefineReview key={`${target.worldId}:${target.moduleKey}:${target.fieldKey}`} target={target} stale={refineStale} onClose={() => setTarget(null)} onGenerate={async instruction => {
      if (!latest.current.editor.canAct() || latest.current.id !== target.worldId || latest.current.editor.draft?.revision !== target.revision) throw new Error(t("worlds.refineStale"));
      const result = await api.worlds.refineField(id, target.moduleKey, target.fieldKey, target.original, instruction);
      void refreshProfile();
      const text = result?.result || result?.content || result?.resultText;
      if (typeof text !== "string" || !text.trim()) throw new Error("WORLD_EMPTY_CANDIDATE");
      return text;
    }} onAccept={text => { if (!refineStale) { updateField(target.moduleKey, target.fieldKey, text); setTarget(null); } }} />}
  </div>;
};

export default WorldEditor;
