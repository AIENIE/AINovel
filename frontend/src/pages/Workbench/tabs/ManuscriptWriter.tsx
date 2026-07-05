import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ImperativePanelHandle } from "react-resizable-panels";
import TiptapEditor from "@/components/editor/TiptapEditor";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import {
  Download,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { api } from "@/lib/api-client";
import { Manuscript, Outline, PlotQualityRun, PlotQualityTrend, SlopQualityRun, Story } from "@/types";
import { useToast } from "@/components/ui/use-toast";
import { ShortcutAction } from "@/lib/shortcuts";
import { useWorkbenchLayoutPersistence } from "@/pages/Workbench/hooks/useWorkbenchLayoutPersistence";
import { useManuscriptShortcuts } from "@/pages/Workbench/hooks/useManuscriptShortcuts";
import { useWorkbenchViewport } from "@/pages/Workbench/hooks/useWorkbenchViewport";
import { useWritingSession } from "@/pages/Workbench/hooks/useWritingSession";
import { PlotSidebarPanel } from "./manuscript-writer/PlotSidebarPanel";
import { VersionSidebarPanel } from "./manuscript-writer/VersionSidebarPanel";
import { ContextSidebarPanel } from "./manuscript-writer/ContextSidebarPanel";
import { ExportSidebarPanel } from "./manuscript-writer/ExportSidebarPanel";
import { StatsSidebarPanel } from "./manuscript-writer/StatsSidebarPanel";
import { GoalsSidebarPanel } from "./manuscript-writer/GoalsSidebarPanel";
import { SceneOutlinePanel } from "./manuscript-writer/SceneOutlinePanel";
import { MobileWorkbenchPanel } from "./manuscript-writer/MobileWorkbenchPanel";
import { DesktopEditorPanel } from "./manuscript-writer/DesktopEditorPanel";
import { DesktopSidebarPanel } from "./manuscript-writer/DesktopSidebarPanel";
import { WorkbenchOverlays } from "./manuscript-writer/WorkbenchOverlays";
import {
  countWords,
  plotStatusText,
  qualityStatusText,
  slopRewriteTaskTitle,
  stripHtml,
} from "./manuscript-writer/shared";

interface ManuscriptWriterProps {
  initialStoryId?: string;
}

type SidebarTab = "copilot" | "context" | "version" | "export" | "stats" | "goals" | "plot";
type SceneStatus = "todo" | "in_progress" | "done";

const sceneStatusClass: Record<SceneStatus, string> = {
  todo: "bg-zinc-300",
  in_progress: "bg-amber-400",
  done: "bg-emerald-500",
};

const VERSION_PAGE_SIZE = 10;

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
  const [qualityRunsByScene, setQualityRunsByScene] = useState<Record<string, SlopQualityRun | null>>({});
  const [plotRunsByScene, setPlotRunsByScene] = useState<Record<string, PlotQualityRun | null>>({});
  const [plotTrend, setPlotTrend] = useState<PlotQualityTrend | null>(null);
  const [isSlopBusy, setIsSlopBusy] = useState(false);
  const [isPlotBusy, setIsPlotBusy] = useState(false);
  const [isPlotRevisionBusy, setIsPlotRevisionBusy] = useState(false);
  const [characters, setCharacters] = useState<any[]>([]);
  const [contextPreview, setContextPreview] = useState<any>(null);
  const [versions, setVersions] = useState<any[]>([]);
  const [branches, setBranches] = useState<any[]>([]);
  const [currentBranchId, setCurrentBranchId] = useState("");
  const [newBranchName, setNewBranchName] = useState("");
  const [mergeBranchId, setMergeBranchId] = useState("");
  const [mergeStrategy, setMergeStrategy] = useState<"REPLACE_ALL" | "SCENE_SELECT">("REPLACE_ALL");
  const [mergeConflicts, setMergeConflicts] = useState<any[]>([]);
  const [sceneResolutions, setSceneResolutions] = useState<Record<string, "target" | "source">>({});
  const [selectedDiffVersions, setSelectedDiffVersions] = useState<string[]>([]);
  const [diffResult, setDiffResult] = useState<any>(null);
  const [diffViewMode, setDiffViewMode] = useState<"split" | "unified">("split");
  const [versionVisibleCount, setVersionVisibleCount] = useState(VERSION_PAGE_SIZE);
  const [aiDiffSummary, setAiDiffSummary] = useState("");
  const [autoSaveConfig, setAutoSaveConfig] = useState<any>(null);
  const [exportJobs, setExportJobs] = useState<any[]>([]);
  const [exportTemplates, setExportTemplates] = useState<any[]>([]);
  const [exportFormat, setExportFormat] = useState("txt");
  const [exportTemplateId, setExportTemplateId] = useState("");
  const [templateName, setTemplateName] = useState("");
  const [templateDescription, setTemplateDescription] = useState("");
  const [workspaceStats, setWorkspaceStats] = useState<any>(null);
  const [goals, setGoals] = useState<any[]>([]);
  const [goalType, setGoalType] = useState("daily_words");
  const [goalTargetValue, setGoalTargetValue] = useState(2000);
  const [batchMoveChapterId, setBatchMoveChapterId] = useState("");
  const [draggingChapterId, setDraggingChapterId] = useState("");
  const [dragOverChapterId, setDragOverChapterId] = useState("");
  const [draggingSceneId, setDraggingSceneId] = useState("");
  const [dragOverSceneId, setDragOverSceneId] = useState("");
  const [draggingTabId, setDraggingTabId] = useState("");
  const [chapterRange, setChapterRange] = useState("");
  const [includeTitlePage, setIncludeTitlePage] = useState(true);
  const [includeTableOfContents, setIncludeTableOfContents] = useState(true);
  const [txtEncoding, setTxtEncoding] = useState("UTF-8");
  const [exportAuthorName, setExportAuthorName] = useState("");
  const [mobilePane, setMobilePane] = useState<"outline" | "editor" | "sidebar">("editor");
  const saveTimer = useRef<Record<string, number>>({});
  const leftPanelRef = useRef<ImperativePanelHandle | null>(null);
  const rightPanelRef = useRef<ImperativePanelHandle | null>(null);
  const leftPanelVisibleRef = useRef<boolean | null>(null);
  const rightPanelVisibleRef = useRef<boolean | null>(null);
  const focusRestoreRef = useRef<{ leftOpen: boolean; rightOpen: boolean } | null>(null);
  const lastSelectedSceneRef = useRef("");

  const {
    activeLayoutId,
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

  const selectedStory = useMemo(() => stories.find((s) => s.id === selectedStoryId) || null, [stories, selectedStoryId]);
  const selectedOutline = useMemo(() => outlines.find((o) => o.id === selectedOutlineId) || null, [outlines, selectedOutlineId]);
  const selectedManuscript = useMemo(() => manuscripts.find((m) => m.id === selectedManuscriptId) || null, [manuscripts, selectedManuscriptId]);
  const selectedQualityRun = selectedSceneId ? qualityRunsByScene[selectedSceneId] : null;
  const selectedPlotRun = selectedSceneId ? plotRunsByScene[selectedSceneId] : null;
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
  const visibleVersions = useMemo(() => versions.slice(0, versionVisibleCount), [versionVisibleCount, versions]);
  const hasMoreVersions = versionVisibleCount < versions.length;
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

  const loadGoals = useCallback(async () => {
    try {
      setGoals(await api.v2.workspace.listGoals());
    } catch {
      setGoals([]);
    }
  }, []);

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
    void loadGoals();
  }, [loadGoals, loadStories]);

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
    if (!selectedManuscriptId || !selectedSceneId) return;
    let cancelled = false;
    api.v2.quality
      .listRuns(selectedManuscriptId, selectedSceneId)
      .then((runs) => {
        if (cancelled) return;
        setQualityRunsByScene((prev) => ({ ...prev, [selectedSceneId]: runs[0] || null }));
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [selectedManuscriptId, selectedSceneId]);

  useEffect(() => {
    if (!selectedManuscriptId || !selectedSceneId) {
      setPlotTrend(null);
      return;
    }
    let cancelled = false;
    Promise.all([
      api.v2.plotQuality.listRuns(selectedManuscriptId, selectedSceneId),
      api.v2.plotQuality.getTrend(selectedManuscriptId),
    ])
      .then(([runs, trend]) => {
        if (cancelled) return;
        setPlotRunsByScene((prev) => ({ ...prev, [selectedSceneId]: runs[0] || null }));
        setPlotTrend(trend);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [selectedManuscriptId, selectedSceneId]);

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

  const loadContextPreview = useCallback(async () => {
    if (!selectedStoryId) return;
    try {
      setContextPreview(await api.v2.context.previewContext(selectedStoryId, 1200));
    } catch (e: any) {
      toast({ variant: "destructive", title: "加载上下文失败", description: e.message });
    }
  }, [selectedStoryId, toast]);

  const loadVersions = useCallback(async () => {
    if (!selectedManuscriptId) return;
    try {
      const [versionList, config, branchList] = await Promise.all([
        api.v2.version.listVersions(selectedManuscriptId),
        api.v2.version.getAutoSave(),
        api.v2.version.listBranches(selectedManuscriptId),
      ]);
      setVersions(versionList);
      setVersionVisibleCount(VERSION_PAGE_SIZE);
      setAutoSaveConfig(config);
      setBranches(branchList);
      const active = branchList.find((branch) => String(branch.status) === "active" && branch.isMain);
      if (active) setCurrentBranchId(String(active.id));
      if (!mergeBranchId && branchList.find((branch) => !branch.isMain && branch.status === "active")) {
        const candidate = branchList.find((branch) => !branch.isMain && branch.status === "active");
        setMergeBranchId(String(candidate?.id || ""));
      }
    } catch (e: any) {
      toast({ variant: "destructive", title: "加载版本失败", description: e.message });
    }
  }, [mergeBranchId, selectedManuscriptId, toast]);

  const createManualVersion = useCallback(async () => {
    if (!selectedManuscriptId) return;
    const label = window.prompt("检查点标签", `manual-${Date.now()}`)?.trim();
    if (!label) return;
    await api.v2.version.createVersion(selectedManuscriptId, { snapshotType: "manual", label });
    await loadVersions();
  }, [loadVersions, selectedManuscriptId]);

  const saveAutoSaveConfig = useCallback(async () => {
    if (!autoSaveConfig) return;
    await api.v2.version.updateAutoSave({
      autoSaveIntervalSeconds: Number(autoSaveConfig.autoSaveIntervalSeconds || 300),
      maxAutoVersions: Number(autoSaveConfig.maxAutoVersions || 100),
    });
    toast({ title: "自动快照配置已更新" });
  }, [autoSaveConfig, toast]);

  const loadExport = useCallback(async () => {
    if (!selectedManuscriptId) return;
    try {
      const [jobs, templates] = await Promise.all([api.v2.export.listJobs(selectedManuscriptId), api.v2.export.listTemplates()]);
      setExportJobs(jobs);
      setExportTemplates(templates);
      if (!exportTemplateId && templates[0]) setExportTemplateId(String(templates[0].id));
    } catch (e: any) {
      toast({ variant: "destructive", title: "加载导出信息失败", description: e.message });
    }
  }, [exportTemplateId, selectedManuscriptId, toast]);

  useEffect(() => {
    const hasRunningExportJob = exportJobs.some((job) => {
      const status = String(job.status || "").toLowerCase();
      return !["completed", "failed", "expired", "cancelled"].includes(status);
    });
    if (!selectedManuscriptId || !hasRunningExportJob) return;
    const timer = window.setInterval(() => {
      void loadExport();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [exportJobs, loadExport, selectedManuscriptId]);

  const loadStats = useCallback(async () => {
    try {
      setWorkspaceStats(await api.v2.workspace.getStats());
    } catch (e: any) {
      toast({ variant: "destructive", title: "加载统计失败", description: e.message });
    }
  }, [toast]);

  useEffect(() => {
    if (!isSidebarOpen) return;
    if (sidebarTab === "context") void loadContextPreview();
    if (sidebarTab === "version") void loadVersions();
    if (sidebarTab === "export") void loadExport();
    if (sidebarTab === "stats") void loadStats();
    if (sidebarTab === "goals") void loadGoals();
  }, [isSidebarOpen, sidebarTab, loadContextPreview, loadVersions, loadExport, loadStats, loadGoals]);

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

  const toggleVersionSelection = (versionId: string) => {
    setSelectedDiffVersions((prev) => {
      if (prev.includes(versionId)) return prev.filter((id) => id !== versionId);
      if (prev.length >= 2) return [prev[1], versionId];
      return [...prev, versionId];
    });
  };

  const runVersionDiff = async () => {
    if (!selectedManuscriptId || selectedDiffVersions.length !== 2) return;
    try {
      const [fromVersionId, toVersionId] = selectedDiffVersions;
      setDiffResult(await api.v2.version.getDiff(selectedManuscriptId, fromVersionId, toVersionId));
      setAiDiffSummary("");
    } catch (e: any) {
      toast({ variant: "destructive", title: "对比失败", description: e.message });
    }
  };

  const summarizeDiff = async () => {
    if (!diffResult) return;
    const localSummary = (() => {
      const changes = Array.isArray(diffResult?.changes) ? diffResult.changes : [];
      if (!changes.length) return "两个版本内容一致，无显著改动。";
      let added = 0;
      let removed = 0;
      changes.forEach((item: any) => {
        const beforeWords = Number(item?.beforeWordCount ?? 0);
        const afterWords = Number(item?.afterWordCount ?? 0);
        const delta = afterWords - beforeWords;
        if (delta >= 0) added += delta;
        else removed += Math.abs(delta);
      });
      const sceneNames = changes
        .slice(0, 3)
        .map((item: any) => String(item?.sceneId || "场景"))
        .join("、");
      const suffix = changes.length > 3 ? "等" : "";
      return `共变更 ${changes.length} 个场景（${sceneNames}${suffix}），约新增 ${added} 词、减少 ${removed} 词，主要集中在段落措辞与细节调整。`;
    })();
    try {
      const models = await api.ai.getModels();
      const modelId = models[0]?.id;
      if (!modelId) throw new Error("暂无可用 AI 模型");
      const prompt = ["请用100字内总结以下改动：", JSON.stringify((diffResult.changes || []).slice(0, 6), null, 2)].join("\n");
      const result = await api.ai.chat([{ role: "user", content: prompt }], modelId, { manuscriptId: selectedManuscriptId });
      setAiDiffSummary(result.content || localSummary);
    } catch (e: any) {
      setAiDiffSummary(localSummary);
      toast({ title: "AI 不可用，已使用本地变更摘要", description: e.message });
    }
  };

  const createExportJob = async () => {
    if (!selectedManuscriptId) return;
    const normalizedChapterRange = chapterRange.trim();
    if (normalizedChapterRange && !/^\d+(-\d+)?$/.test(normalizedChapterRange)) {
      toast({ variant: "destructive", title: "章节范围格式错误", description: "请使用“3-7”或“5”格式" });
      return;
    }
    try {
      await api.v2.export.createJob(selectedManuscriptId, {
        format: exportFormat,
        templateId: exportTemplateId || undefined,
        chapterRange: normalizedChapterRange || undefined,
        config: {
          includeTitlePage,
          includeTableOfContents,
          includeToc: includeTableOfContents,
          txtEncoding,
          authorName: exportAuthorName.trim() || undefined,
          selectedSceneIds: selectedSceneIds.length > 1 ? selectedSceneIds : undefined,
        },
      });
      toast({ title: "导出任务已创建" });
      await loadExport();
    } catch (e: any) {
      toast({ variant: "destructive", title: "导出失败", description: e.message });
    }
  };

  const createGoal = async () => {
    try {
      await api.v2.workspace.createGoal({
        storyId: selectedStoryId || null,
        goalType,
        targetValue: Number(goalTargetValue || 0),
        status: "active",
      });
      await loadGoals();
      toast({ title: "目标已创建" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "创建目标失败", description: e.message });
    }
  };

  const updateGoal = async (goalId: string, patch: Record<string, unknown>) => {
    try {
      await api.v2.workspace.updateGoal(goalId, patch);
      await loadGoals();
      toast({ title: "目标已更新" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "更新目标失败", description: e.message });
    }
  };

  const deleteGoal = async (goalId: string) => {
    try {
      await api.v2.workspace.deleteGoal(goalId);
      await loadGoals();
      toast({ title: "目标已删除" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "删除目标失败", description: e.message });
    }
  };

  const createBranch = async () => {
    if (!selectedManuscriptId) return;
    const fromVersionId = selectedDiffVersions[0] || versions[0]?.id;
    const name = (newBranchName || `branch-${Date.now().toString().slice(-6)}`).trim();
    if (!name) return;
    try {
      await api.v2.version.createBranch(selectedManuscriptId, {
        name,
        sourceVersionId: fromVersionId,
      });
      setNewBranchName("");
      await loadVersions();
      toast({ title: "分支创建成功" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "创建分支失败", description: e.message });
    }
  };

  const applyFetchedManuscript = useCallback((manuscript: Manuscript) => {
    setManuscripts((prev) => prev.map((item) => (item.id === manuscript.id ? manuscript : item)));
    setSceneDrafts(manuscript.sections || {});
    setDirtyScenes({});
  }, []);

  const loadSlopQuality = useCallback(
    async (sceneId = selectedSceneId) => {
      if (!selectedManuscriptId || !sceneId) return;
      const runs = await api.v2.quality.listRuns(selectedManuscriptId, sceneId);
      setQualityRunsByScene((prev) => ({ ...prev, [sceneId]: runs[0] || null }));
    },
    [selectedManuscriptId, selectedSceneId],
  );

  const runSlopDiagnosis = async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    setIsSlopBusy(true);
    try {
      if (dirtyScenes[sceneId]) {
        await persistSection(sceneId, content, true);
      }
      const run = await api.v2.quality.analyzeScene(selectedManuscriptId, sceneId);
      setQualityRunsByScene((prev) => ({ ...prev, [sceneId]: run }));
      setSidebarTab("plot");
      toast({ title: "文本 Slop 诊断已完成", description: run.safeClaim || qualityStatusText(run) });
    } catch (e: any) {
      toast({ variant: "destructive", title: "文本诊断失败", description: e.message });
    } finally {
      setIsSlopBusy(false);
    }
  };

  const copySlopRewriteTask = async (task: any, index: number) => {
    const title = slopRewriteTaskTitle(task, index);
    const lines = [
      `任务 ${title}`,
      task?.problem ? `问题：${task.problem}` : "",
      task?.repair_goal || task?.repairGoal ? `修复目标：${task.repair_goal || task.repairGoal}` : "",
      Array.isArray(task?.constraints) && task.constraints.length ? `约束：${task.constraints.join("；")}` : "",
      "请只针对目标片段做证据驱动改写，不要改变关键设定、人物关系和场景核心事件。",
    ].filter(Boolean);
    try {
      await navigator.clipboard.writeText(lines.join("\n"));
      toast({ title: "改写任务已复制" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "复制失败", description: e.message });
    }
  };

  const loadPlotQuality = useCallback(
    async (sceneId = selectedSceneId) => {
      if (!selectedManuscriptId || !sceneId) return;
      const [runs, trend] = await Promise.all([
        api.v2.plotQuality.listRuns(selectedManuscriptId, sceneId),
        api.v2.plotQuality.getTrend(selectedManuscriptId),
      ]);
      setPlotRunsByScene((prev) => ({ ...prev, [sceneId]: runs[0] || null }));
      setPlotTrend(trend);
    },
    [selectedManuscriptId, selectedSceneId],
  );

  const runPlotDiagnosis = async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    setIsPlotBusy(true);
    try {
      if (dirtyScenes[sceneId]) {
        await persistSection(sceneId, content, true);
      }
      const run = await api.v2.plotQuality.analyzeScene(selectedManuscriptId, sceneId);
      setPlotRunsByScene((prev) => ({ ...prev, [sceneId]: run }));
      const trend = await api.v2.plotQuality.getTrend(selectedManuscriptId);
      setPlotTrend(trend);
      setSidebarTab("plot");
      toast({ title: "剧情诊断已完成", description: plotStatusText(run) });
    } catch (e: any) {
      toast({ variant: "destructive", title: "剧情诊断失败", description: e.message });
    } finally {
      setIsPlotBusy(false);
    }
  };

  const generatePlotRevisionCandidate = async () => {
    if (!selectedManuscriptId || !selectedSceneId || !selectedPlotRun) return;
    const sceneId = selectedSceneId;
    setIsPlotRevisionBusy(true);
    try {
      if (dirtyScenes[sceneId]) {
        await persistSection(sceneId, content, true);
      }
      const run = await api.v2.plotQuality.generateRevisionCandidate(selectedManuscriptId, selectedPlotRun.id);
      setPlotRunsByScene((prev) => ({ ...prev, [sceneId]: run }));
      toast({ title: "候选修订已生成" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "生成候选失败", description: e.message });
    } finally {
      setIsPlotRevisionBusy(false);
    }
  };

  const applyPlotRevision = async () => {
    if (!selectedManuscriptId || !selectedSceneId || !selectedPlotRun) return;
    const sceneId = selectedSceneId;
    setIsPlotRevisionBusy(true);
    try {
      if (dirtyScenes[sceneId]) {
        await persistSection(sceneId, content, true);
      }
      const run = await api.v2.plotQuality.applyRevision(selectedManuscriptId, selectedPlotRun.id);
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      setContent(manuscript.sections?.[sceneId] || "");
      setPlotRunsByScene((prev) => ({ ...prev, [sceneId]: run }));
      await loadPlotQuality(sceneId);
      toast({ title: "候选修订已采纳" });
    } catch (e: any) {
      toast({
        variant: "destructive",
        title: "采纳候选失败",
        description: String(e.message || "").includes("409") ? "场景内容已变化，请重新诊断后再生成候选。" : e.message,
      });
    } finally {
      setIsPlotRevisionBusy(false);
    }
  };

  const checkoutBranch = async (branchId: string) => {
    if (!selectedManuscriptId) return;
    try {
      const result = await api.v2.version.checkoutBranch(selectedManuscriptId, branchId);
      setCurrentBranchId(String(result.currentBranchId || branchId));
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      await loadVersions();
      toast({ title: "分支已切换" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "切换分支失败", description: e.message });
    }
  };

  const rollbackVersion = async (versionId: string) => {
    if (!selectedManuscriptId) return;
    try {
      await api.v2.version.rollback(selectedManuscriptId, versionId);
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      await loadVersions();
      toast({ title: "回滚成功，已自动备份当前内容" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "回滚失败", description: e.message });
    }
  };

  const mergeSelectedBranch = async (resolutions?: Record<string, "target" | "source">) => {
    if (!selectedManuscriptId || !mergeBranchId) return;
    try {
      const result = await api.v2.version.mergeBranch(selectedManuscriptId, mergeBranchId, {
        strategy: mergeStrategy,
        sceneResolutions: resolutions || sceneResolutions,
      });
      if (String(result.status) === "conflict") {
        setMergeConflicts(result.conflicts || []);
        toast({ variant: "destructive", title: "存在冲突，请先逐场景选择" });
        return;
      }
      setMergeConflicts([]);
      setSceneResolutions({});
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      await loadVersions();
      toast({ title: "分支合并成功" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "合并失败", description: e.message });
    }
  };

  const createTemplate = async () => {
    const name = templateName.trim();
    if (!name) return;
    try {
      await api.v2.export.createTemplate({
        name,
        description: templateDescription,
        format: exportFormat,
        config: {
          includeTitlePage,
          includeTableOfContents,
          includeToc: includeTableOfContents,
          txtEncoding,
          authorName: exportAuthorName.trim() || undefined,
          lineSpacing: 1.5,
        },
      });
      setTemplateName("");
      setTemplateDescription("");
      await loadExport();
      toast({ title: "模板创建成功" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "创建模板失败", description: e.message });
    }
  };

  const updateTemplate = async (template: any) => {
    try {
      await api.v2.export.updateTemplate(String(template.id), {
        name: template.name,
        description: template.description || "",
        format: template.format || exportFormat,
        config: template.config || {},
      });
      await loadExport();
      toast({ title: "模板已更新" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "更新模板失败", description: e.message });
    }
  };

  const deleteTemplate = async (templateId: string) => {
    try {
      await api.v2.export.deleteTemplate(templateId);
      await loadExport();
      toast({ title: "模板已删除" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "删除模板失败", description: e.message });
    }
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
          onChangeSidebarTab={(value) => setSidebarTab(value as SidebarTab)}
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
                const qualityRuns = await api.v2.quality.listRuns(saved.id, sceneId).catch(() => []);
                const latestRun = qualityRuns[0] || null;
                setQualityRunsByScene((prev) => ({ ...prev, [sceneId]: latestRun }));
                const plotRuns = await api.v2.plotQuality.listRuns(saved.id, sceneId).catch(() => []);
                setPlotRunsByScene((prev) => ({ ...prev, [sceneId]: plotRuns[0] || null }));
                api.v2.plotQuality.getTrend(saved.id).then(setPlotTrend).catch(() => undefined);
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
                onChangeSidebarTab={(value) => setSidebarTab(value as SidebarTab)}
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
                versionPageSize={VERSION_PAGE_SIZE}
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
