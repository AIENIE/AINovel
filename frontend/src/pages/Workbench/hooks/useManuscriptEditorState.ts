import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import { countWords, stripHtml } from "@/pages/Workbench/tabs/manuscript-writer/shared";
import { useWritingSession } from "./useWritingSession";

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
  const [content, setContent] = useState("");
  const [dirtyScenes, setDirtyScenes] = useState<Record<string, boolean>>({});
  const [sceneDrafts, setSceneDrafts] = useState<Record<string, string>>({});
  const [isSaving, setIsSaving] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState("");
  const saveTimer = useRef<Record<string, number>>({});

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
      replaceManuscript(manuscript);
      setSceneDrafts(manuscript.sections || {});
      setDirtyScenes({});
    },
    [replaceManuscript],
  );

  useEffect(() => {
    if (!selectedManuscript || !selectedSceneId) {
      setContent("");
      return;
    }
    const html = sceneDrafts[selectedSceneId] ?? selectedManuscript.sections?.[selectedSceneId] ?? "";
    setContent(html);
    primeSceneHtml(selectedSceneId, html);
  }, [primeSceneHtml, sceneDrafts, selectedManuscript, selectedSceneId]);

  useEffect(() => {
    Object.values(saveTimer.current).forEach((timer) => window.clearTimeout(timer));
    return () => Object.values(saveTimer.current).forEach((timer) => window.clearTimeout(timer));
  }, []);

  const persistSection = useCallback(
    async (sceneId: string, html: string, silent = false) => {
      if (!selectedManuscriptId || !sceneId) return;
      setIsSaving(true);
      try {
        const saved = await api.manuscripts.saveSection(selectedManuscriptId, sceneId, html);
        replaceManuscript(saved);
        setSceneDrafts((prev) => ({ ...prev, [sceneId]: saved.sections?.[sceneId] || html }));
        setDirtyScenes((prev) => ({ ...prev, [sceneId]: false }));
        setLastSavedAt(new Date().toLocaleTimeString());
        if (!silent) toast({ title: "已保存" });
      } catch (e: any) {
        toast({
          variant: "destructive",
          title: silent ? "自动保存失败" : "保存失败",
          description: e.message,
        });
      } finally {
        setIsSaving(false);
      }
    },
    [replaceManuscript, selectedManuscriptId, toast],
  );

  const scheduleSave = useCallback(
    (sceneId: string, html: string) => {
      if (!selectedManuscriptId || !sceneId) return;
      if (saveTimer.current[sceneId]) window.clearTimeout(saveTimer.current[sceneId]);
      saveTimer.current[sceneId] = window.setTimeout(() => {
        void persistSection(sceneId, html, true);
      }, 1200);
    },
    [persistSection, selectedManuscriptId],
  );

  const handleManualSave = useCallback(async () => {
    if (!selectedSceneId) return;
    if (saveTimer.current[selectedSceneId]) window.clearTimeout(saveTimer.current[selectedSceneId]);
    await persistSection(selectedSceneId, content, false);
  }, [content, persistSection, selectedSceneId]);

  const updateSceneDraft = useCallback((sceneId: string, html: string) => {
    setSceneDrafts((prev) => ({ ...prev, [sceneId]: html }));
    setDirtyScenes((prev) => ({ ...prev, [sceneId]: true }));
  }, []);

  const handleEditorChange = useCallback(
    (html: string) => {
      setContent(html);
      if (!selectedSceneId) return;
      recordSceneHtml(selectedSceneId, html);
      updateSceneDraft(selectedSceneId, html);
      scheduleSave(selectedSceneId, html);
    },
    [recordSceneHtml, scheduleSave, selectedSceneId, updateSceneDraft],
  );

  return {
    applyFetchedManuscript,
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
    setContent,
    updateSceneDraft,
  };
}
