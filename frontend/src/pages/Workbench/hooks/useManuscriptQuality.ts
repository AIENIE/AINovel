import { useCallback, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { Manuscript, PlotQualityRun, PlotQualityTrend, SlopQualityRun } from "@/types";
import { api } from "@/lib/api-client";
import { plotStatusText, qualityStatusText, slopRewriteTaskTitle } from "@/pages/Workbench/tabs/manuscript-writer/shared";
import type { WorkbenchSidebarTab } from "./useWorkbenchLayoutPersistence";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: string;
}) => void;

type UseManuscriptQualityOptions = {
  applyFetchedManuscript: (manuscript: Manuscript) => void;
  content: string;
  dirtyScenes: Record<string, boolean>;
  persistSection: (sceneId: string, html: string, silent?: boolean) => Promise<void>;
  selectedManuscriptId: string;
  selectedSceneId: string;
  setContent: (content: string) => void;
  setSidebarTab: (tab: WorkbenchSidebarTab) => void;
  toast: ToastFn;
};

const WORKBENCH_QUERY_STALE_TIME = 60_000;

const slopQualityQueryKey = (manuscriptId: string, sceneId: string) =>
  ["workbench", "quality", "slop", manuscriptId, sceneId] as const;
const plotRunQueryKey = (manuscriptId: string, sceneId: string) =>
  ["workbench", "quality", "plot-run", manuscriptId, sceneId] as const;
const plotTrendQueryKey = (manuscriptId: string) =>
  ["workbench", "quality", "plot-trend", manuscriptId] as const;

async function fetchSlopQualityRun(manuscriptId: string, sceneId: string) {
  const runs = await api.v2.quality.listRuns(manuscriptId, sceneId);
  return runs[0] || null;
}

async function fetchPlotRun(manuscriptId: string, sceneId: string) {
  const runs = await api.v2.plotQuality.listRuns(manuscriptId, sceneId);
  return runs[0] || null;
}

async function fetchPlotTrend(manuscriptId: string) {
  return await api.v2.plotQuality.getTrend(manuscriptId);
}

export function useManuscriptQuality({
  applyFetchedManuscript,
  content,
  dirtyScenes,
  persistSection,
  selectedManuscriptId,
  selectedSceneId,
  setContent,
  setSidebarTab,
  toast,
}: UseManuscriptQualityOptions) {
  const queryClient = useQueryClient();
  const [isSlopBusy, setIsSlopBusy] = useState(false);
  const [isPlotBusy, setIsPlotBusy] = useState(false);
  const [isPlotRevisionBusy, setIsPlotRevisionBusy] = useState(false);

  const slopRunQuery = useQuery({
    queryKey: slopQualityQueryKey(selectedManuscriptId, selectedSceneId),
    queryFn: () => fetchSlopQualityRun(selectedManuscriptId, selectedSceneId),
    enabled: Boolean(selectedManuscriptId && selectedSceneId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const plotRunQuery = useQuery({
    queryKey: plotRunQueryKey(selectedManuscriptId, selectedSceneId),
    queryFn: () => fetchPlotRun(selectedManuscriptId, selectedSceneId),
    enabled: Boolean(selectedManuscriptId && selectedSceneId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const plotTrendQuery = useQuery({
    queryKey: plotTrendQueryKey(selectedManuscriptId),
    queryFn: () => fetchPlotTrend(selectedManuscriptId),
    enabled: Boolean(selectedManuscriptId),
    staleTime: WORKBENCH_QUERY_STALE_TIME,
    retry: false,
    refetchOnWindowFocus: false,
  });

  const selectedQualityRun = selectedSceneId ? slopRunQuery.data ?? null : null;
  const selectedPlotRun = selectedSceneId ? plotRunQuery.data ?? null : null;
  const plotTrend = selectedManuscriptId ? plotTrendQuery.data ?? null : null;

  const qualityRunsByScene = useMemo(
    () => (selectedSceneId ? { [selectedSceneId]: selectedQualityRun } : {}),
    [selectedQualityRun, selectedSceneId],
  );
  const plotRunsByScene = useMemo(
    () => (selectedSceneId ? { [selectedSceneId]: selectedPlotRun } : {}),
    [selectedPlotRun, selectedSceneId],
  );

  const ensureSceneSaved = useCallback(
    async (sceneId: string) => {
      if (!dirtyScenes[sceneId]) return;
      await persistSection(sceneId, content, true);
    },
    [content, dirtyScenes, persistSection],
  );

  const loadSlopQuality = useCallback(
    async (sceneId = selectedSceneId, manuscriptId = selectedManuscriptId) => {
      if (!manuscriptId || !sceneId) return null;
      return await queryClient.fetchQuery({
        queryKey: slopQualityQueryKey(manuscriptId, sceneId),
        queryFn: () => fetchSlopQualityRun(manuscriptId, sceneId),
        staleTime: 0,
        retry: false,
      });
    },
    [queryClient, selectedManuscriptId, selectedSceneId],
  );

  const loadPlotQuality = useCallback(
    async (sceneId = selectedSceneId, manuscriptId = selectedManuscriptId) => {
      if (!manuscriptId || !sceneId) return { run: null, trend: null };
      const [run, trend] = await Promise.all([
        queryClient.fetchQuery({
          queryKey: plotRunQueryKey(manuscriptId, sceneId),
          queryFn: () => fetchPlotRun(manuscriptId, sceneId),
          staleTime: 0,
          retry: false,
        }),
        queryClient.fetchQuery({
          queryKey: plotTrendQueryKey(manuscriptId),
          queryFn: () => fetchPlotTrend(manuscriptId),
          staleTime: 0,
          retry: false,
        }),
      ]);
      return { run, trend };
    },
    [queryClient, selectedManuscriptId, selectedSceneId],
  );

  const runSlopDiagnosis = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    setIsSlopBusy(true);
    try {
      await ensureSceneSaved(sceneId);
      const run = await api.v2.quality.analyzeScene(selectedManuscriptId, sceneId);
      queryClient.setQueryData(slopQualityQueryKey(selectedManuscriptId, sceneId), run);
      setSidebarTab("plot");
      toast({ title: "文本 Slop 诊断已完成", description: run.safeClaim || qualityStatusText(run) });
    } catch (e: any) {
      toast({ variant: "destructive", title: "文本诊断失败", description: e.message });
    } finally {
      setIsSlopBusy(false);
    }
  }, [ensureSceneSaved, queryClient, selectedManuscriptId, selectedSceneId, setSidebarTab, toast]);

  const copySlopRewriteTask = useCallback(async (task: any, index: number) => {
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
  }, [toast]);

  const runPlotDiagnosis = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    setIsPlotBusy(true);
    try {
      await ensureSceneSaved(sceneId);
      const run = await api.v2.plotQuality.analyzeScene(selectedManuscriptId, sceneId);
      const trend = await api.v2.plotQuality.getTrend(selectedManuscriptId);
      queryClient.setQueryData(plotRunQueryKey(selectedManuscriptId, sceneId), run);
      queryClient.setQueryData(plotTrendQueryKey(selectedManuscriptId), trend);
      setSidebarTab("plot");
      toast({ title: "剧情诊断已完成", description: plotStatusText(run) });
    } catch (e: any) {
      toast({ variant: "destructive", title: "剧情诊断失败", description: e.message });
    } finally {
      setIsPlotBusy(false);
    }
  }, [ensureSceneSaved, queryClient, selectedManuscriptId, selectedSceneId, setSidebarTab, toast]);

  const generatePlotRevisionCandidate = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId || !selectedPlotRun) return;
    const sceneId = selectedSceneId;
    setIsPlotRevisionBusy(true);
    try {
      await ensureSceneSaved(sceneId);
      const run = await api.v2.plotQuality.generateRevisionCandidate(selectedManuscriptId, selectedPlotRun.id);
      queryClient.setQueryData(plotRunQueryKey(selectedManuscriptId, sceneId), run);
      toast({ title: "候选修订已生成" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "生成候选失败", description: e.message });
    } finally {
      setIsPlotRevisionBusy(false);
    }
  }, [ensureSceneSaved, queryClient, selectedManuscriptId, selectedPlotRun, selectedSceneId, toast]);

  const applyPlotRevision = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId || !selectedPlotRun) return;
    const sceneId = selectedSceneId;
    setIsPlotRevisionBusy(true);
    try {
      await ensureSceneSaved(sceneId);
      const run = await api.v2.plotQuality.applyRevision(selectedManuscriptId, selectedPlotRun.id);
      const manuscript = await api.manuscripts.get(selectedManuscriptId);
      applyFetchedManuscript(manuscript);
      setContent(manuscript.sections?.[sceneId] || "");
      queryClient.setQueryData(plotRunQueryKey(selectedManuscriptId, sceneId), run);
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
  }, [
    applyFetchedManuscript,
    ensureSceneSaved,
    loadPlotQuality,
    queryClient,
    selectedManuscriptId,
    selectedPlotRun,
    selectedSceneId,
    setContent,
    toast,
  ]);

  return {
    applyPlotRevision,
    copySlopRewriteTask,
    generatePlotRevisionCandidate,
    isPlotBusy,
    isPlotRevisionBusy,
    isSlopBusy,
    loadPlotQuality,
    loadSlopQuality,
    plotRunsByScene,
    plotTrend,
    qualityRunsByScene,
    runPlotDiagnosis,
    runSlopDiagnosis,
    selectedPlotRun,
    selectedQualityRun,
  };
}
