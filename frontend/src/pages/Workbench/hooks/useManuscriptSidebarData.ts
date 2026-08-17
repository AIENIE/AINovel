import type { NetworkObject } from "@/lib/api-client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import type { WorkbenchSidebarTab } from "./useWorkbenchLayoutPersistence";
import { t } from "@/i18n";
import { localizedErrorMessage } from "@/lib/error-messages";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: "default" | "destructive";
}) => void;

type UseManuscriptSidebarDataOptions = {
  applyFetchedManuscript: (manuscript: Manuscript) => void;
  isSidebarOpen: boolean;
  selectedManuscriptId: string;
  selectedSceneId: string;
  selectedSceneIds: string[];
  selectedStoryId: string;
  sidebarTab: WorkbenchSidebarTab;
  toast: ToastFn;
};

const VERSION_PAGE_SIZE = 10;
const WORKBENCH_QUERY_STALE_TIME = 60_000;

const goalsQueryKey = ["workbench", "sidebar", "goals"] as const;
const contextPreviewQueryKey = (storyId: string, manuscriptId: string, sceneId: string) =>
  ["workbench", "sidebar", "context", storyId, manuscriptId, sceneId] as const;
const versionDataQueryKey = (manuscriptId: string) => ["workbench", "sidebar", "versions", manuscriptId] as const;
const exportDataQueryKey = (manuscriptId: string) => ["workbench", "sidebar", "export", manuscriptId] as const;
const statsQueryKey = ["workbench", "sidebar", "stats"] as const;

async function fetchGoals() {
  try {
    return await api.v2.workspace.listGoals();
  } catch {
    return [];
  }
}

async function fetchContextPreview(storyId: string, manuscriptId: string, sceneId: string) {
  return await api.v2.context.previewContext(storyId, { manuscriptId, sceneId });
}

async function fetchVersionData(manuscriptId: string) {
  const versions = await api.v2.version.listVersions(manuscriptId);
  const branches = await api.v2.version.listBranches(manuscriptId);
  const autoSaveConfig = await api.v2.version.getAutoSave();
  return { autoSaveConfig, branches, versions };
}

async function fetchExportData(manuscriptId: string) {
  const [jobs, templates] = await Promise.all([
    api.v2.export.listJobs(manuscriptId),
    api.v2.export.listTemplates(),
  ]);
  return { jobs, templates };
}

async function fetchStats() {
  return await api.v2.workspace.getStats();
}

export function useManuscriptSidebarData({
  applyFetchedManuscript,
  isSidebarOpen,
  selectedManuscriptId,
  selectedSceneId,
  selectedSceneIds,
  selectedStoryId,
  sidebarTab,
  toast,
}: UseManuscriptSidebarDataOptions) {
  const queryClient = useQueryClient();
  const [currentBranchId, setCurrentBranchId] = useState("");
  const [newBranchName, setNewBranchName] = useState("");
  const [mergeBranchId, setMergeBranchId] = useState("");
  const [mergeStrategy, setMergeStrategy] = useState<"REPLACE_ALL" | "SCENE_SELECT">("REPLACE_ALL");
  const [mergeConflicts, setMergeConflicts] = useState<NetworkObject[]>([]);
  const [sceneResolutions, setSceneResolutions] = useState<Record<string, "target" | "source">>({});
  const [selectedDiffVersions, setSelectedDiffVersions] = useState<string[]>([]);
  const [diffResult, setDiffResult] = useState<NetworkObject | null>(null);
  const [diffViewMode, setDiffViewMode] = useState<"split" | "unified">("split");
  const [versionVisibleCount, setVersionVisibleCount] = useState(VERSION_PAGE_SIZE);
  const [aiDiffSummary, setAiDiffSummary] = useState("");
  const [autoSaveConfig, setAutoSaveConfig] = useState<NetworkObject | null>(null);
  const [exportFormat, setExportFormat] = useState("txt");
  const [exportTemplateId, setExportTemplateId] = useState("");
  const [templateName, setTemplateName] = useState("");
  const [templateDescription, setTemplateDescription] = useState("");
  const [goalType, setGoalType] = useState("daily_words");
  const [goalTargetValue, setGoalTargetValue] = useState(2000);
  const [chapterRange, setChapterRange] = useState("");
  const [includeTitlePage, setIncludeTitlePage] = useState(true);
  const [includeTableOfContents, setIncludeTableOfContents] = useState(true);
  const [txtEncoding, setTxtEncoding] = useState("UTF-8");
  const [exportAuthorName, setExportAuthorName] = useState("");
  const [exportDownloadingJobId, setExportDownloadingJobId] = useState("");

  const goalsQuery = useQuery({
    queryKey: goalsQueryKey,
    queryFn: fetchGoals,
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const contextPreviewQuery = useQuery({
    queryKey: contextPreviewQueryKey(selectedStoryId, selectedManuscriptId, selectedSceneId),
    queryFn: async () => {
      try {
        return await fetchContextPreview(selectedStoryId, selectedManuscriptId, selectedSceneId);
      } catch (e: unknown) {
        toast({ variant: "destructive", title: t("sidebarData.contextLoadFailed"), description: localizedErrorMessage(e) });
        throw e;
      }
    },
    enabled: isSidebarOpen
      && sidebarTab === "context"
      && Boolean(selectedStoryId && selectedManuscriptId && selectedSceneId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const versionDataQuery = useQuery({
    queryKey: versionDataQueryKey(selectedManuscriptId),
    queryFn: async () => {
      try {
        return await fetchVersionData(selectedManuscriptId);
      } catch (e: unknown) {
        toast({ variant: "destructive", title: t("sidebarData.versionLoadFailed"), description: localizedErrorMessage(e) });
        throw e;
      }
    },
    enabled: isSidebarOpen && sidebarTab === "version" && Boolean(selectedManuscriptId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const exportDataQuery = useQuery({
    queryKey: exportDataQueryKey(selectedManuscriptId),
    queryFn: async () => {
      try {
        return await fetchExportData(selectedManuscriptId);
      } catch (e: unknown) {
        toast({ variant: "destructive", title: t("sidebarData.exportLoadFailed"), description: localizedErrorMessage(e) });
        throw e;
      }
    },
    enabled: isSidebarOpen && sidebarTab === "export" && Boolean(selectedManuscriptId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const statsQuery = useQuery({
    queryKey: statsQueryKey,
    queryFn: async () => {
      try {
        return await fetchStats();
      } catch (e: unknown) {
        toast({ variant: "destructive", title: t("sidebarData.statsLoadFailed"), description: localizedErrorMessage(e) });
        throw e;
      }
    },
    enabled: isSidebarOpen && sidebarTab === "stats",
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });

  const goals = goalsQuery.data ?? [];
  const contextPreview = contextPreviewQuery.data ?? null;
  const versions = useMemo(() => versionDataQuery.data?.versions ?? [], [versionDataQuery.data?.versions]);
  const branches = versionDataQuery.data?.branches ?? [];
  const exportJobs = useMemo(() => exportDataQuery.data?.jobs ?? [], [exportDataQuery.data?.jobs]);
  const exportTemplates = exportDataQuery.data?.templates ?? [];
  const workspaceStats = statsQuery.data ?? null;

  const visibleVersions = useMemo(() => versions.slice(0, versionVisibleCount), [versionVisibleCount, versions]);
  const hasMoreVersions = versionVisibleCount < versions.length;

  const loadGoals = useCallback(async () => {
    return await queryClient.fetchQuery({
      queryKey: goalsQueryKey,
      queryFn: fetchGoals,
      staleTime: 0,
      retry: false,
    });
  }, [queryClient]);

  const loadContextPreview = useCallback(async () => {
    if (!selectedStoryId || !selectedManuscriptId || !selectedSceneId) return null;
    try {
      return await queryClient.fetchQuery({
        queryKey: contextPreviewQueryKey(selectedStoryId, selectedManuscriptId, selectedSceneId),
        queryFn: () => fetchContextPreview(selectedStoryId, selectedManuscriptId, selectedSceneId),
        staleTime: 0,
        retry: false,
      });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.contextLoadFailed"), description: localizedErrorMessage(e) });
      return null;
    }
  }, [queryClient, selectedManuscriptId, selectedSceneId, selectedStoryId, toast]);

  const loadVersions = useCallback(async () => {
    if (!selectedManuscriptId) return null;
    try {
      return await queryClient.fetchQuery({
        queryKey: versionDataQueryKey(selectedManuscriptId),
        queryFn: () => fetchVersionData(selectedManuscriptId),
        staleTime: 0,
        retry: false,
      });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.versionLoadFailed"), description: localizedErrorMessage(e) });
      return null;
    }
  }, [queryClient, selectedManuscriptId, toast]);

  const loadExport = useCallback(async () => {
    if (!selectedManuscriptId) return null;
    try {
      return await queryClient.fetchQuery({
        queryKey: exportDataQueryKey(selectedManuscriptId),
        queryFn: () => fetchExportData(selectedManuscriptId),
        staleTime: 0,
        retry: false,
      });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.exportLoadFailed"), description: localizedErrorMessage(e) });
      return null;
    }
  }, [queryClient, selectedManuscriptId, toast]);

  const loadStats = useCallback(async () => {
    try {
      return await queryClient.fetchQuery({
        queryKey: statsQueryKey,
        queryFn: fetchStats,
        staleTime: 0,
        retry: false,
      });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.statsLoadFailed"), description: localizedErrorMessage(e) });
      return null;
    }
  }, [queryClient, toast]);

  const toggleVersionSelection = useCallback((versionId: string) => {
    setSelectedDiffVersions((prev) => {
      if (prev.includes(versionId)) return prev.filter((id) => id !== versionId);
      if (prev.length >= 2) return [prev[1], versionId];
      return [...prev, versionId];
    });
  }, []);

  const runVersionDiff = useCallback(async () => {
    if (!selectedManuscriptId || selectedDiffVersions.length !== 2) return;
    try {
      const [fromVersionId, toVersionId] = selectedDiffVersions;
      setDiffResult(await api.v2.version.getDiff(selectedManuscriptId, fromVersionId, toVersionId));
      setAiDiffSummary("");
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.diffFailed"), description: localizedErrorMessage(e) });
    }
  }, [selectedDiffVersions, selectedManuscriptId, toast]);

  const summarizeDiff = useCallback(async () => {
    if (!diffResult) return;
    const localSummary = (() => {
      const changes = Array.isArray(diffResult?.changes) ? diffResult.changes : [];
      if (!changes.length) return t("sidebarData.diffNoChanges");
      let added = 0;
      let removed = 0;
      changes.forEach((item: NetworkObject) => {
        const beforeWords = Number(item?.beforeWordCount ?? 0);
        const afterWords = Number(item?.afterWordCount ?? 0);
        const delta = afterWords - beforeWords;
        if (delta >= 0) added += delta;
        else removed += Math.abs(delta);
      });
      const sceneNames = changes
        .slice(0, 3)
        .map((item: NetworkObject) => String(item?.sceneId || t("sidebarData.sceneLabel")))
        .join("、");
      const suffix = changes.length > 3 ? t("sidebarData.etcLabel") : "";
      return `共变更 ${changes.length} 个场景（${sceneNames}${suffix}），约新增 ${added} 词、减少 ${removed} 词，主要集中在段落措辞与细节调整。`;
    })();
    try {
      const models = await api.ai.getModels();
      const modelId = models[0]?.id;
      if (!modelId) throw new Error(t("sidebarData.noAiModel"));
      const prompt = [t("sidebarData.diffSummaryPrompt"), JSON.stringify((diffResult.changes || []).slice(0, 6), null, 2)].join("\n");
      const result = await api.ai.chat([{ role: "user", content: prompt }], modelId, { manuscriptId: selectedManuscriptId });
      setAiDiffSummary(result.content || localSummary);
    } catch (e: unknown) {
      setAiDiffSummary(localSummary);
      toast({ title: t("sidebarData.aiUnavailableLocalSummary"), description: localizedErrorMessage(e) });
    }
  }, [diffResult, selectedManuscriptId, toast]);

  const createExportJob = useCallback(async () => {
    if (!selectedManuscriptId) return;
    const normalizedChapterRange = chapterRange.trim();
    if (normalizedChapterRange && !/^\d+(-\d+)?$/.test(normalizedChapterRange)) {
      toast({ variant: "destructive", title: t("sidebarData.chapterRangeInvalid"), description: t("sidebarData.chapterRangeFormatHint") });
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
      toast({ title: t("sidebarData.exportJobCreated") });
      await loadExport();
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.exportFailed"), description: localizedErrorMessage(e) });
    }
  }, [
    chapterRange,
    exportAuthorName,
    exportFormat,
    exportTemplateId,
    includeTableOfContents,
    includeTitlePage,
    loadExport,
    selectedManuscriptId,
    selectedSceneIds,
    toast,
    txtEncoding,
  ]);

  const downloadExport = useCallback(async (job: NetworkObject) => {
    if (!selectedManuscriptId || !job?.id) return;
    const jobId = String(job.id);
    setExportDownloadingJobId(jobId);
    let objectUrl = "";
    try {
      const result = await api.v2.export.download(selectedManuscriptId, jobId);
      if (!result.blob.size) throw new Error(t("sidebarData.exportFileEmpty"));
      objectUrl = URL.createObjectURL(result.blob);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = result.fileName || job.fileName || `${jobId}.${job.format || exportFormat}`;
      link.style.display = "none";
      document.body.appendChild(link);
      link.click();
      link.remove();
      toast({ title: t("sidebarData.downloadStarted") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.downloadFailed"), description: localizedErrorMessage(e) });
    } finally {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setExportDownloadingJobId("");
    }
  }, [exportFormat, selectedManuscriptId, toast]);

  const createGoal = useCallback(async () => {
    try {
      await api.v2.workspace.createGoal({
        storyId: selectedStoryId || null,
        goalType,
        targetValue: Number(goalTargetValue || 0),
        status: "active",
      });
      await loadGoals();
      toast({ title: t("sidebarData.goalCreated") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.goalCreateFailed"), description: localizedErrorMessage(e) });
    }
  }, [goalTargetValue, goalType, loadGoals, selectedStoryId, toast]);

  const updateGoal = useCallback(async (goalId: string, patch: Record<string, NetworkObject>) => {
    try {
      await api.v2.workspace.updateGoal(goalId, patch);
      await loadGoals();
      toast({ title: t("sidebarData.goalUpdated") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.goalUpdateFailed"), description: localizedErrorMessage(e) });
    }
  }, [loadGoals, toast]);

  const deleteGoal = useCallback(async (goalId: string) => {
    try {
      await api.v2.workspace.deleteGoal(goalId);
      await loadGoals();
      toast({ title: t("sidebarData.goalDeleted") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.goalDeleteFailed"), description: localizedErrorMessage(e) });
    }
  }, [loadGoals, toast]);

  const createManualVersion = useCallback(async () => {
    if (!selectedManuscriptId) return;
    const label = window.prompt(t("sidebarData.checkpointLabel"), `manual-${Date.now()}`)?.trim();
    if (!label) return;
    await api.v2.version.createVersion(selectedManuscriptId, { snapshotType: "manual", label });
    await loadVersions();
  }, [loadVersions, selectedManuscriptId]);

  const saveAutoSaveConfig = useCallback(async () => {
    if (!autoSaveConfig) return;
    const nextConfig = {
      autoSaveIntervalSeconds: Number(autoSaveConfig.autoSaveIntervalSeconds || 300),
      maxAutoVersions: Number(autoSaveConfig.maxAutoVersions || 100),
    };
    await api.v2.version.updateAutoSave(nextConfig);
    if (selectedManuscriptId) {
      queryClient.setQueryData<{ autoSaveConfig: NetworkObject; branches: NetworkObject[]; versions: NetworkObject[] } | undefined>(
        versionDataQueryKey(selectedManuscriptId),
        (prev) => (prev ? { ...prev, autoSaveConfig: nextConfig } : prev),
      );
    }
    setAutoSaveConfig(nextConfig);
    toast({ title: t("sidebarData.autoSnapshotConfigUpdated") });
  }, [autoSaveConfig, queryClient, selectedManuscriptId, toast]);

  const createBranch = useCallback(async () => {
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
      toast({ title: t("sidebarData.branchCreated") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.branchCreateFailed"), description: localizedErrorMessage(e) });
    }
  }, [loadVersions, newBranchName, selectedDiffVersions, selectedManuscriptId, toast, versions]);

  const checkoutBranch = useCallback(async (branchId: string) => {
    if (!selectedManuscriptId) return;
    try {
      const result = await api.v2.version.checkoutBranch(selectedManuscriptId, branchId);
      setCurrentBranchId(String(result.currentBranchId || branchId));
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      await loadVersions();
      toast({ title: t("sidebarData.branchSwitched") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.branchSwitchFailed"), description: localizedErrorMessage(e) });
    }
  }, [applyFetchedManuscript, loadVersions, selectedManuscriptId, toast]);

  const updateBranch = useCallback(async (branchId: string, payload: Record<string, NetworkObject>) => {
    if (!selectedManuscriptId) return;
    try {
      await api.v2.version.updateBranch(selectedManuscriptId, branchId, payload);
      await loadVersions();
      toast({ title: t("sidebarData.branchUpdated") });
    } catch (e: unknown) { toast({ variant: "destructive", title: t("sidebarData.branchUpdateFailed"), description: localizedErrorMessage(e) }); }
  }, [loadVersions, selectedManuscriptId, toast]);

  const abandonBranch = useCallback(async (branchId: string) => {
    if (!selectedManuscriptId) return;
    try {
      await api.v2.version.abandonBranch(selectedManuscriptId, branchId);
      await loadVersions();
      toast({ title: t("sidebarData.branchAbandoned") });
    } catch (e: unknown) { toast({ variant: "destructive", title: t("sidebarData.branchAbandonFailed"), description: localizedErrorMessage(e) }); }
  }, [loadVersions, selectedManuscriptId, toast]);

  const rollbackVersion = useCallback(async (versionId: string) => {
    if (!selectedManuscriptId) return;
    try {
      await api.v2.version.rollback(selectedManuscriptId, versionId);
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      await loadVersions();
      toast({ title: t("sidebarData.rollbackDone") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.rollbackFailed"), description: localizedErrorMessage(e) });
    }
  }, [applyFetchedManuscript, loadVersions, selectedManuscriptId, toast]);

  const mergeSelectedBranch = useCallback(async (resolutions?: Record<string, "target" | "source">) => {
    if (!selectedManuscriptId || !mergeBranchId) return;
    try {
      const result = await api.v2.version.mergeBranch(selectedManuscriptId, mergeBranchId, {
        strategy: mergeStrategy,
        sceneResolutions: resolutions || sceneResolutions,
      });
      if (String(result.status) === "conflict") {
        setMergeConflicts(result.conflicts || []);
        toast({ variant: "destructive", title: t("sidebarData.mergeConflictHint") });
        return;
      }
      setMergeConflicts([]);
      setSceneResolutions({});
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      await loadVersions();
      toast({ title: t("sidebarData.mergeDone") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.mergeFailed"), description: localizedErrorMessage(e) });
    }
  }, [applyFetchedManuscript, loadVersions, mergeBranchId, mergeStrategy, sceneResolutions, selectedManuscriptId, toast]);

  const createTemplate = useCallback(async () => {
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
      toast({ title: t("sidebarData.templateCreated") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.templateCreateFailed"), description: localizedErrorMessage(e) });
    }
  }, [
    exportAuthorName,
    exportFormat,
    includeTableOfContents,
    includeTitlePage,
    loadExport,
    templateDescription,
    templateName,
    toast,
    txtEncoding,
  ]);

  const updateTemplate = useCallback(async (template: NetworkObject) => {
    try {
      await api.v2.export.updateTemplate(String(template.id), {
        name: template.name,
        description: template.description || "",
        format: template.format || exportFormat,
        config: template.config || {},
      });
      await loadExport();
      toast({ title: t("sidebarData.templateUpdated") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.templateUpdateFailed"), description: localizedErrorMessage(e) });
    }
  }, [exportFormat, loadExport, toast]);

  const deleteTemplate = useCallback(async (templateId: string) => {
    try {
      await api.v2.export.deleteTemplate(templateId);
      await loadExport();
      toast({ title: t("sidebarData.templateDeleted") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sidebarData.templateDeleteFailed"), description: localizedErrorMessage(e) });
    }
  }, [loadExport, toast]);

  useEffect(() => {
    if (!versionDataQuery.data) return;
    const { autoSaveConfig: nextConfig, branches: nextBranches } = versionDataQuery.data;
    setVersionVisibleCount(VERSION_PAGE_SIZE);
    setAutoSaveConfig(nextConfig);
    const activeBranch = nextBranches.find((branch) => String(branch.status) === "active" && branch.isMain);
    setCurrentBranchId(activeBranch ? String(activeBranch.id) : "");
    setMergeBranchId((prev) => {
      if (prev && nextBranches.some((branch) => String(branch.id) === prev)) return prev;
      const candidate = nextBranches.find((branch) => !branch.isMain && branch.status === "active");
      return String(candidate?.id || "");
    });
  }, [versionDataQuery.data]);

  useEffect(() => {
    const templates = exportDataQuery.data?.templates ?? [];
    setExportTemplateId((prev) => {
      if (prev && templates.some((template: NetworkObject) => String(template.id) === prev)) return prev;
      return String(templates[0]?.id || "");
    });
  }, [exportDataQuery.data]);

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

  return {
    aiDiffSummary,
    autoSaveConfig,
    branches,
    abandonBranch,
    chapterRange,
    contextPreview,
    createBranch,
    createExportJob,
    createGoal,
    createManualVersion,
    createTemplate,
    currentBranchId,
    deleteGoal,
    deleteTemplate,
    downloadExport,
    diffResult,
    diffViewMode,
    exportAuthorName,
    exportFormat,
    exportDownloadingJobId,
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
    loadExport,
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
    updateBranch,
    txtEncoding,
    updateGoal,
    updateTemplate,
    versionPageSize: VERSION_PAGE_SIZE,
    versions,
    visibleVersions,
    workspaceStats,
    checkoutBranch,
  };
}
