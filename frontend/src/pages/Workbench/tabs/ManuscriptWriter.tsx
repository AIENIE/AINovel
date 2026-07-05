import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ImperativePanelHandle } from "react-resizable-panels";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import { cn } from "@/lib/utils";
import { api } from "@/lib/api-client";
import { Manuscript, Outline, Story } from "@/types";
import { useToast } from "@/components/ui/use-toast";
import { ShortcutAction } from "@/lib/shortcuts";
import { useManuscriptQuality } from "@/pages/Workbench/hooks/useManuscriptQuality";
import { useWorkbenchLayoutPersistence } from "@/pages/Workbench/hooks/useWorkbenchLayoutPersistence";
import { useManuscriptSidebarData } from "@/pages/Workbench/hooks/useManuscriptSidebarData";
import { useManuscriptShortcuts } from "@/pages/Workbench/hooks/useManuscriptShortcuts";
import { useWorkbenchViewport } from "@/pages/Workbench/hooks/useWorkbenchViewport";
import { useWritingSession } from "@/pages/Workbench/hooks/useWritingSession";
import { SceneOutlinePanel } from "./manuscript-writer/SceneOutlinePanel";
import { MobileWorkbenchPanel } from "./manuscript-writer/MobileWorkbenchPanel";
import { DesktopEditorPanel } from "./manuscript-writer/DesktopEditorPanel";
import { DesktopSidebarPanel } from "./manuscript-writer/DesktopSidebarPanel";
import { WorkbenchOverlays } from "./manuscript-writer/WorkbenchOverlays";
import {
  countWords,
  qualityStatusText,
  stripHtml,
} from "./manuscript-writer/shared";

interface ManuscriptWriterProps {
  initialStoryId?: string;
}

type SceneStatus = "todo" | "in_progress" | "done";

const sceneStatusClass: Record<SceneStatus, string> = {
  todo: "bg-zinc-300",
  in_progress: "bg-amber-400",
  done: "bg-emerald-500",
};

type SceneRow = {
  chapterId: string;
  chapterTitle: string;
  sceneIndex: number;
  chapterIndex: number;
  scene: any;
  id: string;
  displayName: string;
};
const ManuscriptWriter = ({ initialStoryId }: ManuscriptWriterProps) => {
  const { toast } = useToast();
  const [stories, setStories] = useState<Story[]>([]);
  const [selectedStoryId, setSelectedStoryId] = useState<string>("");
  const [outlines, setOutlines] = useState<Outline[]>([]);
  const [selectedOutlineId, setSelectedOutlineId] = useState<string>("");
  const [outlineDraft, setOutlineDraft] = useState<Outline | null>(null);
  const [manuscripts, setManuscripts] = useState<Manuscript[]>([]);
  const [selectedManuscriptId, setSelectedManuscriptId] = useState<string>("");
  const [selectedSceneIds, setSelectedSceneIds] = useState<string[]>([]);
  const [openSceneIds, setOpenSceneIds] = useState<string[]>([]);
  const [expandedChapterIds, setExpandedChapterIds] = useState<Record<string, boolean>>({});
  const [sceneStatuses, setSceneStatuses] = useState<Record<string, SceneStatus>>({});

  const [selectedSceneId, setSelectedSceneId] = useState<string>("");
  const [content, setContent] = useState<string>("");
  const [dirtyScenes, setDirtyScenes] = useState<Record<string, boolean>>({});
  const [sceneDrafts, setSceneDrafts] = useState<Record<string, string>>({});
  const [focusMode, setFocusMode] = useState(false);
  const [isCommandOpen, setIsCommandOpen] = useState(false);
  const [commandQuery, setCommandQuery] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState<string>("");
  const [isGenerating, setIsGenerating] = useState(false);
  const [selectedWordCount, setSelectedWordCount] = useState(0);
  const [characters, setCharacters] = useState<any[]>([]);
  const [batchMoveChapterId, setBatchMoveChapterId] = useState("");
  const [draggingChapterId, setDraggingChapterId] = useState("");
  const [dragOverChapterId, setDragOverChapterId] = useState("");
  const [draggingSceneId, setDraggingSceneId] = useState("");
  const [dragOverSceneId, setDragOverSceneId] = useState("");
  const [draggingTabId, setDraggingTabId] = useState("");
  const [mobilePane, setMobilePane] = useState<"outline" | "editor" | "sidebar">("editor");
  const saveTimer = useRef<Record<string, number>>({});
  const leftPanelRef = useRef<ImperativePanelHandle | null>(null);
  const rightPanelRef = useRef<ImperativePanelHandle | null>(null);
  const leftPanelVisibleRef = useRef<boolean | null>(null);
  const rightPanelVisibleRef = useRef<boolean | null>(null);
  const focusRestoreRef = useRef<{ leftOpen: boolean; rightOpen: boolean } | null>(null);
  const lastSelectedSceneRef = useRef("");

  const {
    isSidebarOpen,
    leftPanelOpen,
    leftPanelSize,
    rightPanelSize,
    setIsSidebarOpen,
    setLeftPanelOpen,
    setLeftPanelSize,
    setRightPanelSize,
    setSidebarTab,
    sidebarTab,
  } = useWorkbenchLayoutPersistence({
    selectedStoryId,
    selectedManuscriptId,
  });

  const applyFetchedManuscript = useCallback((manuscript: Manuscript) => {
    setManuscripts((prev) => prev.map((item) => (item.id === manuscript.id ? manuscript : item)));
    setSceneDrafts(manuscript.sections || {});
    setDirtyScenes({});
  }, []);

  const {
    aiDiffSummary,
    autoSaveConfig,
    branches,
    chapterRange,
    checkoutBranch,
    contextPreview,
    createBranch,
    createExportJob,
    createGoal,
    createManualVersion,
    createTemplate,
    currentBranchId,
    deleteGoal,
    deleteTemplate,
    diffResult,
    diffViewMode,
    exportAuthorName,
    exportFormat,
    exportJobs,
    exportTemplateId,
    exportTemplates,
    goalTargetValue,
    goalType,
    goals,
    hasMoreVersions,
    includeTableOfContents,
    includeTitlePage,
    loadContextPreview,
    loadStats,
    loadVersions,
    mergeBranchId,
    mergeConflicts,
    mergeSelectedBranch,
    mergeStrategy,
    newBranchName,
    rollbackVersion,
    runVersionDiff,
    saveAutoSaveConfig,
    sceneResolutions,
    selectedDiffVersions,
    setAutoSaveConfig,
    setChapterRange,
    setDiffViewMode,
    setExportAuthorName,
    setExportFormat,
    setExportTemplateId,
    setGoalTargetValue,
    setGoalType,
    setIncludeTableOfContents,
    setIncludeTitlePage,
    setMergeBranchId,
    setMergeStrategy,
    setNewBranchName,
    setSceneResolutions,
    setTemplateDescription,
    setTemplateName,
    setTxtEncoding,
    setVersionVisibleCount,
    summarizeDiff,
    templateDescription,
    templateName,
    toggleVersionSelection,
    txtEncoding,
    updateGoal,
    updateTemplate,
    versionPageSize,
    versions,
    visibleVersions,
    workspaceStats,
  } = useManuscriptSidebarData({
    applyFetchedManuscript,
    isSidebarOpen,
    selectedManuscriptId,
    selectedSceneIds,
    selectedStoryId,
    sidebarTab,
    toast,
  });

  const selectedStory = useMemo(() => stories.find((s) => s.id === selectedStoryId) || null, [stories, selectedStoryId]);
  const selectedOutline = useMemo(() => outlines.find((o) => o.id === selectedOutlineId) || null, [outlines, selectedOutlineId]);
  const selectedManuscript = useMemo(() => manuscripts.find((m) => m.id === selectedManuscriptId) || null, [manuscripts, selectedManuscriptId]);
  const sceneRows = useMemo(() => {
    const list: SceneRow[] = [];
    (outlineDraft?.chapters || []).forEach((chapter, chapterIndex) => {
      chapter.scenes.forEach((scene, sceneIndex) => {
        list.push({
          chapterId: chapter.id,
          chapterTitle: chapter.title,
          sceneIndex,
          chapterIndex,
          scene,
          id: scene.id,
          displayName: `第${chapterIndex + 1}章 Sc.${sceneIndex + 1} ${scene.title}`,
        });
      });
    });
    return list;
  }, [outlineDraft]);
  const sceneMap = useMemo(() => Object.fromEntries(sceneRows.map((row) => [row.id, row])), [sceneRows]);
  const currentWordCount = useMemo(() => countWords(stripHtml(content)), [content]);
  const chapters = outlineDraft?.chapters || [];
  const activeGoal = goals.find((goal) => String(goal.status || "active").toLowerCase() !== "archived");
  const dailyHeatmap = useMemo(() => (workspaceStats?.dailySeries || []).slice(-30), [workspaceStats]);
  const showLeftPanel = leftPanelOpen && !focusMode;
  const showRightPanel = isSidebarOpen && !focusMode;

  useEffect(() => {
    if (leftPanelVisibleRef.current === showLeftPanel) return;
    if (showLeftPanel) leftPanelRef.current?.expand();
    else leftPanelRef.current?.collapse();
    leftPanelVisibleRef.current = showLeftPanel;
  }, [showLeftPanel]);

  useEffect(() => {
    if (rightPanelVisibleRef.current === showRightPanel) return;
    if (showRightPanel) rightPanelRef.current?.expand();
    else rightPanelRef.current?.collapse();
    rightPanelVisibleRef.current = showRightPanel;
  }, [showRightPanel]);

  const reorderOpenTabs = useCallback((fromId: string, toId: string) => {
    if (!fromId || !toId || fromId === toId) return;
    setOpenSceneIds((prev) => {
      const from = prev.indexOf(fromId);
      const to = prev.indexOf(toId);
      if (from < 0 || to < 0) return prev;
      const next = [...prev];
      const [item] = next.splice(from, 1);
      next.splice(to, 0, item);
      return next;
    });
  }, []);

  const persistOutlineDraft = useCallback(
    async (nextOutline: Outline) => {
      if (!selectedOutlineId) return;
      try {
        const saved = await api.outlines.save(selectedOutlineId, nextOutline);
        setOutlines((prev) => prev.map((outline) => (outline.id === saved.id ? saved : outline)));
      } catch (e: any) {
        toast({ variant: "destructive", title: "保存大纲顺序失败", description: e.message });
      }
    },
    [selectedOutlineId, toast],
  );

  const moveScene = useCallback(
    async (sourceSceneId: string, targetSceneId: string) => {
      if (!outlineDraft || !sourceSceneId || !targetSceneId || sourceSceneId === targetSceneId) return;
      const sourceRow = sceneMap[sourceSceneId];
      const targetRow = sceneMap[targetSceneId];
      if (!sourceRow || !targetRow) return;

      const nextOutline = JSON.parse(JSON.stringify(outlineDraft)) as Outline;
      const sourceChapter = nextOutline.chapters.find((chapter) => chapter.id === sourceRow.chapterId);
      const targetChapter = nextOutline.chapters.find((chapter) => chapter.id === targetRow.chapterId);
      if (!sourceChapter || !targetChapter) return;

      const sourceIndex = sourceChapter.scenes.findIndex((scene) => scene.id === sourceSceneId);
      const targetIndex = targetChapter.scenes.findIndex((scene) => scene.id === targetSceneId);
      if (sourceIndex < 0 || targetIndex < 0) return;

      const [moved] = sourceChapter.scenes.splice(sourceIndex, 1);
      targetChapter.scenes.splice(targetIndex, 0, moved);
      setOutlineDraft(nextOutline);
      await persistOutlineDraft(nextOutline);
    },
    [outlineDraft, persistOutlineDraft, sceneMap],
  );

  const moveChapter = useCallback(
    async (sourceChapterId: string, targetChapterId: string) => {
      if (!outlineDraft || !sourceChapterId || !targetChapterId || sourceChapterId === targetChapterId) return;
      const sourceIndex = outlineDraft.chapters.findIndex((chapter) => chapter.id === sourceChapterId);
      const targetIndex = outlineDraft.chapters.findIndex((chapter) => chapter.id === targetChapterId);
      if (sourceIndex < 0 || targetIndex < 0) return;

      const nextOutline = JSON.parse(JSON.stringify(outlineDraft)) as Outline;
      const [moved] = nextOutline.chapters.splice(sourceIndex, 1);
      nextOutline.chapters.splice(targetIndex, 0, moved);
      setOutlineDraft(nextOutline);
      await persistOutlineDraft(nextOutline);
    },
    [outlineDraft, persistOutlineDraft],
  );

  const loadCharacters = useCallback(async () => {
    if (!selectedStoryId) {
      setCharacters([]);
      return;
    }
    try {
      setCharacters(await api.stories.listCharacters(selectedStoryId));
    } catch {
      setCharacters([]);
    }
  }, [selectedStoryId]);

  const loadStories = useCallback(async () => {
    try {
      const list = await api.stories.list();
      setStories(list);
      if (initialStoryId && list.some((s) => s.id === initialStoryId)) setSelectedStoryId(initialStoryId);
      else if (list.length > 0) setSelectedStoryId(list[0].id);
    } catch (e: any) {
      toast({ variant: "destructive", title: "加载故事失败", description: e.message });
    }
  }, [initialStoryId, toast]);

  useEffect(() => {
    void loadStories();
  }, [loadStories]);

  useEffect(() => {
    void loadCharacters();
  }, [loadCharacters]);

  useEffect(() => {
    if (!selectedStoryId) return;
    api.outlines
      .listByStory(selectedStoryId)
      .then(async (list) => {
        let next = list;
        if (next.length === 0) {
          const created = await api.outlines.create(selectedStoryId, { title: "主线大纲" });
          next = [created];
        }
        setOutlines(next);
        setSelectedOutlineId((prev) => (prev && next.some((o) => o.id === prev) ? prev : next[0].id));
      })
      .catch((e: any) => toast({ variant: "destructive", title: "加载大纲失败", description: e.message }));
  }, [selectedStoryId, toast]);

  useEffect(() => {
    if (!selectedOutlineId) return;
    api.manuscripts
      .listByOutline(selectedOutlineId)
      .then(async (list) => {
        let next = list;
        if (next.length === 0) {
          const created = await api.manuscripts.create(selectedOutlineId, { title: "正文稿" });
          next = [created];
        }
        setManuscripts(next);
        setSelectedManuscriptId((prev) => (prev && next.some((m) => m.id === prev) ? prev : next[0].id));
      })
      .catch((e: any) => toast({ variant: "destructive", title: "加载稿件失败", description: e.message }));
  }, [selectedOutlineId, toast]);

  useEffect(() => {
    if (!selectedOutline) {
      setOutlineDraft(null);
      return;
    }
    const copied = JSON.parse(JSON.stringify(selectedOutline)) as Outline;
    setOutlineDraft(copied);
    const expanded: Record<string, boolean> = {};
    copied.chapters.forEach((chapter) => {
      expanded[chapter.id] = true;
    });
    setExpandedChapterIds(expanded);
    setBatchMoveChapterId(copied.chapters[0]?.id || "");
  }, [selectedOutline]);

  useEffect(() => {
    const firstScene = sceneRows[0]?.id || "";
    setSelectedSceneId((prev) => {
      if (!prev) return firstScene;
      return sceneRows.some((row) => row.id === prev) ? prev : firstScene;
    });
  }, [sceneRows]);

  useEffect(() => {
    if (!selectedSceneId) return;
    setOpenSceneIds((prev) => (prev.includes(selectedSceneId) ? prev : [...prev, selectedSceneId]));
    setSelectedSceneIds((prev) => (prev.length ? prev : [selectedSceneId]));
  }, [selectedSceneId]);

  useEffect(() => {
    const valid = new Set(sceneRows.map((row) => row.id));
    setOpenSceneIds((prev) => prev.filter((id) => valid.has(id)));
    setSelectedSceneIds((prev) => prev.filter((id) => valid.has(id)));
  }, [sceneRows]);

  const measureSceneWords = useCallback((html: string) => countWords(stripHtml(html)), []);

  const { primeSceneHtml, recordSceneHtml, sessionDurationSeconds, sessionNetWords } = useWritingSession({
    selectedStoryId,
    selectedManuscriptId,
    selectedSceneId,
    selectedSceneDirty: Boolean(selectedSceneId && dirtyScenes[selectedSceneId]),
    autoSaveIntervalSeconds: autoSaveConfig?.autoSaveIntervalSeconds,
    measureHtmlWords: measureSceneWords,
  });

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
        setManuscripts((prev) => prev.map((m) => (m.id === saved.id ? saved : m)));
        setSceneDrafts((prev) => ({ ...prev, [sceneId]: saved.sections?.[sceneId] || html }));
        setDirtyScenes((prev) => ({ ...prev, [sceneId]: false }));
        setLastSavedAt(new Date().toLocaleTimeString());
        if (!silent) toast({ title: "已保存" });
      } catch (e: any) {
        toast({ variant: "destructive", title: silent ? "自动保存失败" : "保存失败", description: e.message });
      } finally {
        setIsSaving(false);
      }
    },
    [selectedManuscriptId, toast],
  );

  const {
    applyPlotRevision,
    copySlopRewriteTask,
    generatePlotRevisionCandidate,
    isPlotBusy,
    isPlotRevisionBusy,
    isSlopBusy,
    loadPlotQuality,
    loadSlopQuality,
    plotTrend,
    runPlotDiagnosis,
    runSlopDiagnosis,
    selectedPlotRun,
    selectedQualityRun,
  } = useManuscriptQuality({
    applyFetchedManuscript,
    content,
    dirtyScenes,
    persistSection,
    selectedManuscriptId,
    selectedSceneId,
    setContent,
    setSidebarTab,
    toast,
  });

  const plotTrendChartData = useMemo(
    () =>
      (plotTrend?.points || []).map((point) => ({
        ...point,
        label: `${point.chapterOrder + 1}-${point.sceneOrder + 1}`,
      })),
    [plotTrend],
  );
  const plotDimensionEntries = useMemo(
    () => Object.entries(plotTrend?.dimensionCounts || {}).sort((a, b) => b[1] - a[1]),
    [plotTrend],
  );

  const scheduleSave = useCallback(
    (sceneId: string, html: string) => {
      if (!selectedManuscriptId || !sceneId) return;
      if (saveTimer.current[sceneId]) window.clearTimeout(saveTimer.current[sceneId]);
      saveTimer.current[sceneId] = window.setTimeout(() => void persistSection(sceneId, html, true), 1200);
    },
    [persistSection, selectedManuscriptId],
  );

  const handleManualSave = useCallback(async () => {
    if (!selectedSceneId) return;
    if (saveTimer.current[selectedSceneId]) window.clearTimeout(saveTimer.current[selectedSceneId]);
    await persistSection(selectedSceneId, content, false);
  }, [content, persistSection, selectedSceneId]);

  const handleEditorChange = useCallback(
    (html: string) => {
      setContent(html);
      if (!selectedSceneId) return;
      recordSceneHtml(selectedSceneId, html);
      setSceneDrafts((prev) => ({ ...prev, [selectedSceneId]: html }));
      setDirtyScenes((prev) => ({ ...prev, [selectedSceneId]: true }));
      scheduleSave(selectedSceneId, html);
    },
    [recordSceneHtml, scheduleSave, selectedSceneId],
  );

  const jumpScene = useCallback((offset: number) => {
    if (!sceneRows.length) return;
    const currentIndex = sceneRows.findIndex((row) => row.id === selectedSceneId);
    const nextIndex = currentIndex < 0 ? 0 : Math.max(0, Math.min(sceneRows.length - 1, currentIndex + offset));
    const nextSceneId = sceneRows[nextIndex]?.id;
    if (!nextSceneId) return;
    setSelectedSceneId(nextSceneId);
    setSelectedSceneIds([nextSceneId]);
    setOpenSceneIds((prev) => (prev.includes(nextSceneId) ? prev : [...prev, nextSceneId]));
  }, [sceneRows, selectedSceneId]);

  const closeCurrentTab = useCallback(() => {
    if (!selectedSceneId) return;
    closeSceneTab(selectedSceneId);
  }, [selectedSceneId]);

  const focusNextTab = useCallback(() => {
    if (!openSceneIds.length) return;
    const currentIndex = openSceneIds.indexOf(selectedSceneId);
    const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % openSceneIds.length;
    setSelectedSceneId(openSceneIds[nextIndex]);
  }, [openSceneIds, selectedSceneId]);

  const createSceneInCurrentChapter = useCallback(async () => {
    if (!outlineDraft || !selectedOutlineId) return;
    const row = sceneMap[selectedSceneId] || sceneRows[0];
    const nextOutline = JSON.parse(JSON.stringify(outlineDraft)) as Outline;
    const chapter = (row ? nextOutline.chapters.find((item) => item.id === row.chapterId) : null) || nextOutline.chapters[0];
    if (!chapter) return;
    const sceneId = `scene-${Date.now()}`;
    chapter.scenes.push({
      id: sceneId,
      title: `新场景 ${chapter.scenes.length + 1}`,
      summary: "",
    });
    setOutlineDraft(nextOutline);
    await persistOutlineDraft(nextOutline);
    setSelectedSceneId(sceneId);
    setOpenSceneIds((prev) => (prev.includes(sceneId) ? prev : [...prev, sceneId]));
  }, [outlineDraft, persistOutlineDraft, sceneMap, sceneRows, selectedOutlineId, selectedSceneId]);

  const enterFocusMode = useCallback(() => {
    if (focusMode) return;
    focusRestoreRef.current = { leftOpen: leftPanelOpen, rightOpen: isSidebarOpen };
    setLeftPanelOpen(false);
    setIsSidebarOpen(false);
    setFocusMode(true);
  }, [focusMode, isSidebarOpen, leftPanelOpen]);

  const exitFocusMode = useCallback(() => {
    if (!focusMode) return;
    const restore = focusRestoreRef.current || { leftOpen: true, rightOpen: true };
    setFocusMode(false);
    window.setTimeout(() => {
      setLeftPanelOpen(Boolean(restore.leftOpen));
      window.setTimeout(() => {
        setIsSidebarOpen(Boolean(restore.rightOpen));
      }, 80);
    }, 0);
  }, [focusMode]);

  const toggleFocusMode = useCallback(() => {
    if (focusMode) {
      exitFocusMode();
      return;
    }
    enterFocusMode();
  }, [enterFocusMode, exitFocusMode, focusMode]);

  const handleShortcutAction = useCallback((action: ShortcutAction) => {
    if (action === "command_palette") setIsCommandOpen(true);
    if (action === "save") void handleManualSave();
    if (action === "focus_mode") toggleFocusMode();
    if (action === "toggle_left_panel") setLeftPanelOpen((prev) => !prev);
    if (action === "toggle_right_panel") setIsSidebarOpen((prev) => !prev);
    if (action === "next_chapter") jumpScene(1);
    if (action === "prev_chapter") jumpScene(-1);
    if (action === "ai_refine") {
      setIsSidebarOpen(true);
      setSidebarTab("copilot");
    }
    if (action === "new_scene") void createSceneInCurrentChapter();
    if (action === "search_manuscript" || action === "search_replace") setIsCommandOpen(true);
    if (action === "export") {
      setIsSidebarOpen(true);
      setSidebarTab("export");
    }
    if (action === "close_tab") closeCurrentTab();
    if (action === "next_tab") focusNextTab();
  }, [closeCurrentTab, createSceneInCurrentChapter, focusNextTab, handleManualSave, jumpScene, toggleFocusMode]);

  const { shortcuts } = useManuscriptShortcuts({
    focusMode,
    onAction: handleShortcutAction,
    onExitFocusMode: exitFocusMode,
  });

  const { isCompact, isMobile } = useWorkbenchViewport({
    focusMode,
    setLeftPanelOpen,
    setIsSidebarOpen,
  });

  useEffect(() => {
    const selectionHandler = () => setSelectedWordCount(countWords(window.getSelection()?.toString() || ""));
    document.addEventListener("selectionchange", selectionHandler);
    return () => document.removeEventListener("selectionchange", selectionHandler);
  }, []);

  const contextData = useMemo(() => {
    const currentChapter = outlineDraft?.chapters?.find((c) => c.scenes.some((s) => s.id === selectedSceneId));
    const currentScene = currentChapter?.scenes?.find((s) => s.id === selectedSceneId);
    return {
      storyTitle: selectedStory?.title,
      outlineTitle: outlineDraft?.title,
      currentChapter: currentChapter?.title,
      currentScene: currentScene?.title,
      sceneSummary: currentScene?.summary,
      currentContent: content,
    };
  }, [selectedStory, outlineDraft, selectedSceneId, content]);

  const handleSceneSelect = (sceneId: string, event?: React.MouseEvent) => {
    const orderedIds = sceneRows.map((row) => row.id);
    if (event?.shiftKey && lastSelectedSceneRef.current) {
      const from = orderedIds.indexOf(lastSelectedSceneRef.current);
      const to = orderedIds.indexOf(sceneId);
      if (from >= 0 && to >= 0) {
        const [start, end] = from < to ? [from, to] : [to, from];
        setSelectedSceneIds(orderedIds.slice(start, end + 1));
      }
    } else if (event && (event.metaKey || event.ctrlKey)) {
      setSelectedSceneIds((prev) => prev.includes(sceneId) ? prev.filter((id) => id !== sceneId) : [...prev, sceneId]);
    } else {
      setSelectedSceneIds([sceneId]);
    }
    lastSelectedSceneRef.current = sceneId;
    setSelectedSceneId(sceneId);
  };

  const closeSceneTab = (sceneId: string) => {
    setOpenSceneIds((prev) => {
      const index = prev.indexOf(sceneId);
      if (index < 0) return prev;
      const next = prev.filter((id) => id !== sceneId);
      if (selectedSceneId === sceneId) setSelectedSceneId(next[index] || next[index - 1] || sceneRows[0]?.id || "");
      return next;
    });
  };

  const setSceneStatus = (sceneId: string, status: SceneStatus) => {
    setSceneStatuses((prev) => ({ ...prev, [sceneId]: status }));
  };

  const toggleChapterExpanded = useCallback((chapterId: string) => {
    setExpandedChapterIds((prev) => ({ ...prev, [chapterId]: !prev[chapterId] }));
  }, []);

  const deleteSceneFromOutline = useCallback(
    async (sceneId: string, chapterId: string) => {
      if (!outlineDraft) return;
      const nextOutline = JSON.parse(JSON.stringify(outlineDraft)) as Outline;
      const targetChapter = nextOutline.chapters.find((item) => item.id === chapterId);
      if (!targetChapter) return;
      targetChapter.scenes = targetChapter.scenes.filter((item) => item.id !== sceneId);
      setOutlineDraft(nextOutline);
      setOpenSceneIds((prev) => prev.filter((id) => id !== sceneId));
      if (selectedSceneId === sceneId) setSelectedSceneId("");
      await persistOutlineDraft(nextOutline);
    },
    [outlineDraft, persistOutlineDraft, selectedSceneId],
  );

  const batchDeleteScenes = async () => {
    if (!outlineDraft || !selectedSceneIds.length) return;
    const ok = window.confirm(`确认删除已选 ${selectedSceneIds.length} 个场景？`);
    if (!ok) return;
    const selected = new Set(selectedSceneIds);
    const nextOutline = JSON.parse(JSON.stringify(outlineDraft)) as Outline;
    nextOutline.chapters.forEach((chapter) => {
      chapter.scenes = chapter.scenes.filter((scene) => !selected.has(scene.id));
    });
    setOutlineDraft(nextOutline);
    setOpenSceneIds((prev) => prev.filter((id) => !selected.has(id)));
    setSelectedSceneIds([]);
    setSelectedSceneId("");
    await persistOutlineDraft(nextOutline);
  };

  const batchMoveScenes = async () => {
    if (!outlineDraft || !selectedSceneIds.length || !batchMoveChapterId) return;
    const selected = new Set(selectedSceneIds);
    const nextOutline = JSON.parse(JSON.stringify(outlineDraft)) as Outline;
    const moved: any[] = [];
    nextOutline.chapters.forEach((chapter) => {
      const keep: any[] = [];
      chapter.scenes.forEach((scene) => {
        if (selected.has(scene.id)) moved.push(scene);
        else keep.push(scene);
      });
      chapter.scenes = keep;
    });
    const target = nextOutline.chapters.find((chapter) => chapter.id === batchMoveChapterId);
    if (!target || !moved.length) return;
    target.scenes.push(...moved);
    setOutlineDraft(nextOutline);
    await persistOutlineDraft(nextOutline);
    toast({ title: `已移动 ${moved.length} 个场景` });
  };

  return (
    <div className="relative h-[calc(100vh-180px)]">
      {isMobile ? (
        <MobileWorkbenchPanel
          content={content}
          contextData={contextData}
          exportJobs={exportJobs}
          focusMode={focusMode}
          isPlotBusy={isPlotBusy}
          isPlotRevisionBusy={isPlotRevisionBusy}
          isSlopBusy={isSlopBusy}
          mobilePane={mobilePane}
          onApplyPlotRevision={applyPlotRevision}
          onChangeMobilePane={setMobilePane}
          onChangeSidebarTab={setSidebarTab}
          onCreateExportJob={createExportJob}
          onEditorChange={handleEditorChange}
          onGeneratePlotRevisionCandidate={generatePlotRevisionCandidate}
          onLoadVersions={loadVersions}
          onRunPlotDiagnosis={runPlotDiagnosis}
          onRunSlopDiagnosis={runSlopDiagnosis}
          onSelectOutlineScene={handleSceneSelect}
          outlineChapters={outlineDraft?.chapters || []}
          selectedManuscriptId={selectedManuscriptId}
          selectedPlotRun={selectedPlotRun}
          selectedQualityRun={selectedQualityRun}
          selectedSceneId={selectedSceneId}
          sidebarTab={sidebarTab}
          versions={versions}
        />
      ) : (
        <ResizablePanelGroup direction="horizontal" className="h-full rounded-lg border bg-background">
          <ResizablePanel
            ref={leftPanelRef}
            collapsible
            collapsedSize={0}
            defaultSize={leftPanelSize}
            minSize={16}
            maxSize={35}
            onResize={(size) => setLeftPanelSize(Math.round(size))}
          >
            <SceneOutlinePanel
              batchMoveChapterId={batchMoveChapterId}
              chapters={chapters}
              dirtyScenes={dirtyScenes}
              dragOverChapterId={dragOverChapterId}
              dragOverSceneId={dragOverSceneId}
              draggingChapterId={draggingChapterId}
              draggingSceneId={draggingSceneId}
              expandedChapterIds={expandedChapterIds}
              manuscripts={manuscripts}
              onBatchDeleteScenes={batchDeleteScenes}
              onBatchMoveScenes={batchMoveScenes}
              onDeleteScene={deleteSceneFromOutline}
              onHandleSceneSelect={handleSceneSelect}
              onMoveChapter={moveChapter}
              onMoveScene={moveScene}
              onOpenBatchExport={() => {
                setIsSidebarOpen(true);
                setSidebarTab("export");
              }}
              onSelectManuscript={setSelectedManuscriptId}
              onSelectOutline={setSelectedOutlineId}
              onSelectStory={setSelectedStoryId}
              onSetBatchMoveChapterId={setBatchMoveChapterId}
              onSetDragOverChapterId={setDragOverChapterId}
              onSetDragOverSceneId={setDragOverSceneId}
              onSetDraggingChapterId={setDraggingChapterId}
              onSetDraggingSceneId={setDraggingSceneId}
              onSetSceneStatus={setSceneStatus}
              onToggleChapterExpanded={toggleChapterExpanded}
              outlines={outlines}
              sceneStatusClass={sceneStatusClass}
              sceneStatuses={sceneStatuses}
              selectedManuscriptId={selectedManuscriptId}
              selectedOutlineId={selectedOutlineId}
              selectedSceneId={selectedSceneId}
              selectedSceneIds={selectedSceneIds}
              selectedStoryId={selectedStoryId}
              showLeftPanel={showLeftPanel}
              stories={stories}
            />
          </ResizablePanel>
          <ResizableHandle withHandle className={cn(!showLeftPanel && "pointer-events-none opacity-0")} />

        <ResizablePanel minSize={35}>
          <DesktopEditorPanel
            activeGoal={activeGoal}
            content={content}
            currentWordCount={currentWordCount}
            dirtyScenes={dirtyScenes}
            draggingTabId={draggingTabId}
            focusMode={focusMode}
            isGenerating={isGenerating}
            isSaving={isSaving}
            isSidebarOpen={isSidebarOpen}
            lastSavedAt={lastSavedAt}
            onCloseSceneTab={closeSceneTab}
            onEditorChange={handleEditorChange}
            onGenerateScene={async () => {
              if (!selectedManuscriptId || !selectedSceneId) return;
              const sceneId = selectedSceneId;
              setIsGenerating(true);
              try {
                const saved = await api.manuscripts.generateScene(selectedManuscriptId, sceneId);
                setManuscripts((prev) => prev.map((m) => (m.id === saved.id ? saved : m)));
                setContent(saved.sections?.[sceneId] || "");
                const latestRun = await loadSlopQuality(sceneId, saved.id).catch(() => null);
                await loadPlotQuality(sceneId, saved.id).catch(() => ({ run: null, trend: null }));
                toast({
                  title: "已生成场景正文",
                  description: qualityStatusText(latestRun),
                });
              } catch (e: any) {
                toast({ variant: "destructive", title: "生成失败", description: e.message });
              } finally {
                setIsGenerating(false);
              }
            }}
            onHandleManualSave={handleManualSave}
            onOpenVersionPanel={() => {
              setIsSidebarOpen(true);
              setSidebarTab("version");
            }}
            onReorderOpenTabs={reorderOpenTabs}
            onSelectScene={setSelectedSceneId}
            onSetDraggingTabId={setDraggingTabId}
            onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
            openSceneIds={openSceneIds}
            sceneMap={sceneMap}
            selectedManuscriptId={selectedManuscriptId}
            selectedPlotRun={selectedPlotRun}
            selectedQualityRun={selectedQualityRun}
            selectedSceneId={selectedSceneId}
            selectedWordCount={selectedWordCount}
            sessionDurationSeconds={sessionDurationSeconds}
            sessionNetWords={sessionNetWords}
          />
        </ResizablePanel>

            <ResizableHandle withHandle className={cn(!showRightPanel && "pointer-events-none opacity-0")} />
            <ResizablePanel
              ref={rightPanelRef}
              collapsible
              collapsedSize={0}
              defaultSize={rightPanelSize}
              minSize={20}
              maxSize={45}
              onResize={(size) => setRightPanelSize(Math.round(size))}
            >
              <DesktopSidebarPanel
                aiDiffSummary={aiDiffSummary}
                applyPlotRevision={applyPlotRevision}
                autoSaveConfig={autoSaveConfig}
                branches={branches}
                chapterRange={chapterRange}
                checkoutBranch={checkoutBranch}
                contextData={contextData}
                contextPreview={contextPreview}
                copySlopRewriteTask={copySlopRewriteTask}
                createBranch={createBranch}
                createExportJob={createExportJob}
                createGoal={createGoal}
                createManualVersion={createManualVersion}
                createTemplate={createTemplate}
                currentBranchId={currentBranchId}
                dailyHeatmap={dailyHeatmap}
                deleteGoal={deleteGoal}
                deleteTemplate={deleteTemplate}
                diffResult={diffResult}
                diffViewMode={diffViewMode}
                exportAuthorName={exportAuthorName}
                exportFormat={exportFormat}
                exportJobs={exportJobs}
                exportTemplateId={exportTemplateId}
                exportTemplates={exportTemplates}
                generatePlotRevisionCandidate={generatePlotRevisionCandidate}
                goalTargetValue={goalTargetValue}
                goalType={goalType}
                goals={goals}
                hasMoreVersions={hasMoreVersions}
                includeTableOfContents={includeTableOfContents}
                includeTitlePage={includeTitlePage}
                isPlotBusy={isPlotBusy}
                isPlotRevisionBusy={isPlotRevisionBusy}
                isSlopBusy={isSlopBusy}
                loadContextPreview={loadContextPreview}
                loadPlotQuality={loadPlotQuality}
                loadStats={loadStats}
                loadVersions={loadVersions}
                mergeBranchId={mergeBranchId}
                mergeConflicts={mergeConflicts}
                mergeSelectedBranch={mergeSelectedBranch}
                mergeStrategy={mergeStrategy}
                newBranchName={newBranchName}
                onChangeSidebarTab={setSidebarTab}
                plotDimensionEntries={plotDimensionEntries}
                plotTrend={plotTrend}
                plotTrendChartData={plotTrendChartData}
                rollbackVersion={rollbackVersion}
                runPlotDiagnosis={runPlotDiagnosis}
                runSlopDiagnosis={runSlopDiagnosis}
                runVersionDiff={runVersionDiff}
                saveAutoSaveConfig={saveAutoSaveConfig}
                sceneResolutions={sceneResolutions}
                selectedDiffVersions={selectedDiffVersions}
                selectedManuscriptId={selectedManuscriptId}
                selectedPlotRun={selectedPlotRun}
                selectedQualityRun={selectedQualityRun}
                selectedSceneId={selectedSceneId}
                selectedSceneTitle={sceneMap[selectedSceneId]?.scene?.title || ""}
                setAutoSaveConfig={setAutoSaveConfig}
                setChapterRange={setChapterRange}
                setDiffViewMode={setDiffViewMode}
                setExportAuthorName={setExportAuthorName}
                setExportFormat={setExportFormat}
                setExportTemplateId={setExportTemplateId}
                setGoalTargetValue={setGoalTargetValue}
                setGoalType={setGoalType}
                setIncludeTableOfContents={setIncludeTableOfContents}
                setIncludeTitlePage={setIncludeTitlePage}
                setMergeBranchId={setMergeBranchId}
                setMergeStrategy={setMergeStrategy}
                setNewBranchName={setNewBranchName}
                setSceneResolutions={setSceneResolutions}
                setTemplateDescription={setTemplateDescription}
                setTemplateName={setTemplateName}
                setTxtEncoding={setTxtEncoding}
                setVersionVisibleCount={setVersionVisibleCount}
                showRightPanel={showRightPanel}
                sidebarTab={sidebarTab}
                summarizeDiff={summarizeDiff}
                templateDescription={templateDescription}
                templateName={templateName}
                toggleVersionSelection={toggleVersionSelection}
                txtEncoding={txtEncoding}
                updateGoal={updateGoal}
                updateTemplate={updateTemplate}
                versionPageSize={versionPageSize}
                visibleVersions={visibleVersions}
                workspaceStats={workspaceStats}
              />
            </ResizablePanel>
        </ResizablePanelGroup>
      )}

      <WorkbenchOverlays
        characters={characters}
        commandQuery={commandQuery}
        focusMode={focusMode}
        isCommandOpen={isCommandOpen}
        isMobile={isMobile}
        leftPanelOpen={leftPanelOpen}
        onChangeCommandOpen={setIsCommandOpen}
        onChangeCommandQuery={setCommandQuery}
        onHandleManualSave={handleManualSave}
        onJumpScene={jumpScene}
        onOpenCharacterContext={(name) => {
          setIsSidebarOpen(true);
          setSidebarTab("context");
          setCommandQuery(name);
        }}
        onSelectCommandScene={setSelectedSceneId}
        onToggleFocusMode={toggleFocusMode}
        onToggleLeftPanelOpen={() => setLeftPanelOpen((prev) => !prev)}
        onToggleSidebarOpen={() => setIsSidebarOpen((prev) => !prev)}
        sceneRows={sceneRows.map((row) => ({ id: row.id, displayName: row.displayName }))}
        shortcuts={shortcuts}
      />
    </div>
  );
};

export default ManuscriptWriter;
