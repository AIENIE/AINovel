import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ImperativePanelHandle } from "react-resizable-panels";
import TiptapEditor from "@/components/editor/TiptapEditor";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator, CommandShortcut } from "@/components/ui/command";
import { Progress } from "@/components/ui/progress";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import {
  Save,
  History,
  PanelRightOpen,
  PanelRightClose,
  Sparkles,
  Loader2,
  X,
  ChevronDown,
  ChevronRight,
  PanelLeftClose,
  PanelLeftOpen,
  Focus,
  Download,
  Flag,
  Split,
  ArrowDownUp,
  RotateCcw,
  GitBranch,
  GripVertical,
  Clock3,
} from "lucide-react";
import CopilotSidebar from "@/components/ai/CopilotSidebar";
import { cn } from "@/lib/utils";
import { api } from "@/lib/mock-api";
import { Manuscript, Outline, Story } from "@/types";
import { useToast } from "@/components/ui/use-toast";
import { ShortcutAction, ShortcutMap, DEFAULT_SHORTCUTS, detectShortcutConflicts, matchesShortcut, isEditableTarget } from "@/lib/shortcuts";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from "@/components/ui/context-menu";

interface ManuscriptWriterProps {
  initialStoryId?: string;
}

type SidebarTab = "copilot" | "context" | "version" | "export" | "stats" | "goals";
type SceneStatus = "todo" | "in_progress" | "done";

const sceneStatusClass: Record<SceneStatus, string> = {
  todo: "bg-zinc-300",
  in_progress: "bg-amber-400",
  done: "bg-emerald-500",
};

const formatDateTime = (value: any) => {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
};

const stripHtml = (html: string) => {
  const div = document.createElement("div");
  div.innerHTML = html || "";
  return (div.textContent || div.innerText || "").trim();
};

const countWords = (text: string) => {
  if (!text) return 0;
  return text.replace(/\s+/g, "").trim().length;
};

const VERSION_PAGE_SIZE = 10;

const versionWordCount = (version: any) => {
  const fromMeta = Number(version?.metadata?.word_count ?? version?.metadata?.wordCount);
  if (Number.isFinite(fromMeta) && fromMeta >= 0) return Math.round(fromMeta);
  try {
    const sections = typeof version?.sectionsJson === "string" ? JSON.parse(version.sectionsJson) : version?.sectionsJson;
    if (!sections || typeof sections !== "object") return 0;
    return Object.values(sections).reduce((total, scene) => total + countWords(stripHtml(String(scene || ""))), 0);
  } catch {
    return 0;
  }
};

const snapshotTypeLabel = (snapshotType: any) => {
  const type = String(snapshotType || "manual").toLowerCase();
  if (type === "auto") return "自动";
  if (type === "branch_point") return "分支点";
  if (type === "merge") return "合并";
  return "手动";
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

const WORKBENCH_LAYOUT_KEY = "ainovel.workbench.layout.v2";

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
  const [leftPanelOpen, setLeftPanelOpen] = useState(true);
  const [leftPanelSize, setLeftPanelSize] = useState(24);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [rightPanelSize, setRightPanelSize] = useState(30);
  const [sidebarTab, setSidebarTab] = useState<SidebarTab>("copilot");
  const [focusMode, setFocusMode] = useState(false);
  const [isCommandOpen, setIsCommandOpen] = useState(false);
  const [commandQuery, setCommandQuery] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState<string>("");
  const [isGenerating, setIsGenerating] = useState(false);
  const [selectedWordCount, setSelectedWordCount] = useState(0);
  const [shortcuts, setShortcuts] = useState<ShortcutMap>(DEFAULT_SHORTCUTS);
  const [shortcutConflicts, setShortcutConflicts] = useState<Array<{ shortcut: string; actions: string[] }>>([]);
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
  const [isCompact, setIsCompact] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [mobilePane, setMobilePane] = useState<"outline" | "editor" | "sidebar">("editor");
  const [activeLayoutId, setActiveLayoutId] = useState("");
  const [sessionStartedAt, setSessionStartedAt] = useState<number>(0);
  const [sessionWordsWritten, setSessionWordsWritten] = useState(0);
  const [sessionWordsDeleted, setSessionWordsDeleted] = useState(0);
  const [tick, setTick] = useState(0);
  const saveTimer = useRef<Record<string, number>>({});
  const leftPanelRef = useRef<ImperativePanelHandle | null>(null);
  const rightPanelRef = useRef<ImperativePanelHandle | null>(null);
  const leftPanelVisibleRef = useRef<boolean | null>(null);
  const rightPanelVisibleRef = useRef<boolean | null>(null);
  const focusRestoreRef = useRef<{ leftOpen: boolean; rightOpen: boolean } | null>(null);
  const sessionIdRef = useRef("");
  const sessionWordsWrittenRef = useRef(0);
  const sessionWordsDeletedRef = useRef(0);
  const sceneWordCacheRef = useRef<Record<string, number>>({});
  const lastSelectedSceneRef = useRef("");
  const autoSnapshotRef = useRef(0);
  const layoutSyncTimerRef = useRef<number | null>(null);

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
  const sessionNetWords = sessionWordsWritten - sessionWordsDeleted;
  const sessionDurationSeconds = sessionStartedAt ? Math.max(0, Math.floor((Date.now() - sessionStartedAt) / 1000)) : 0;
  const currentWordCount = useMemo(() => countWords(stripHtml(content)), [content]);
  const chapters = outlineDraft?.chapters || [];
  const activeGoal = goals.find((goal) => String(goal.status || "active").toLowerCase() !== "archived");
  const dailyHeatmap = useMemo(() => (workspaceStats?.dailySeries || []).slice(-30), [workspaceStats]);
  const showLeftPanel = leftPanelOpen && !focusMode;
  const showRightPanel = isSidebarOpen && !focusMode;
  const visibleVersions = useMemo(() => versions.slice(0, versionVisibleCount), [versionVisibleCount, versions]);
  const hasMoreVersions = versionVisibleCount < versions.length;

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

  const layoutCacheKey = useMemo(
    () => `${WORKBENCH_LAYOUT_KEY}:${selectedStoryId || "na"}:${selectedManuscriptId || "na"}`,
    [selectedManuscriptId, selectedStoryId],
  );

  const saveLayoutLocal = useCallback(
    (partial: Record<string, unknown>) => {
      if (!selectedStoryId || !selectedManuscriptId) return;
      const next = {
        leftPanelOpen,
        leftPanelSize,
        rightPanelOpen: isSidebarOpen,
        rightPanelSize,
        sidebarTab,
        ...partial,
      };
      localStorage.setItem(layoutCacheKey, JSON.stringify(next));
    },
    [isSidebarOpen, layoutCacheKey, leftPanelOpen, leftPanelSize, rightPanelSize, selectedManuscriptId, selectedStoryId, sidebarTab],
  );

  const applyLayoutPayload = useCallback((layout: any) => {
    if (!layout || typeof layout !== "object") return;
    const raw = layout.layout && typeof layout.layout === "object" ? layout.layout : layout;
    if (typeof raw.leftPanelOpen === "boolean") setLeftPanelOpen(raw.leftPanelOpen);
    if (typeof raw.leftPanelSize === "number") setLeftPanelSize(raw.leftPanelSize);
    if (typeof raw.rightPanelOpen === "boolean") setIsSidebarOpen(raw.rightPanelOpen);
    if (typeof raw.rightPanelSize === "number") setRightPanelSize(raw.rightPanelSize);
    if (typeof raw.sidebarTab === "string") setSidebarTab(raw.sidebarTab as SidebarTab);
  }, []);

  const buildLayoutPayload = useCallback(
    () => ({
      leftPanelOpen,
      leftPanelSize,
      rightPanelOpen: isSidebarOpen,
      rightPanelSize,
      sidebarTab,
    }),
    [isSidebarOpen, leftPanelOpen, leftPanelSize, rightPanelSize, sidebarTab],
  );

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

  const loadShortcuts = useCallback(async () => {
    try {
      const list = await api.v2.workspace.listShortcuts();
      const merged: ShortcutMap = { ...DEFAULT_SHORTCUTS };
      list.forEach((item: any) => {
        const action = String(item.action || "") as ShortcutAction;
        const shortcut = String(item.shortcut || "");
        if (action && shortcut && action in merged) {
          merged[action] = shortcut;
        }
      });
      setShortcuts(merged);
      setShortcutConflicts(detectShortcutConflicts(Object.entries(merged).map(([action, shortcut]) => ({ action, shortcut }))));
    } catch {
      setShortcuts(DEFAULT_SHORTCUTS);
      setShortcutConflicts([]);
    }
  }, []);

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
    void loadShortcuts();
    void loadGoals();
  }, [loadGoals, loadShortcuts, loadStories]);

  useEffect(() => {
    void loadCharacters();
  }, [loadCharacters]);

  useEffect(() => {
    const handle = () => void loadShortcuts();
    window.addEventListener("ainovel-shortcuts-updated", handle as EventListener);
    return () => window.removeEventListener("ainovel-shortcuts-updated", handle as EventListener);
  }, [loadShortcuts]);

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

  useEffect(() => {
    const syncViewport = () => {
      const width = window.innerWidth;
      setIsMobile(width < 768);
      setIsCompact(width < 1280);
    };
    syncViewport();
    window.addEventListener("resize", syncViewport);
    return () => window.removeEventListener("resize", syncViewport);
  }, []);

  useEffect(() => {
    if (focusMode) return;
    if (isMobile) {
      setLeftPanelOpen(false);
      setIsSidebarOpen(false);
      return;
    }
    if (isCompact) {
      setIsSidebarOpen(false);
    }
  }, [focusMode, isCompact, isMobile]);

  useEffect(() => {
    if (!layoutCacheKey || !selectedManuscriptId || !selectedStoryId) return;
    let cancelled = false;

    const loadLayout = async () => {
      let localApplied = false;
      try {
        const cached = localStorage.getItem(layoutCacheKey);
        if (cached) {
          applyLayoutPayload(JSON.parse(cached));
          localApplied = true;
        }
      } catch {
        // ignore corrupted cache
      }

      try {
        const layouts = await api.v2.workspace.listLayouts();
        if (cancelled) return;
        const activeLayout = layouts.find((layout: any) => Boolean(layout.isActive));
        if (activeLayout?.id) setActiveLayoutId(String(activeLayout.id));
        if (!localApplied && activeLayout?.layout) {
          applyLayoutPayload(activeLayout.layout);
        }
      } catch {
        // ignore server-side layout fetch failures
      }
    };

    void loadLayout();
    return () => {
      cancelled = true;
    };
  }, [applyLayoutPayload, layoutCacheKey, selectedManuscriptId, selectedStoryId]);

  useEffect(() => {
    saveLayoutLocal({});
  }, [isSidebarOpen, leftPanelOpen, leftPanelSize, rightPanelSize, saveLayoutLocal, sidebarTab]);

  useEffect(() => {
    if (!selectedStoryId || !selectedManuscriptId) return;
    if (layoutSyncTimerRef.current) window.clearTimeout(layoutSyncTimerRef.current);
    layoutSyncTimerRef.current = window.setTimeout(() => {
      const payload = buildLayoutPayload();
      const request = activeLayoutId
        ? api.v2.workspace.updateLayout(activeLayoutId, { layout: payload, isActive: true })
        : api.v2.workspace.createLayout({ name: "写作模式", layout: payload, isActive: true });
      void request
        .then((layout: any) => {
          if (layout?.id) setActiveLayoutId(String(layout.id));
        })
        .catch(() => undefined);
    }, 600);

    return () => {
      if (layoutSyncTimerRef.current) window.clearTimeout(layoutSyncTimerRef.current);
    };
  }, [activeLayoutId, buildLayoutPayload, selectedManuscriptId, selectedStoryId]);

  useEffect(() => {
    if (!selectedManuscript || !selectedSceneId) {
      setContent("");
      return;
    }
    const html = sceneDrafts[selectedSceneId] ?? selectedManuscript.sections?.[selectedSceneId] ?? "";
    setContent(html);
    sceneWordCacheRef.current[selectedSceneId] = countWords(stripHtml(html));
  }, [sceneDrafts, selectedManuscript, selectedSceneId]);

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

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.key === "Escape" && focusMode) {
        event.preventDefault();
        exitFocusMode();
        return;
      }
      const matched = (Object.keys(shortcuts) as ShortcutAction[]).find((action) => matchesShortcut(event, shortcuts[action]));
      if (!matched) return;
      if (matched === "undo" || matched === "redo") return;
      const inProseMirror = event.target instanceof HTMLElement && Boolean(event.target.closest(".ProseMirror"));
      if (isEditableTarget(event.target) && !inProseMirror && !["save", "focus_mode", "command_palette"].includes(matched)) return;
      event.preventDefault();
      handleShortcutAction(matched);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [exitFocusMode, focusMode, handleShortcutAction, shortcuts]);

  useEffect(() => {
    const selectionHandler = () => setSelectedWordCount(countWords(window.getSelection()?.toString() || ""));
    document.addEventListener("selectionchange", selectionHandler);
    return () => document.removeEventListener("selectionchange", selectionHandler);
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setTick((prev) => prev + 1), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    sessionWordsWrittenRef.current = sessionWordsWritten;
    sessionWordsDeletedRef.current = sessionWordsDeleted;
  }, [sessionWordsDeleted, sessionWordsWritten]);

  useEffect(() => {
    if (!selectedStoryId) return;
    api.v2.workspace.startSession({ storyId: selectedStoryId }).then((session) => {
      sessionIdRef.current = String(session.id || "");
      setSessionStartedAt(new Date(session.startedAt || Date.now()).getTime());
      setSessionWordsWritten(Number(session.wordsWritten || 0));
      setSessionWordsDeleted(Number(session.wordsDeleted || 0));
    }).catch(() => undefined);
    return () => {
      if (!sessionIdRef.current) return;
      void api.v2.workspace.endSession(sessionIdRef.current, {
        wordsWritten: sessionWordsWrittenRef.current,
        wordsDeleted: sessionWordsDeletedRef.current,
      });
      sessionIdRef.current = "";
    };
  }, [selectedStoryId]);

  useEffect(() => {
    if (!sessionIdRef.current) return;
    const timer = window.setInterval(() => {
      void api.v2.workspace.heartbeatSession(sessionIdRef.current, {
        wordsWritten: sessionWordsWrittenRef.current,
        wordsDeleted: sessionWordsDeletedRef.current,
      });
    }, 30000);
    return () => window.clearInterval(timer);
  }, [selectedStoryId]);

  useEffect(() => {
    const onBeforeUnload = () => {
      if (!sessionIdRef.current) return;
      void api.v2.workspace.endSession(sessionIdRef.current, {
        wordsWritten: sessionWordsWrittenRef.current,
        wordsDeleted: sessionWordsDeletedRef.current,
      });
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
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
      const modelId = models[0]?.id || "fallback-model";
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

  useEffect(() => {
    if (!autoSaveConfig?.autoSaveIntervalSeconds || !selectedManuscriptId || !selectedSceneId) return;
    if (!dirtyScenes[selectedSceneId]) return;
    const intervalMs = Math.max(30000, Number(autoSaveConfig.autoSaveIntervalSeconds) * 1000);
    const now = Date.now();
    if (now - autoSnapshotRef.current < intervalMs) return;
    autoSnapshotRef.current = now;
    void api.v2.version.createVersion(selectedManuscriptId, {
      snapshotType: "auto",
      label: `auto-${new Date().toLocaleTimeString()}`,
    }).catch(() => undefined);
  }, [autoSaveConfig, dirtyScenes, selectedManuscriptId, selectedSceneId, tick]);

  return (
    <div className="relative h-[calc(100vh-180px)]">
      {isMobile ? (
        <Tabs value={mobilePane} onValueChange={(value) => setMobilePane(value as "outline" | "editor" | "sidebar")} className="h-full rounded-lg border bg-background p-2">
          <TabsList className="grid grid-cols-3">
            <TabsTrigger value="outline">大纲</TabsTrigger>
            <TabsTrigger value="editor">编辑</TabsTrigger>
            <TabsTrigger value="sidebar">参考</TabsTrigger>
          </TabsList>
          <TabsContent value="outline" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
            <ScrollArea className="h-full rounded border p-2">
              {(outlineDraft?.chapters || []).map((chapter, ci) => (
                <div key={chapter.id} className="mb-2">
                  <div className="text-xs font-semibold text-muted-foreground mb-1">{`第${ci + 1}章 ${chapter.title}`}</div>
                  <div className="space-y-1">
                    {chapter.scenes.map((scene, si) => (
                      <button
                        key={scene.id}
                        className={cn("w-full rounded border px-2 py-1 text-left text-xs", selectedSceneId === scene.id ? "bg-secondary border-primary/40" : "hover:bg-muted")}
                        onClick={(event) => {
                          handleSceneSelect(scene.id, event);
                          setMobilePane("editor");
                        }}
                      >
                        {`Sc.${si + 1} ${scene.title}`}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </ScrollArea>
          </TabsContent>
          <TabsContent value="editor" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
            <div className="h-full border rounded overflow-hidden">
              <TiptapEditor
                key={`mobile-editor-${focusMode ? "zen" : "normal"}`}
                content={content}
                onChange={(html) => {
                  setContent(html);
                  if (!selectedSceneId) return;
                  const previousWordCount = sceneWordCacheRef.current[selectedSceneId] ?? 0;
                  const nextWordCount = countWords(stripHtml(html));
                  const delta = nextWordCount - previousWordCount;
                  sceneWordCacheRef.current[selectedSceneId] = nextWordCount;
                  if (delta > 0) setSessionWordsWritten((value) => value + delta);
                  if (delta < 0) setSessionWordsDeleted((value) => value + Math.abs(delta));
                  setSceneDrafts((prev) => ({ ...prev, [selectedSceneId]: html }));
                  setDirtyScenes((prev) => ({ ...prev, [selectedSceneId]: true }));
                  scheduleSave(selectedSceneId, html);
                }}
                className="h-full"
                editable={!!selectedSceneId}
                zenMode={focusMode}
              />
            </div>
          </TabsContent>
          <TabsContent value="sidebar" className="h-[calc(100%-3rem)] m-0 mt-2 min-h-0">
            <Tabs value={sidebarTab} onValueChange={(value) => setSidebarTab(value as SidebarTab)} className="h-full flex flex-col">
              <TabsList className="grid grid-cols-3">
                <TabsTrigger value="copilot">AI</TabsTrigger>
                <TabsTrigger value="version">版本</TabsTrigger>
                <TabsTrigger value="export">导出</TabsTrigger>
              </TabsList>
              <TabsContent value="copilot" className="flex-1 m-0 mt-2 min-h-0">
                <CopilotSidebar context={contextData} className="h-full border-none" />
              </TabsContent>
              <TabsContent value="version" className="flex-1 m-0 mt-2 min-h-0 rounded border p-2 text-xs">
                <Button size="sm" variant="outline" className="mb-2" onClick={() => void loadVersions()}>刷新版本</Button>
                <ScrollArea className="h-[calc(100%-2.2rem)]">
                  {versions.map((version) => (
                    <div key={version.id} className="rounded border p-2 mb-2">
                      <div>{version.label}</div>
                      <div className="text-muted-foreground">{formatDateTime(version.createdAt)}</div>
                    </div>
                  ))}
                </ScrollArea>
              </TabsContent>
              <TabsContent value="export" className="flex-1 m-0 mt-2 min-h-0 rounded border p-2 text-xs">
                <Button size="sm" onClick={() => void createExportJob()} className="mb-2">创建导出任务</Button>
                <ScrollArea className="h-[calc(100%-2.2rem)]">
                  {exportJobs.map((job) => (
                    <div key={job.id} className="rounded border p-2 mb-2">{job.fileName || job.id}</div>
                  ))}
                </ScrollArea>
              </TabsContent>
            </Tabs>
          </TabsContent>
        </Tabs>
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
              <div className={cn("h-full flex flex-col gap-2 border-r pr-2", !showLeftPanel && "invisible")}>
                <div className="px-2 pt-2 space-y-2">
                  <Select value={selectedStoryId} onValueChange={setSelectedStoryId}>
                    <SelectTrigger><SelectValue placeholder="选择故事" /></SelectTrigger>
                    <SelectContent>{stories.map((s) => <SelectItem key={s.id} value={s.id}>{s.title}</SelectItem>)}</SelectContent>
                  </Select>
                  <Select value={selectedOutlineId} onValueChange={setSelectedOutlineId} disabled={!selectedStoryId}>
                    <SelectTrigger><SelectValue placeholder="选择大纲" /></SelectTrigger>
                    <SelectContent>{outlines.map((o) => <SelectItem key={o.id} value={o.id}>{o.title}</SelectItem>)}</SelectContent>
                  </Select>
                  <Select value={selectedManuscriptId} onValueChange={setSelectedManuscriptId} disabled={!selectedOutlineId}>
                    <SelectTrigger><SelectValue placeholder="选择稿件" /></SelectTrigger>
                    <SelectContent>{manuscripts.map((m) => <SelectItem key={m.id} value={m.id}>{m.title}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                {selectedSceneIds.length > 1 && (
                  <div className="px-2 py-2 border-y bg-muted/40 text-xs space-y-2">
                    <div className="text-muted-foreground">已多选 {selectedSceneIds.length} 个场景</div>
                    <div className="flex flex-wrap items-center gap-2">
                      <Button size="sm" variant="outline" className="h-7" onClick={() => { setIsSidebarOpen(true); setSidebarTab("export"); }}>
                        批量导出
                      </Button>
                      <Select value={batchMoveChapterId} onValueChange={setBatchMoveChapterId}>
                        <SelectTrigger className="h-7 w-[130px]">
                          <SelectValue placeholder="目标章节" />
                        </SelectTrigger>
                        <SelectContent>
                          {chapters.map((chapter, index) => (
                            <SelectItem key={chapter.id} value={chapter.id}>{`第${index + 1}章`}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <Button size="sm" variant="outline" className="h-7" onClick={() => void batchMoveScenes()}>
                        批量移动
                      </Button>
                      <Button size="sm" variant="destructive" className="h-7" onClick={() => void batchDeleteScenes()}>
                        批量删除
                      </Button>
                    </div>
                  </div>
                )}
                <ScrollArea className="flex-1 mt-1">
                  {(outlineDraft?.chapters || []).map((chapter, ci) => (
                    <div
                      key={chapter.id}
                      className={cn(
                        "px-2 pb-1 rounded-md",
                        dragOverChapterId === chapter.id && draggingChapterId !== chapter.id ? "border border-dashed border-primary" : "",
                      )}
                      onDragOver={(event) => {
                        if (!draggingChapterId || draggingChapterId === chapter.id) return;
                        event.preventDefault();
                        setDragOverChapterId(chapter.id);
                      }}
                      onDrop={(event) => {
                        if (!draggingChapterId) return;
                        event.preventDefault();
                        void moveChapter(draggingChapterId, chapter.id);
                        setDraggingChapterId("");
                        setDragOverChapterId("");
                      }}
                    >
                      <button
                        type="button"
                        draggable
                        onDragStart={() => setDraggingChapterId(chapter.id)}
                        onDragEnd={() => {
                          setDraggingChapterId("");
                          setDragOverChapterId("");
                        }}
                        className="w-full text-left text-xs font-semibold text-muted-foreground flex items-center gap-1"
                        onClick={() => setExpandedChapterIds((prev) => ({ ...prev, [chapter.id]: !prev[chapter.id] }))}
                      >
                        <GripVertical className="h-3.5 w-3.5 text-muted-foreground/70" />
                        {expandedChapterIds[chapter.id] === false ? <ChevronRight className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                        {`第${ci + 1}章 ${chapter.title}`}
                      </button>
                      {expandedChapterIds[chapter.id] !== false && (
                        <div className="space-y-1 mt-1">
                          {chapter.scenes.map((scene, si) => {
                            const selected = selectedSceneIds.includes(scene.id);
                            const active = selectedSceneId === scene.id;
                            const row = sceneMap[scene.id];
                            return (
                              <ContextMenu key={scene.id}>
                                <ContextMenuTrigger asChild>
                                  <button
                                    type="button"
                                    draggable
                                    onDragStart={() => setDraggingSceneId(scene.id)}
                                    onDragOver={(event) => {
                                      event.preventDefault();
                                      setDragOverSceneId(scene.id);
                                    }}
                                    onDrop={(event) => {
                                      event.preventDefault();
                                      void moveScene(draggingSceneId, scene.id);
                                      setDraggingSceneId("");
                                      setDragOverSceneId("");
                                    }}
                                    onDragEnd={() => {
                                      setDraggingSceneId("");
                                      setDragOverSceneId("");
                                    }}
                                    className={cn(
                                      "w-full rounded-md border px-2 py-1 text-left text-sm flex items-center gap-2",
                                      active ? "bg-secondary border-primary/40" : "hover:bg-muted",
                                      dragOverSceneId === scene.id && draggingSceneId !== scene.id ? "border-primary border-dashed" : "",
                                    )}
                                    onClick={(event) => handleSceneSelect(scene.id, event)}
                                  >
                                    <GripVertical className="h-3.5 w-3.5 text-muted-foreground/70" />
                                    <Checkbox checked={selected} onCheckedChange={() => handleSceneSelect(scene.id)} />
                                    <span className={cn("h-2 w-2 rounded-full", sceneStatusClass[sceneStatuses[scene.id] || "todo"])} />
                                    <span className="text-xs text-muted-foreground">{`Sc.${si + 1}`}</span>
                                    <span className="truncate">{scene.title}</span>
                                    {dirtyScenes[scene.id] && <span className="h-2 w-2 rounded-full bg-amber-500 ml-auto" />}
                                  </button>
                                </ContextMenuTrigger>
                                <ContextMenuContent className="w-40">
                                  <ContextMenuItem onClick={() => handleSceneSelect(scene.id)}>打开场景</ContextMenuItem>
                                  <ContextMenuItem onClick={() => setSceneStatus(scene.id, "todo")}>标记为待写</ContextMenuItem>
                                  <ContextMenuItem onClick={() => setSceneStatus(scene.id, "in_progress")}>标记为进行中</ContextMenuItem>
                                  <ContextMenuItem onClick={() => setSceneStatus(scene.id, "done")}>标记为已完成</ContextMenuItem>
                                  <ContextMenuItem
                                    onClick={() => {
                                      if (!row) return;
                                      const nextOutline = JSON.parse(JSON.stringify(outlineDraft)) as Outline;
                                      const targetChapter = nextOutline.chapters.find((item) => item.id === row.chapterId);
                                      if (!targetChapter) return;
                                      targetChapter.scenes = targetChapter.scenes.filter((item) => item.id !== scene.id);
                                      setOutlineDraft(nextOutline);
                                      setOpenSceneIds((prev) => prev.filter((id) => id !== scene.id));
                                      if (selectedSceneId === scene.id) setSelectedSceneId("");
                                      void persistOutlineDraft(nextOutline);
                                    }}
                                  >
                                    删除场景
                                  </ContextMenuItem>
                                </ContextMenuContent>
                              </ContextMenu>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  ))}
                </ScrollArea>
              </div>
            </ResizablePanel>
            <ResizableHandle withHandle className={cn(!showLeftPanel && "pointer-events-none opacity-0")} />

        <ResizablePanel minSize={35}>
          <div className="h-full flex flex-col min-w-0 transition-all duration-300">
            {!focusMode && (
              <div className="flex items-center justify-between mb-2 px-2 pt-2">
                <div className="text-sm text-muted-foreground">
                  {isSaving ? "正在保存..." : lastSavedAt ? `上次保存: ${lastSavedAt}` : "未保存"}
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => { setIsSidebarOpen(true); setSidebarTab("version"); }}><History className="mr-2 h-4 w-4" /> 历史版本</Button>
                  <Button size="sm" onClick={() => void handleManualSave()} disabled={!selectedManuscriptId || !selectedSceneId}>
                    <Save className="mr-2 h-4 w-4" /> 保存
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={async () => {
                      if (!selectedManuscriptId || !selectedSceneId) return;
                      setIsGenerating(true);
                      try {
                        const saved = await api.manuscripts.generateScene(selectedManuscriptId, selectedSceneId);
                        setManuscripts((prev) => prev.map((m) => (m.id === saved.id ? saved : m)));
                        setContent(saved.sections?.[selectedSceneId] || "");
                        toast({ title: "已生成场景正文" });
                      } catch (e: any) {
                        toast({ variant: "destructive", title: "生成失败", description: e.message });
                      } finally {
                        setIsGenerating(false);
                      }
                    }}
                    disabled={isGenerating || !selectedSceneId || !selectedManuscriptId}
                  >
                    {isGenerating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
                    生成本场景
                  </Button>
                  <Button variant="ghost" size="icon" onClick={() => setIsSidebarOpen(!isSidebarOpen)} className="ml-2" title={isSidebarOpen ? "收起右栏" : "展开右栏"}>
                    {isSidebarOpen ? <PanelRightClose className="h-4 w-4" /> : <PanelRightOpen className="h-4 w-4" />}
                  </Button>
                </div>
              </div>
            )}

            <div className="px-2 pb-2">
              <ScrollArea className="w-full whitespace-nowrap">
                <div className="flex gap-2">
                  {openSceneIds.map((sceneId) => {
                    const scene = sceneMap[sceneId]?.scene;
                    if (!scene) return null;
                    return (
                      <div
                        key={sceneId}
                        draggable
                        onDragStart={() => setDraggingTabId(sceneId)}
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={(event) => {
                          event.preventDefault();
                          reorderOpenTabs(draggingTabId, sceneId);
                          setDraggingTabId("");
                        }}
                        className={cn(
                          "rounded-md border flex items-center",
                          selectedSceneId === sceneId ? "bg-secondary border-primary/40" : "bg-muted/40",
                        )}
                      >
                        <button type="button" className="px-2 py-1 text-sm" onClick={() => setSelectedSceneId(sceneId)}>
                          {scene.title}
                          {dirtyScenes[sceneId] ? " *" : ""}
                        </button>
                        {openSceneIds.length > 1 && <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => closeSceneTab(sceneId)}><X className="h-3 w-3" /></Button>}
                      </div>
                    );
                  })}
                </div>
              </ScrollArea>
            </div>

            <div className="flex-1 border rounded-lg overflow-hidden bg-background shadow-sm mx-2">
              <TiptapEditor
                key={`desktop-editor-${focusMode ? "zen" : "normal"}`}
                content={content}
                onChange={(html) => {
                  setContent(html);
                  if (!selectedSceneId) return;
                  const previousWordCount = sceneWordCacheRef.current[selectedSceneId] ?? 0;
                  const nextWordCount = countWords(stripHtml(html));
                  const delta = nextWordCount - previousWordCount;
                  sceneWordCacheRef.current[selectedSceneId] = nextWordCount;
                  if (delta > 0) setSessionWordsWritten((value) => value + delta);
                  if (delta < 0) setSessionWordsDeleted((value) => value + Math.abs(delta));
                  setSceneDrafts((prev) => ({ ...prev, [selectedSceneId]: html }));
                  setDirtyScenes((prev) => ({ ...prev, [selectedSceneId]: true }));
                  scheduleSave(selectedSceneId, html);
                }}
                className="h-full"
                editable={!!selectedSceneId}
                zenMode={focusMode}
              />
            </div>

            {!focusMode && (
              <div className="h-8 mt-2 border-t px-3 flex items-center justify-between text-xs text-muted-foreground bg-muted/30">
                <div className="flex items-center gap-3">
                  <span>字数 {currentWordCount}</span>
                  <span>选中 {selectedWordCount}</span>
                  <span>会话 {Math.floor(sessionDurationSeconds / 60)}m{sessionDurationSeconds % 60}s</span>
                </div>
                <div className="flex items-center gap-3">
                  <span>净增 {sessionNetWords}</span>
                  {!!activeGoal && <span>目标 {activeGoal.currentValue || 0}/{activeGoal.targetValue || 0}</span>}
                </div>
              </div>
            )}
          </div>
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
              <div className={cn("h-full", !showRightPanel && "invisible")}>
              <Tabs value={sidebarTab} onValueChange={(value) => setSidebarTab(value as SidebarTab)} className="h-full flex flex-col">
                <TabsList className="grid grid-cols-6 mx-2 mt-2">
                  <TabsTrigger value="copilot">copilot</TabsTrigger>
                  <TabsTrigger value="context">context</TabsTrigger>
                  <TabsTrigger value="version">version</TabsTrigger>
                  <TabsTrigger value="export">export</TabsTrigger>
                  <TabsTrigger value="stats">stats</TabsTrigger>
                  <TabsTrigger value="goals">goals</TabsTrigger>
                </TabsList>

                <TabsContent value="copilot" className="flex-1 m-0 mt-2 min-h-0"><CopilotSidebar context={contextData} className="h-full border-none" /></TabsContent>
                <TabsContent value="context" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
                  <Button size="sm" variant="outline" className="mb-2" onClick={() => void loadContextPreview()}>刷新上下文</Button>
                  <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 text-xs space-y-2">
                    <div>{`Token: ${contextPreview?.tokenUsed || 0}/${contextPreview?.tokenBudget || 0}`}</div>
                    <div>{`生成时间: ${formatDateTime(contextPreview?.generatedAt)}`}</div>
                    <div className="rounded border p-2">
                      <div className="font-medium mb-1">System Prompt</div>
                      {(contextPreview?.systemPromptEntries || []).map((entry: any) => (
                        <div key={entry.id} className="mb-1 last:mb-0">{entry.displayName}</div>
                      ))}
                      {!(contextPreview?.systemPromptEntries || []).length && <div className="text-muted-foreground">暂无</div>}
                    </div>
                    <div className="rounded border p-2">
                      <div className="font-medium mb-1">场景前 / 场景后</div>
                      <div>{`前: ${(contextPreview?.beforeSceneEntries || []).length} 条`}</div>
                      <div>{`后: ${(contextPreview?.afterSceneEntries || []).length} 条`}</div>
                    </div>
                    <div className="rounded border p-2">
                      <div className="font-medium mb-1">图谱关系</div>
                      {(contextPreview?.graphRelations || []).map((relation: string, index: number) => (
                        <div key={`${relation}-${index}`} className="mb-1 last:mb-0">{relation}</div>
                      ))}
                      {!(contextPreview?.graphRelations || []).length && <div className="text-muted-foreground">暂无</div>}
                    </div>
                    <div className="rounded border p-2">
                      <div className="font-medium mb-1">活跃角色</div>
                      <div>{(contextPreview?.activeCharacters || []).join("、") || "暂无"}</div>
                    </div>
                    <div className="rounded border p-2">
                      <div className="font-medium mb-1">前情摘要</div>
                      <div className="text-muted-foreground">{contextPreview?.recentSummary || "暂无"}</div>
                    </div>
                  </ScrollArea>
                </TabsContent>

                <TabsContent value="version" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
                  <div className="space-y-2 mb-2">
                    <div className="flex gap-2">
                      <Button size="sm" variant="outline" onClick={() => void loadVersions()}>刷新</Button>
                      <Button size="sm" onClick={async () => {
                        if (!selectedManuscriptId) return;
                        const label = window.prompt("检查点标签", `manual-${Date.now()}`)?.trim();
                        if (!label) return;
                        await api.v2.version.createVersion(selectedManuscriptId, { snapshotType: "manual", label });
                        await loadVersions();
                      }}><Flag className="h-3.5 w-3.5 mr-1" />检查点</Button>
                      <Button size="sm" variant="secondary" onClick={() => void runVersionDiff()} disabled={selectedDiffVersions.length !== 2}>对比</Button>
                    </div>
                    <div className="rounded border p-2 space-y-2 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="font-medium">分支管理</span>
                        <Badge variant="outline">当前 {currentBranchId ? currentBranchId.slice(0, 8) : "-"}</Badge>
                      </div>
                      <div className="flex gap-2">
                        <Input value={newBranchName} onChange={(event) => setNewBranchName(event.target.value)} placeholder="分支名称" className="h-8" />
                        <Button size="sm" onClick={() => void createBranch()}><GitBranch className="h-3.5 w-3.5 mr-1" />新建分支</Button>
                      </div>
                      <div className="space-y-1">
                        {branches.map((branch) => (
                          <div key={String(branch.id)} className="flex items-center justify-between rounded border p-1">
                            <div className="truncate mr-2">
                              <span className="font-medium">{branch.name}</span>
                              <span className="text-muted-foreground ml-1">{branch.status}</span>
                            </div>
                            <Button size="sm" variant="outline" className="h-6 px-2" onClick={() => void checkoutBranch(String(branch.id))} disabled={String(branch.status) !== "active"}>
                              切换
                            </Button>
                          </div>
                        ))}
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        <Select value={mergeBranchId} onValueChange={setMergeBranchId}>
                          <SelectTrigger className="h-8"><SelectValue placeholder="选择分支" /></SelectTrigger>
                          <SelectContent>
                            {branches.filter((branch) => !branch.isMain && String(branch.status) === "active").map((branch) => (
                              <SelectItem key={String(branch.id)} value={String(branch.id)}>{branch.name}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <Select value={mergeStrategy} onValueChange={(value) => setMergeStrategy(value as "REPLACE_ALL" | "SCENE_SELECT")}>
                          <SelectTrigger className="h-8"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            <SelectItem value="REPLACE_ALL">整体替换</SelectItem>
                            <SelectItem value="SCENE_SELECT">逐场景选择</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <Button size="sm" variant="secondary" onClick={() => void mergeSelectedBranch()} disabled={!mergeBranchId}>合并到主线</Button>
                      {!!mergeConflicts.length && (
                        <div className="space-y-2 rounded border border-amber-300 bg-amber-50 p-2">
                          <div className="text-amber-700">检测到冲突，请逐场景选择保留版本。</div>
                          {mergeConflicts.map((conflict) => (
                            <div key={conflict.sceneId} className="rounded border p-2">
                              <div className="font-medium">{conflict.sceneId}</div>
                              <Select
                                value={sceneResolutions[conflict.sceneId] || ""}
                                onValueChange={(value) => setSceneResolutions((prev) => ({ ...prev, [conflict.sceneId]: value as "target" | "source" }))}
                              >
                                <SelectTrigger className="h-7 mt-1"><SelectValue placeholder="选择保留主线或分支" /></SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="target">保留主线</SelectItem>
                                  <SelectItem value="source">保留分支</SelectItem>
                                </SelectContent>
                              </Select>
                            </div>
                          ))}
                          <Button size="sm" onClick={() => void mergeSelectedBranch(sceneResolutions)}>提交冲突解决</Button>
                        </div>
                      )}
                    </div>
                  </div>
                  <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 space-y-2">
                    {visibleVersions.map((version, index) => (
                      <div key={String(version.id)} className="flex gap-2 text-xs">
                        <div className="flex flex-col items-center pt-0.5">
                          <Clock3 className="h-3.5 w-3.5 text-muted-foreground" />
                          {index < visibleVersions.length - 1 && <div className="mt-1 w-px flex-1 min-h-4 bg-border" />}
                        </div>
                        <div className="flex-1 rounded border p-2 space-y-1">
                          <div className="flex items-center gap-2">
                            <Checkbox checked={selectedDiffVersions.includes(String(version.id))} onCheckedChange={() => toggleVersionSelection(String(version.id))} />
                            <span className="font-medium">{`v${Number(version.versionNumber || index + 1)} ${version.label || "未命名版本"}`}</span>
                            <Badge variant="outline">{snapshotTypeLabel(version.snapshotType)}</Badge>
                            <Button size="sm" variant="ghost" className="ml-auto h-6 px-2" onClick={() => void rollbackVersion(String(version.id))}>
                              <RotateCcw className="h-3 w-3 mr-1" />回滚
                            </Button>
                          </div>
                          <div className="text-muted-foreground">{`${formatDateTime(version.createdAt)} · ${versionWordCount(version)} 字`}</div>
                        </div>
                      </div>
                    ))}
                    {hasMoreVersions && (
                      <Button size="sm" variant="outline" className="w-full h-7" onClick={() => setVersionVisibleCount((prev) => prev + VERSION_PAGE_SIZE)}>
                        加载更多版本
                      </Button>
                    )}
                    {!!diffResult && (
                      <div className="rounded border p-2 space-y-2 text-xs">
                        <div className="flex gap-1">
                          <Button size="sm" variant={diffViewMode === "split" ? "default" : "outline"} className="h-6 px-2" onClick={() => setDiffViewMode("split")}><Split className="h-3 w-3 mr-1" />并排</Button>
                          <Button size="sm" variant={diffViewMode === "unified" ? "default" : "outline"} className="h-6 px-2" onClick={() => setDiffViewMode("unified")}><ArrowDownUp className="h-3 w-3 mr-1" />统一</Button>
                          <Button size="sm" variant="secondary" className="h-6 px-2" onClick={() => void summarizeDiff()}>AI 总结</Button>
                        </div>
                        {!!aiDiffSummary && <div className="rounded bg-muted p-2">{aiDiffSummary}</div>}
                        {(diffResult.changes || []).slice(0, 6).map((change: any) => (
                          <div key={change.sceneId} className="rounded border p-2">
                            <div className="font-medium mb-1">场景 {change.sceneId}</div>
                            {diffViewMode === "split" ? (
                              <div className="grid grid-cols-2 gap-2">
                                <div className="bg-rose-50/40 rounded p-1 whitespace-pre-wrap">{change.beforeContent || "<empty>"}</div>
                                <div className="bg-emerald-50/40 rounded p-1 whitespace-pre-wrap">{change.afterContent || "<empty>"}</div>
                              </div>
                            ) : (
                              <div className="space-y-1">
                                <div className="text-rose-600 whitespace-pre-wrap">- {change.beforeContent || "<empty>"}</div>
                                <div className="text-emerald-600 whitespace-pre-wrap">+ {change.afterContent || "<empty>"}</div>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                    {!!autoSaveConfig && (
                      <div className="rounded border p-2 text-xs space-y-2">
                        <div className="font-medium">自动快照</div>
                        <Input type="number" value={Number(autoSaveConfig.autoSaveIntervalSeconds || 300)} onChange={(e) => setAutoSaveConfig((prev: any) => ({ ...prev, autoSaveIntervalSeconds: Number(e.target.value || 300) }))} />
                        <Input type="number" value={Number(autoSaveConfig.maxAutoVersions || 100)} onChange={(e) => setAutoSaveConfig((prev: any) => ({ ...prev, maxAutoVersions: Number(e.target.value || 100) }))} />
                        <Button size="sm" onClick={async () => {
                          await api.v2.version.updateAutoSave({ autoSaveIntervalSeconds: Number(autoSaveConfig.autoSaveIntervalSeconds || 300), maxAutoVersions: Number(autoSaveConfig.maxAutoVersions || 100) });
                          toast({ title: "自动快照配置已更新" });
                        }}>保存配置</Button>
                      </div>
                    )}
                  </ScrollArea>
                </TabsContent>

                <TabsContent value="export" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
                  <div className="grid grid-cols-2 gap-2 mb-2">
                    <Select value={exportFormat} onValueChange={setExportFormat}>
                      <SelectTrigger className="h-8"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="txt">TXT</SelectItem>
                        <SelectItem value="docx">DOCX</SelectItem>
                        <SelectItem value="epub">EPUB</SelectItem>
                        <SelectItem value="pdf">PDF</SelectItem>
                      </SelectContent>
                    </Select>
                    <Select value={exportTemplateId} onValueChange={setExportTemplateId}>
                      <SelectTrigger className="h-8"><SelectValue placeholder="模板" /></SelectTrigger>
                      <SelectContent>{exportTemplates.map((tpl) => <SelectItem key={tpl.id} value={String(tpl.id)}>{tpl.name || tpl.id}</SelectItem>)}</SelectContent>
                    </Select>
                  </div>
                  <div className="grid grid-cols-2 gap-2 mb-2">
                    <Input
                      className="h-8"
                      value={chapterRange}
                      onChange={(event) => setChapterRange(event.target.value)}
                      placeholder="章节范围：如 3-7"
                    />
                    <Input
                      className="h-8"
                      value={exportAuthorName}
                      onChange={(event) => setExportAuthorName(event.target.value)}
                      placeholder="作者名（标题页）"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2 mb-2 rounded border p-2 text-xs">
                    <label className="flex items-center gap-2">
                      <Checkbox checked={includeTitlePage} onCheckedChange={(checked) => setIncludeTitlePage(checked === true)} />
                      标题页
                    </label>
                    <label className="flex items-center gap-2">
                      <Checkbox checked={includeTableOfContents} onCheckedChange={(checked) => setIncludeTableOfContents(checked === true)} />
                      目录
                    </label>
                    <div className="col-span-2 grid grid-cols-[56px_1fr] items-center gap-2">
                      <span>编码</span>
                      <Select value={txtEncoding} onValueChange={setTxtEncoding}>
                        <SelectTrigger className="h-8"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="UTF-8">UTF-8</SelectItem>
                          <SelectItem value="GBK">GBK</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                  <div className="rounded border p-2 space-y-2 mb-2 text-xs">
                    <div className="font-medium">模板管理</div>
                    <div className="grid grid-cols-2 gap-2">
                      <Input className="h-8" value={templateName} onChange={(event) => setTemplateName(event.target.value)} placeholder="模板名称" />
                      <Input className="h-8" value={templateDescription} onChange={(event) => setTemplateDescription(event.target.value)} placeholder="模板说明" />
                    </div>
                    <div className="flex gap-2">
                      <Button size="sm" onClick={() => void createTemplate()}>新建模板</Button>
                      <Button size="sm" variant="secondary" onClick={() => void createExportJob()}>创建导出任务</Button>
                    </div>
                    <div className="space-y-1">
                      {exportTemplates.map((template) => (
                        <div key={template.id} className="flex items-center justify-between rounded border p-1">
                          <div className="truncate mr-2">{template.name}</div>
                          <div className="flex gap-1">
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-6 px-2"
                              onClick={() => void updateTemplate(template)}
                              disabled={!template.userId}
                            >
                              更新
                            </Button>
                            <Button
                              size="sm"
                              variant="destructive"
                              className="h-6 px-2"
                              onClick={() => void deleteTemplate(String(template.id))}
                              disabled={!template.userId}
                            >
                              删除
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                  <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 space-y-2 text-xs">
                    {exportJobs.map((job) => (
                      <div key={job.id} className="rounded border p-2">
                        <div className="flex items-center justify-between">
                          <span>{job.fileName || `${job.id}.${job.format || exportFormat}`}</span>
                          <Badge variant="outline">{job.status || "pending"}</Badge>
                        </div>
                        <Progress className="mt-1" value={Number(job.progress || 0)} />
                        {String(job.status).toLowerCase() === "completed" && (
                          <a className="inline-flex items-center gap-1 text-primary mt-1" target="_blank" rel="noreferrer" href={api.v2.export.downloadUrl(selectedManuscriptId, String(job.id))}>
                            <Download className="h-3 w-3" />下载
                          </a>
                        )}
                      </div>
                    ))}
                  </ScrollArea>
                </TabsContent>

                <TabsContent value="stats" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
                  <Button size="sm" variant="outline" className="mb-2" onClick={() => void loadStats()}>刷新统计</Button>
                  <div className="grid grid-cols-2 gap-2 text-xs mb-2">
                    <div className="rounded border p-2">会话数 {workspaceStats?.totalSessions ?? 0}</div>
                    <div className="rounded border p-2">净字数 {workspaceStats?.totalNetWords ?? 0}</div>
                  </div>
                  <div className="grid grid-cols-1 gap-2">
                    <div className="h-[180px] rounded border p-2">
                      <div className="text-xs text-muted-foreground mb-1">日维度</div>
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={workspaceStats?.dailySeries || []}>
                          <CartesianGrid strokeDasharray="3 3" />
                          <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                          <YAxis tick={{ fontSize: 10 }} />
                          <Tooltip />
                          <Line type="monotone" dataKey="netWords" stroke="#2f855a" strokeWidth={2} dot={false} />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                    <div className="h-[160px] rounded border p-2">
                      <div className="text-xs text-muted-foreground mb-1">周维度</div>
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={workspaceStats?.weeklySeries || []}>
                          <CartesianGrid strokeDasharray="3 3" />
                          <XAxis dataKey="weekStart" tick={{ fontSize: 10 }} />
                          <YAxis tick={{ fontSize: 10 }} />
                          <Tooltip />
                          <Line type="monotone" dataKey="netWords" stroke="#8b6f4e" strokeWidth={2} dot={false} />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                    <div className="h-[160px] rounded border p-2">
                      <div className="text-xs text-muted-foreground mb-1">月维度</div>
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={workspaceStats?.monthlySeries || []}>
                          <CartesianGrid strokeDasharray="3 3" />
                          <XAxis dataKey="month" tick={{ fontSize: 10 }} />
                          <YAxis tick={{ fontSize: 10 }} />
                          <Tooltip />
                          <Line type="monotone" dataKey="netWords" stroke="#3b82f6" strokeWidth={2} dot={false} />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                    <div className="rounded border p-2">
                      <div className="text-xs text-muted-foreground mb-2">近30天热力图</div>
                      <div className="grid grid-cols-10 gap-1">
                        {dailyHeatmap.map((item: any) => {
                          const words = Number(item.netWords || 0);
                          const level = words <= 0 ? 0 : words < 500 ? 1 : words < 1200 ? 2 : words < 2500 ? 3 : 4;
                          const cls = ["bg-muted", "bg-emerald-100", "bg-emerald-200", "bg-emerald-400", "bg-emerald-600"][level];
                          return (
                            <div
                              key={item.date}
                              title={`${item.date}: ${words} 字`}
                              className={cn("h-4 rounded-sm border", cls)}
                            />
                          );
                        })}
                        {!dailyHeatmap.length && <div className="text-xs text-muted-foreground">暂无热力图数据</div>}
                      </div>
                    </div>
                  </div>
                </TabsContent>

                <TabsContent value="goals" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
                  <div className="grid grid-cols-2 gap-2 mb-2">
                    <Select value={goalType} onValueChange={setGoalType}>
                      <SelectTrigger className="h-8"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="daily_words">日更字数</SelectItem>
                        <SelectItem value="session_words">单次会话</SelectItem>
                        <SelectItem value="total_words">全书总字数</SelectItem>
                      </SelectContent>
                    </Select>
                    <Input type="number" value={goalTargetValue} onChange={(e) => setGoalTargetValue(Number(e.target.value || 0))} />
                  </div>
                  <Button size="sm" onClick={() => void createGoal()} className="mb-2">创建目标</Button>
                  <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 space-y-2 text-xs">
                    {goals.map((goal) => (
                      <div key={goal.id} className="rounded border p-2">
                        <div className="flex items-center justify-between">
                          <span>{goal.goalType}</span>
                          <Badge variant={goal.status === "completed" ? "default" : "outline"}>{goal.status || "active"}</Badge>
                        </div>
                        <div className="text-muted-foreground mt-1">{`${goal.currentValue || 0}/${goal.targetValue || 0}`}</div>
                        <div className="grid grid-cols-2 gap-2 mt-2">
                          <Button size="sm" variant="outline" className="h-7" onClick={() => void updateGoal(String(goal.id), { status: goal.status === "completed" ? "active" : "completed" })}>
                            切换状态
                          </Button>
                          <Button size="sm" variant="destructive" className="h-7" onClick={() => void deleteGoal(String(goal.id))}>
                            删除
                          </Button>
                        </div>
                      </div>
                    ))}
                  </ScrollArea>
                </TabsContent>
              </Tabs>
              </div>
            </ResizablePanel>
        </ResizablePanelGroup>
      )}

      {!focusMode && !isMobile && (
        <div className="absolute top-2 right-2 flex items-center gap-1">
          <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => setLeftPanelOpen((prev) => !prev)} title={leftPanelOpen ? "收起左栏" : "展开左栏"}>
            {leftPanelOpen ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeftOpen className="h-4 w-4" />}
          </Button>
          <Button
            size="icon"
            variant="ghost"
            className="h-7 w-7"
            onClick={toggleFocusMode}
            title={focusMode ? "退出专注" : "专注模式"}
          >
            <Focus className="h-4 w-4" />
          </Button>
        </div>
      )}

      <CommandDialog open={isCommandOpen} onOpenChange={setIsCommandOpen}>
        <CommandInput placeholder="输入命令或场景..." value={commandQuery} onValueChange={setCommandQuery} />
        <CommandList>
          <CommandEmpty>没有匹配命令</CommandEmpty>
          <CommandGroup heading="快捷操作">
            <CommandItem onSelect={() => { setIsCommandOpen(false); void handleManualSave(); }}>保存当前场景<CommandShortcut>{shortcuts.save}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { setIsCommandOpen(false); setIsSidebarOpen((prev) => !prev); }}>切换右侧面板<CommandShortcut>{shortcuts.toggle_right_panel}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { setIsCommandOpen(false); toggleFocusMode(); }}>切换专注模式<CommandShortcut>{shortcuts.focus_mode}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { setIsCommandOpen(false); jumpScene(1); }}>下一场景<CommandShortcut>{shortcuts.next_chapter}</CommandShortcut></CommandItem>
            <CommandItem onSelect={() => { setIsCommandOpen(false); jumpScene(-1); }}>上一场景<CommandShortcut>{shortcuts.prev_chapter}</CommandShortcut></CommandItem>
          </CommandGroup>
          <CommandSeparator />
          <CommandGroup heading="场景跳转">
            {sceneRows.map((row) => (
              <CommandItem key={row.id} onSelect={() => { setSelectedSceneId(row.id); setIsCommandOpen(false); }}>
                {row.displayName}
              </CommandItem>
            ))}
          </CommandGroup>
          <CommandSeparator />
          <CommandGroup heading="角色检索">
            {characters.map((character) => (
              <CommandItem
                key={character.id}
                onSelect={() => {
                  setIsCommandOpen(false);
                  setIsSidebarOpen(true);
                  setSidebarTab("context");
                  setCommandQuery(character.name || "");
                }}
              >
                {character.name || "未命名角色"}
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </CommandDialog>
    </div>
  );
};

export default ManuscriptWriter;
