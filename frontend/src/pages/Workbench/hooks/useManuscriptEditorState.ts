import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api, isApiError } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import { countWords, stripHtml } from "@/pages/Workbench/tabs/manuscript-writer/shared";
import { useWritingSession } from "./useWritingSession";
import { t } from "@/i18n";
import { localizedErrorMessage } from "@/lib/error-messages";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: "default" | "destructive";
}) => void;

type UseManuscriptEditorStateOptions = {
  autoSaveIntervalSeconds?: number | null;
  replaceManuscript: (manuscript: Manuscript) => void;
  selectedManuscript?: Manuscript | null;
  selectedManuscriptId: string;
  selectedSceneId: string;
  selectedStoryId: string;
  toast: ToastFn;
};

export function useManuscriptEditorState({
  autoSaveIntervalSeconds,
  replaceManuscript,
  selectedManuscript,
  selectedManuscriptId,
  selectedSceneId,
  selectedStoryId,
  toast,
}: UseManuscriptEditorStateOptions) {
  const [dirtyScenes, setDirtyScenes] = useState<Record<string, boolean>>({});
  const [sceneDrafts, setSceneDrafts] = useState<Record<string, string>>({});
  // Resolve the selected scene in this render, before a newly mounted editor can emit updates.
  const content = selectedSceneId
    ? sceneDrafts[selectedSceneId] ?? selectedManuscript?.sections?.[selectedSceneId] ?? ""
    : "";
  const [isSaving, setIsSaving] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState("");
  const saveTimer = useRef<Record<string, number>>({});
  const hydratedSceneKeyRef = useRef("");
  const manuscriptRef = useRef(selectedManuscript);
  const draftsRef = useRef(sceneDrafts);
  const saveQueue = useRef<Promise<void>>(Promise.resolve());
  const conflictRef = useRef(false);
  if (selectedManuscript?.id !== manuscriptRef.current?.id ||
      (selectedManuscript && selectedManuscript.version >= (manuscriptRef.current?.version ?? -1))) {
    manuscriptRef.current = selectedManuscript;
  }
  draftsRef.current = sceneDrafts;

  const sceneKey = useCallback(
    (manuscriptId: string, sceneId: string) => `${manuscriptId}:${sceneId}`,
    [],
  );

  const currentWordCount = useMemo(() => countWords(stripHtml(content)), [content]);
  const measureSceneWords = useCallback((html: string) => countWords(stripHtml(html)), []);

  const { primeSceneHtml, recordSceneHtml, sessionDurationSeconds, sessionNetWords } = useWritingSession({
    selectedStoryId,
    selectedManuscriptId,
    selectedSceneId,
    selectedSceneDirty: Boolean(selectedSceneId && dirtyScenes[selectedSceneId]),
    autoSaveIntervalSeconds,
    measureHtmlWords: measureSceneWords,
  });

  const applyFetchedManuscript = useCallback(
    (manuscript: Manuscript) => {
      manuscriptRef.current = manuscript;
      conflictRef.current = false;
      replaceManuscript(manuscript);
      setSceneDrafts(manuscript.sections || {});
      setDirtyScenes({});
      if (selectedSceneId) {
        hydratedSceneKeyRef.current = sceneKey(manuscript.id, selectedSceneId);
      }
    },
    [replaceManuscript, sceneKey, selectedSceneId],
  );

  const applyServerSection = useCallback((manuscript: Manuscript, sceneId: string) => {
    const html = manuscript.sections?.[sceneId];
    if (typeof html !== "string") throw new Error(t("editorState.noServerSection"));
    if (saveTimer.current[sceneId]) {
      window.clearTimeout(saveTimer.current[sceneId]);
      delete saveTimer.current[sceneId];
    }
    replaceManuscript(manuscript);
    setSceneDrafts((prev) => ({ ...prev, [sceneId]: html }));
    setDirtyScenes((prev) => {
      const next = { ...prev };
      delete next[sceneId];
      return next;
    });
    if (selectedSceneId === sceneId) {
      hydratedSceneKeyRef.current = sceneKey(manuscript.id, sceneId);
    }
    primeSceneHtml(sceneId, html);
  }, [primeSceneHtml, replaceManuscript, sceneKey, selectedSceneId]);

  useEffect(() => {
    if (!selectedManuscript || !selectedSceneId) {
      hydratedSceneKeyRef.current = "";
      return;
    }
    setLastSavedAt(dirtyScenes[selectedSceneId] ? "" : (selectedManuscript.updatedAt
      ? new Date(selectedManuscript.updatedAt).toLocaleTimeString() : ""));
    const html = sceneDrafts[selectedSceneId] ?? selectedManuscript.sections?.[selectedSceneId] ?? "";
    hydratedSceneKeyRef.current = sceneKey(selectedManuscript.id, selectedSceneId);
    primeSceneHtml(selectedSceneId, html);
  }, [dirtyScenes, primeSceneHtml, sceneDrafts, sceneKey, selectedManuscript, selectedSceneId]);

  useEffect(() => {
    const timers = saveTimer.current;
    Object.values(timers).forEach((timer) => window.clearTimeout(timer));
    return () => Object.values(timers).forEach((timer) => window.clearTimeout(timer));
  }, []);

  const persistSection = useCallback(
    async (sceneId: string, html: string, silent = false) => {
      if (!selectedManuscriptId || !sceneId) return;
      if (saveTimer.current[sceneId]) {
        window.clearTimeout(saveTimer.current[sceneId]);
        delete saveTimer.current[sceneId];
      }
      const save = async () => {
        if (conflictRef.current && silent) return;
        setIsSaving(true);
        try {
          if (manuscriptRef.current?.id !== selectedManuscriptId) return;
          if (!manuscriptRef.current) throw new Error(t("editorState.noServerSection"));
          const saved = await api.manuscripts.saveSection(
            selectedManuscriptId,
            sceneId,
            html,
            manuscriptRef.current.version,
          );
          if (manuscriptRef.current?.id !== selectedManuscriptId) return;
          manuscriptRef.current = saved;
          replaceManuscript(saved);
          const unchanged = draftsRef.current[sceneId] === undefined || draftsRef.current[sceneId] === html;
          if (unchanged) {
            setSceneDrafts((prev) => ({ ...prev, [sceneId]: saved.sections?.[sceneId] ?? html }));
            setDirtyScenes((prev) => ({ ...prev, [sceneId]: false }));
          }
          conflictRef.current = false;
          setLastSavedAt(new Date().toLocaleTimeString());
          if (!silent) toast({ title: t("editorState.saved") });
        } catch (e: unknown) {
          if (isApiError(e) && e.status === 409) conflictRef.current = true;
          toast({
            variant: "destructive",
            title: silent ? t("editorState.autoSaveFailed") : t("editorState.saveFailed"),
            description: isApiError(e) && e.status === 409
              ? "服务端正文已变化，本地内容已保留。请复制当前内容后重新载入并比较，再决定如何保存。"
              : localizedErrorMessage(e),
          });
        } finally {
          setIsSaving(false);
        }
      };
      saveQueue.current = saveQueue.current.then(save, save);
      await saveQueue.current;
    },
    [replaceManuscript, selectedManuscriptId, toast],
  );

  const scheduleSave = useCallback(
    (sceneId: string, html: string) => {
      if (!selectedManuscriptId || !sceneId) return;
      if (saveTimer.current[sceneId]) window.clearTimeout(saveTimer.current[sceneId]);
      saveTimer.current[sceneId] = window.setTimeout(() => {
        delete saveTimer.current[sceneId];
        void persistSection(sceneId, html, true);
      }, 1200);
    },
    [persistSection, selectedManuscriptId],
  );

  const handleManualSave = useCallback(async () => {
    if (!selectedSceneId) return;
    await persistSection(selectedSceneId, content, false);
  }, [content, persistSection, selectedSceneId]);

  const updateSceneDraft = useCallback((sceneId: string, html: string) => {
    draftsRef.current = { ...draftsRef.current, [sceneId]: html };
    setSceneDrafts((prev) => ({ ...prev, [sceneId]: html }));
    setDirtyScenes((prev) => ({ ...prev, [sceneId]: true }));
  }, []);

  const cancelPendingSectionSave = useCallback((sceneId: string) => {
    if (!saveTimer.current[sceneId]) return;
    window.clearTimeout(saveTimer.current[sceneId]);
    delete saveTimer.current[sceneId];
  }, []);

  const handleEditorChange = useCallback(
    (html: string) => {
      if (hydratedSceneKeyRef.current !== sceneKey(selectedManuscriptId, selectedSceneId)) return;
      if (!selectedSceneId) return;
      recordSceneHtml(selectedSceneId, html);
      updateSceneDraft(selectedSceneId, html);
      scheduleSave(selectedSceneId, html);
    },
    [recordSceneHtml, sceneKey, scheduleSave, selectedManuscriptId, selectedSceneId, updateSceneDraft],
  );

  return {
    applyFetchedManuscript,
    applyServerSection,
    cancelPendingSectionSave,
    content,
    currentWordCount,
    dirtyScenes,
    handleEditorChange,
    handleManualSave,
    isSaving,
    lastSavedAt,
    persistSection,
    scheduleSave,
    sceneDrafts,
    sessionDurationSeconds,
    sessionNetWords,
    updateSceneDraft,
  };
}
