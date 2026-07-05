import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import type { WorkbenchSidebarTab } from "./useWorkbenchLayoutPersistence";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: string;
}) => void;

type UseManuscriptSidebarDataOptions = {
  applyFetchedManuscript: (manuscript: Manuscript) => void;
  isSidebarOpen: boolean;
  selectedManuscriptId: string;
  selectedSceneIds: string[];
  selectedStoryId: string;
  sidebarTab: WorkbenchSidebarTab;
  toast: ToastFn;
};

const VERSION_PAGE_SIZE = 10;

export function useManuscriptSidebarData({
  applyFetchedManuscript,
  isSidebarOpen,
  selectedManuscriptId,
  selectedSceneIds,
  selectedStoryId,
  sidebarTab,
  toast,
}: UseManuscriptSidebarDataOptions) {
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
  const [chapterRange, setChapterRange] = useState("");
  const [includeTitlePage, setIncludeTitlePage] = useState(true);
  const [includeTableOfContents, setIncludeTableOfContents] = useState(true);
  const [txtEncoding, setTxtEncoding] = useState("UTF-8");
  const [exportAuthorName, setExportAuthorName] = useState("");

  const visibleVersions = useMemo(() => versions.slice(0, versionVisibleCount), [versionVisibleCount, versions]);
  const hasMoreVersions = versionVisibleCount < versions.length;

  const loadGoals = useCallback(async () => {
    try {
      setGoals(await api.v2.workspace.listGoals());
    } catch {
      setGoals([]);
    }
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

  const loadStats = useCallback(async () => {
    try {
      setWorkspaceStats(await api.v2.workspace.getStats());
    } catch (e: any) {
      toast({ variant: "destructive", title: "加载统计失败", description: e.message });
    }
  }, [toast]);

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
    } catch (e: any) {
      toast({ variant: "destructive", title: "对比失败", description: e.message });
    }
  }, [selectedDiffVersions, selectedManuscriptId, toast]);

  const summarizeDiff = useCallback(async () => {
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
  }, [diffResult, selectedManuscriptId, toast]);

  const createExportJob = useCallback(async () => {
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

  const createGoal = useCallback(async () => {
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
  }, [goalTargetValue, goalType, loadGoals, selectedStoryId, toast]);

  const updateGoal = useCallback(async (goalId: string, patch: Record<string, unknown>) => {
    try {
      await api.v2.workspace.updateGoal(goalId, patch);
      await loadGoals();
      toast({ title: "目标已更新" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "更新目标失败", description: e.message });
    }
  }, [loadGoals, toast]);

  const deleteGoal = useCallback(async (goalId: string) => {
    try {
      await api.v2.workspace.deleteGoal(goalId);
      await loadGoals();
      toast({ title: "目标已删除" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "删除目标失败", description: e.message });
    }
  }, [loadGoals, toast]);

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
      toast({ title: "分支创建成功" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "创建分支失败", description: e.message });
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
      toast({ title: "分支已切换" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "切换分支失败", description: e.message });
    }
  }, [applyFetchedManuscript, loadVersions, selectedManuscriptId, toast]);

  const rollbackVersion = useCallback(async (versionId: string) => {
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
      toast({ title: "模板创建成功" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "创建模板失败", description: e.message });
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

  const updateTemplate = useCallback(async (template: any) => {
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
  }, [exportFormat, loadExport, toast]);

  const deleteTemplate = useCallback(async (templateId: string) => {
    try {
      await api.v2.export.deleteTemplate(templateId);
      await loadExport();
      toast({ title: "模板已删除" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "删除模板失败", description: e.message });
    }
  }, [loadExport, toast]);

  useEffect(() => {
    void loadGoals();
  }, [loadGoals]);

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

  useEffect(() => {
    if (!isSidebarOpen) return;
    if (sidebarTab === "context") void loadContextPreview();
    if (sidebarTab === "version") void loadVersions();
    if (sidebarTab === "export") void loadExport();
    if (sidebarTab === "stats") void loadStats();
    if (sidebarTab === "goals") void loadGoals();
  }, [isSidebarOpen, loadContextPreview, loadExport, loadGoals, loadStats, loadVersions, sidebarTab]);

  return {
    aiDiffSummary,
    autoSaveConfig,
    branches,
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
