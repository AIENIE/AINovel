import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api, isApiError } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import { countWords, stripHtml } from "@/pages/Workbench/tabs/manuscript-writer/shared";
import { useWritingSession } from "./useWritingSession";
import { t } from "@/i18n";
import { localizedErrorMessage } from "@/lib/error-messages";

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
  lastSavedAt: string;
};
const scopeKey = (id: string, branch?: string | null) => JSON.stringify([id, branch || ""]);
const emptyScope = (): DraftScope => ({ drafts: {}, dirty: {}, conflict: false, saving: 0, lastSavedAt: "" });

export function useManuscriptEditorState({ autoSaveIntervalSeconds, replaceManuscript, selectedManuscript,
  selectedManuscriptId, selectedSceneId, selectedStoryId, toast }: Options) {
  // Scope identity is resolved during render: an effect cannot protect the first frame after a switch.
  const scopes = useRef(new Map<string, DraftScope>());
  const queues = useRef(new Map<string, Promise<void>>());
  const timers = useRef(new Map<string, number>());
  const [, render] = useState(0);
  const refresh = useCallback(() => render(value => value + 1), []);
  const key = scopeKey(selectedManuscriptId, selectedManuscript?.currentBranchId);
  const activeKey = useRef(key);
  activeKey.current = key;
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

  const persistSection = useCallback(async (sceneId: string, html: string, silent = false) => {
    if (!selectedManuscriptId || !sceneId) return;
    const target = scopes.current.get(key);
    if (!target) return;
    stopTimer(key, sceneId);
    const save = async () => {
      if (target.conflict && silent) return;
      target.saving++;
      refresh();
      try {
        const manuscript = target.manuscript;
        if (!manuscript || manuscript.id !== selectedManuscriptId) throw new Error(t("editorState.noServerSection"));
        const saved = manuscript.currentBranchId
          ? await api.manuscripts.saveSection(selectedManuscriptId, sceneId, html, manuscript.version, manuscript.currentBranchId)
          : await api.manuscripts.saveSection(selectedManuscriptId, sceneId, html, manuscript.version);
        if (saved.id !== selectedManuscriptId || scopeKey(saved.id, saved.currentBranchId) !== key) {
          throw new Error("保存响应的稿件或分支身份不匹配，草稿已保留");
        }
        const merged = { ...manuscript, ...saved, sections: { ...manuscript.sections, ...saved.sections } };
        target.manuscript = merged;
        if (target.drafts[sceneId] === undefined || target.drafts[sceneId] === html) {
          target.drafts = { ...target.drafts, [sceneId]: saved.sections?.[sceneId] ?? html };
          target.dirty = { ...target.dirty, [sceneId]: false };
        }
        target.conflict = false;
        target.lastSavedAt = new Date().toLocaleTimeString();
        if (activeKey.current === key) {
          replaceManuscript(merged);
          if (!silent) toast({ title: t("editorState.saved") });
        }
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
  }, [key, refresh, replaceManuscript, selectedManuscriptId, stopTimer, toast]);

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
  const handleEditorChange = useCallback((html: string) => {
    if (!selectedSceneId || hydrated.current !== JSON.stringify([key, selectedSceneId])) return;
    recordSceneHtml(selectedSceneId, html);
    updateSceneDraft(selectedSceneId, html);
    scheduleSave(selectedSceneId, html);
  }, [key, recordSceneHtml, scheduleSave, selectedSceneId, updateSceneDraft]);
  return { applyFetchedManuscript, applyServerSection, cancelPendingSectionSave, content, currentWordCount, dirtyScenes,
    handleEditorChange, handleManualSave, isSaving: scope.saving > 0,
    lastSavedAt: dirtyScenes[selectedSceneId] ? "" : scope.lastSavedAt || (scope.manuscript?.updatedAt ? new Date(scope.manuscript.updatedAt).toLocaleTimeString() : ""),
    persistSection, scheduleSave, sceneDrafts, sessionDurationSeconds, sessionNetWords, updateSceneDraft };
}
