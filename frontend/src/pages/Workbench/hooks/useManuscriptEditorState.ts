import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api, isApiError } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import { countWords, stripHtml } from "@/pages/Workbench/tabs/manuscript-writer/shared";
import { useWritingSession } from "./useWritingSession";
import { t } from "@/i18n";
import { localizedErrorMessage } from "@/lib/error-messages";
import { AssistantInteractionError, sameAssistantDocument } from "@/lib/assistant-editor";

type ToastFn = (options: { description?: string; title?: string; variant?: "default" | "destructive" }) => void;
type Options = {
  autoSaveIntervalSeconds?: number | null;
  replaceManuscript: (manuscript: Manuscript) => void;
  selectedManuscript?: Manuscript | null;
  selectedManuscriptId: string;
  selectedSceneId: string;
  selectedStoryId: string;
  toast: ToastFn;
};
type DraftScope = {
  manuscript?: Manuscript | null;
  drafts: Record<string, string>;
  dirty: Record<string, boolean>;
  conflict: boolean;
  saving: number;
  exclusive?: boolean;
  manualOnly?: Record<string, boolean>;
  lastSavedAt: string;
};
const scopeKey = (id: string, branch?: string | null) => JSON.stringify([id, branch || ""]);
const emptyScope = (): DraftScope => ({ drafts: {}, dirty: {}, conflict: false, saving: 0, lastSavedAt: "" });

export function useManuscriptEditorState({ autoSaveIntervalSeconds, replaceManuscript, selectedManuscript,
  selectedManuscriptId, selectedSceneId, selectedStoryId, toast }: Options) {
  // Scope identity is resolved during render: an effect cannot protect the first frame after a switch.
  const scopes = useRef(new Map<string, DraftScope>());
  const queues = useRef(new Map<string, Promise<unknown>>());
  const timers = useRef(new Map<string, number>());
  const [, render] = useState(0);
  const refresh = useCallback(() => render(value => value + 1), []);
  const key = scopeKey(selectedManuscriptId, selectedManuscript?.currentBranchId);
  const activeKey = useRef(key);
  activeKey.current = key;
  const activeScene = useRef(selectedSceneId);
  activeScene.current = selectedSceneId;
  const scope = scopes.current.get(key) ?? emptyScope();
  scopes.current.set(key, scope);
  if (selectedManuscript && selectedManuscript.id === selectedManuscriptId &&
      selectedManuscript.version >= (scope.manuscript?.version ?? -1)) scope.manuscript = selectedManuscript;
  const sceneDrafts = scope.drafts;
  const dirtyScenes = scope.dirty;
  const content = selectedSceneId ? sceneDrafts[selectedSceneId] ?? scope.manuscript?.sections[selectedSceneId] ?? "" : "";
  const hydrated = useRef("");
  const currentWordCount = useMemo(() => countWords(stripHtml(content)), [content]);
  const measureSceneWords = useCallback((html: string) => countWords(stripHtml(html)), []);
  const { primeSceneHtml, recordSceneHtml, sessionDurationSeconds, sessionNetWords } = useWritingSession({
    selectedStoryId, selectedManuscriptId, selectedSceneId,
    selectedSceneDirty: Boolean(dirtyScenes[selectedSceneId]), autoSaveIntervalSeconds, measureHtmlWords: measureSceneWords,
  });
  const stopTimer = useCallback((targetKey: string, sceneId: string) => {
    const timerKey = JSON.stringify([targetKey, sceneId]);
    const timer = timers.current.get(timerKey);
    if (timer !== undefined) window.clearTimeout(timer);
    timers.current.delete(timerKey);
  }, []);
  useEffect(() => {
    const pending = timers.current;
    return () => { pending.forEach(timer => window.clearTimeout(timer)); pending.clear(); };
  }, []);
  useEffect(() => {
    hydrated.current = scope.manuscript && (!scope.manuscript.partial || scope.manuscript.sections[selectedSceneId] !== undefined || scope.dirty[selectedSceneId]) ? JSON.stringify([key, selectedSceneId]) : "";
    if (scope.manuscript && selectedSceneId) primeSceneHtml(selectedSceneId, content);
  }, [key, selectedSceneId, scope.manuscript, scope.dirty, content, primeSceneHtml]);

  const applyFetchedManuscript = useCallback((manuscript: Manuscript) => {
    const targetKey = scopeKey(manuscript.id, manuscript.currentBranchId);
    const target = scopes.current.get(targetKey) ?? emptyScope();
    Object.keys(target.drafts).forEach(sceneId => stopTimer(targetKey, sceneId));
    target.manuscript = manuscript;
    target.drafts = { ...manuscript.sections };
    target.dirty = {};
    target.conflict = false;
    scopes.current.set(targetKey, target);
    replaceManuscript(manuscript);
    refresh();
  }, [refresh, replaceManuscript, stopTimer]);

  const applyServerSection = useCallback((manuscript: Manuscript, sceneId: string) => {
    const html = manuscript.sections?.[sceneId];
    if (typeof html !== "string") throw new Error(t("editorState.noServerSection"));
    const targetKey = scopeKey(manuscript.id, manuscript.currentBranchId);
    const target = scopes.current.get(targetKey) ?? emptyScope();
    stopTimer(targetKey, sceneId);
    target.manuscript = manuscript;
    if (target.dirty[sceneId]) {
      // Keep edits made while generation/adoption was in flight; only advance the server baseline.
      scopes.current.set(targetKey, target);
      if (activeKey.current === targetKey) replaceManuscript(manuscript);
      refresh();
      return;
    }
    target.drafts = { ...target.drafts, [sceneId]: html };
    target.dirty = { ...target.dirty };
    delete target.dirty[sceneId];
    scopes.current.set(targetKey, target);
    if (activeKey.current === targetKey) {
      replaceManuscript(manuscript);
      primeSceneHtml(sceneId, html);
    }
    refresh();
  }, [primeSceneHtml, refresh, replaceManuscript, stopTimer]);

  const saveSnapshot = useCallback(async (target: DraftScope, targetKey: string, manuscriptId: string, sceneId: string, html: string) => {
    const manuscript = target.manuscript;
    if (!manuscript || manuscript.id !== manuscriptId) throw new Error(t("editorState.noServerSection"));
    const saved = manuscript.currentBranchId
      ? await api.manuscripts.saveSection(manuscriptId, sceneId, html, manuscript.version, manuscript.currentBranchId)
      : await api.manuscripts.saveSection(manuscriptId, sceneId, html, manuscript.version);
    if (saved.id !== manuscriptId || scopeKey(saved.id, saved.currentBranchId) !== targetKey) throw new AssistantInteractionError(t("assistant.identityChanged"));
    const merged = { ...manuscript, ...saved, sections: { ...manuscript.sections, ...saved.sections } };
    target.manuscript = merged;
    if (target.drafts[sceneId] === undefined || target.drafts[sceneId] === html) {
      target.drafts = { ...target.drafts, [sceneId]: saved.sections?.[sceneId] ?? html };
      target.dirty = { ...target.dirty, [sceneId]: false };
      if (target.manualOnly) delete target.manualOnly[sceneId];
    }
    target.conflict = false;
    target.lastSavedAt = new Date().toLocaleTimeString();
    if (activeKey.current === targetKey) replaceManuscript(merged);
    refresh();
    return merged;
  }, [refresh, replaceManuscript]);

  const persistSection = useCallback(async (sceneId: string, html: string, silent = false): Promise<void> => {
    if (!selectedManuscriptId || !sceneId) return;
    const target = scopes.current.get(key);
    if (!target) return;
    if (target.exclusive) {
      await queues.current.get(selectedManuscriptId)?.catch(() => undefined);
      // A timer for another scene may expire while adoption holds the shared queue.
      // Resume only the still-current dirty draft, never the pre-adoption body.
      if (!target.exclusive && target.dirty[sceneId] && target.drafts[sceneId] === html) return persistSection(sceneId, html, silent);
      return;
    }
    stopTimer(key, sceneId);
    const save = async () => {
      if ((target.conflict || target.manualOnly?.[sceneId]) && silent) return;
      target.saving++;
      refresh();
      try {
        await saveSnapshot(target, scopeKey(selectedManuscriptId, target.manuscript?.currentBranchId), selectedManuscriptId, sceneId, html);
        if (activeKey.current === key && !silent) toast({ title: t("editorState.saved") });
      } catch (error: unknown) {
        if (isApiError(error) && error.status === 409) target.conflict = true;
        toast({ variant: "destructive", title: silent ? t("editorState.autoSaveFailed") : t("editorState.saveFailed"),
          description: isApiError(error) && error.status === 409
            ? "服务端正文或分支已变化，本地草稿已保留。请重新载入并比较后保存。"
            : localizedErrorMessage(error) });
      } finally { target.saving--; refresh(); }
    };
    // One queue per manuscript prevents competing version updates, without coupling unrelated manuscripts.
    const queued = (queues.current.get(selectedManuscriptId) ?? Promise.resolve()).then(save, save);
    queues.current.set(selectedManuscriptId, queued);
    await queued;
    if (queues.current.get(selectedManuscriptId) === queued) queues.current.delete(selectedManuscriptId);
  }, [key, refresh, saveSnapshot, selectedManuscriptId, stopTimer, toast]);

  /** Share the manuscript write queue with autosave; errors are real receipts, never swallowed. */
  const performAssistantWrite = useCallback(async (expectedHtml: string, action: (current: Manuscript) => Promise<Manuscript>, saveBaseline: boolean, reconcile?: { expectedServerHtml: string }) => {
    const target = scopes.current.get(key);
    const sceneId = selectedSceneId;
    if (!target?.manuscript || !sceneId || target.exclusive) throw new AssistantInteractionError(t("assistant.writeBusy"));
    const assertLocal = () => {
      if (activeKey.current !== key || activeScene.current !== sceneId || (!reconcile && !sameAssistantDocument(target.drafts[sceneId] ?? target.manuscript!.sections[sceneId] ?? "", expectedHtml))) throw new AssistantInteractionError(t("assistant.localChanged"));
    };
    assertLocal();
    target.exclusive = true;
    stopTimer(key, sceneId);
    refresh();
    let baselineSaved = false;
    const write = async () => {
      assertLocal();
      try {
        if (saveBaseline && (target.dirty[sceneId] || !sameAssistantDocument(target.manuscript!.sections[sceneId] ?? "", expectedHtml))) {
          await saveSnapshot(target, key, selectedManuscriptId, sceneId, expectedHtml);
          baselineSaved = true;
        }
        assertLocal();
        const current = target.manuscript!;
        const result = await action(current);
        if (scopeKey(result.id, result.currentBranchId) !== key) throw new AssistantInteractionError(t("assistant.identityChanged"));
        if (result.version < target.manuscript!.version) throw new AssistantInteractionError(t("assistant.receiptOlder"));
        const localHtml = target.drafts[sceneId] ?? target.manuscript!.sections[sceneId] ?? "";
        const applied = !reconcile || (activeKey.current === key && activeScene.current === sceneId && sameAssistantDocument(localHtml, expectedHtml)
          && sameAssistantDocument(result.sections[sceneId] ?? "", reconcile.expectedServerHtml));
        target.manuscript = result;
        target.drafts = { ...target.drafts, [sceneId]: applied ? result.sections[sceneId] ?? expectedHtml : localHtml };
        target.dirty = { ...target.dirty, [sceneId]: !applied && !sameAssistantDocument(localHtml, result.sections[sceneId] ?? "") };
        target.conflict = false;
        if (activeKey.current === key) replaceManuscript(result);
        return { manuscript: result, applied };
      } catch (error) {
        if (isApiError(error) && error.status === 409) target.conflict = true;
        if (baselineSaved) throw new AssistantInteractionError(`${t("assistant.baselineSaved")} ${localizedErrorMessage(error)}`);
        throw error;
      }
    };
    const queued = (queues.current.get(selectedManuscriptId) ?? Promise.resolve()).then(write, write);
    queues.current.set(selectedManuscriptId, queued);
    try { return await queued; }
    finally { target.exclusive = false; if (queues.current.get(selectedManuscriptId) === queued) queues.current.delete(selectedManuscriptId); refresh(); }
  }, [key, refresh, replaceManuscript, saveSnapshot, selectedManuscriptId, selectedSceneId, stopTimer]);
  const runAssistantWrite = useCallback(async (expectedHtml: string, action: (current: Manuscript) => Promise<Manuscript>, saveBaseline = true) =>
    (await performAssistantWrite(expectedHtml, action, saveBaseline)).manuscript, [performAssistantWrite]);
  const reconcileAssistantWrite = useCallback((expectedHtml: string, expectedServerHtml: string, action: (current: Manuscript) => Promise<Manuscript>) =>
    performAssistantWrite(expectedHtml, action, false, { expectedServerHtml }), [performAssistantWrite]);

  const initializeAssistantBranch = useCallback(async () => {
    const target = scopes.current.get(key);
    if (!target?.manuscript) throw new AssistantInteractionError(t("assistant.chooseScene"));
    if (target.manuscript.currentBranchId) return target.manuscript;
    target.exclusive = true;
    refresh();
    const initialize = async () => {
      await api.v2.version.listBranches(selectedManuscriptId);
      const current = await api.manuscripts.get(selectedManuscriptId);
      if (!current.currentBranchId) throw new AssistantInteractionError(t("assistant.identityChanged"));
      target.manuscript = current;
      // Initial main-branch creation names the existing draft scope; it is not a checkout.
      scopes.current.set(scopeKey(current.id, current.currentBranchId), target);
      if (activeKey.current === key) replaceManuscript(current);
      return current;
    };
    const queued = (queues.current.get(selectedManuscriptId) ?? Promise.resolve()).then(initialize, initialize);
    queues.current.set(selectedManuscriptId, queued);
    try { return await queued; }
    finally { target.exclusive = false; if (queues.current.get(selectedManuscriptId) === queued) queues.current.delete(selectedManuscriptId); refresh(); }
  }, [key, refresh, replaceManuscript, selectedManuscriptId]);

  const scheduleSave = useCallback((sceneId: string, html: string) => {
    if (!selectedManuscriptId || !sceneId) return;
    stopTimer(key, sceneId);
    const timerKey = JSON.stringify([key, sceneId]);
    timers.current.set(timerKey, window.setTimeout(() => {
      timers.current.delete(timerKey);
      void persistSection(sceneId, html, true);
    }, 1200));
  }, [key, persistSection, selectedManuscriptId, stopTimer]);
  const updateSceneDraft = useCallback((sceneId: string, html: string) => {
    const target = scopes.current.get(key);
    if (!target) return;
    target.drafts = { ...target.drafts, [sceneId]: html };
    target.dirty = { ...target.dirty, [sceneId]: true };
    refresh();
  }, [key, refresh]);
  const cancelPendingSectionSave = useCallback((sceneId: string) => stopTimer(key, sceneId), [key, stopTimer]);
  const handleManualSave = useCallback(async () => {
    if (selectedSceneId) await persistSection(selectedSceneId, content);
  }, [content, persistSection, selectedSceneId]);
  const loadCandidateDraft = useCallback((html: string) => {
    const target = scopes.current.get(key);
    if (!target || target.dirty[selectedSceneId] || target.saving) return;
    stopTimer(key, selectedSceneId);
    target.manualOnly = { ...target.manualOnly, [selectedSceneId]: true };
    updateSceneDraft(selectedSceneId, html);
  }, [key, selectedSceneId, stopTimer, updateSceneDraft]);
  const handleEditorChange = useCallback((html: string) => {
    if (scopes.current.get(key)?.exclusive) return;
    if (!selectedSceneId || hydrated.current !== JSON.stringify([key, selectedSceneId])) return;
    recordSceneHtml(selectedSceneId, html);
    updateSceneDraft(selectedSceneId, html);
    scheduleSave(selectedSceneId, html);
  }, [key, recordSceneHtml, scheduleSave, selectedSceneId, updateSceneDraft]);
  return { initializeAssistantBranch, runAssistantWrite, reconcileAssistantWrite, assistantWriteLocked: Boolean(scope.exclusive), loadCandidateDraft, applyFetchedManuscript, applyServerSection, cancelPendingSectionSave, content, currentWordCount, dirtyScenes,
    handleEditorChange, handleManualSave, isSaving: scope.saving > 0 || Boolean(scope.exclusive),
    lastSavedAt: dirtyScenes[selectedSceneId] ? "" : scope.lastSavedAt || (scope.manuscript?.updatedAt ? new Date(scope.manuscript.updatedAt).toLocaleTimeString() : ""),
    persistSection, scheduleSave, sceneDrafts, sessionDurationSeconds, sessionNetWords, updateSceneDraft };
}
