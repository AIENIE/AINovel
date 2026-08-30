import type { NetworkObject } from "@/lib/api-client";
import { useCallback, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { Manuscript } from "@/types";
import { api } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import { plotStatusText, qualityStatusText, slopRewriteTaskTitle } from "@/pages/Workbench/tabs/manuscript-writer/shared";
import type { WorkbenchSidebarTab } from "./useWorkbenchLayoutPersistence";
import { t } from "@/i18n";
import { localizedErrorMessage } from "@/lib/error-messages";
import { isApiError } from "@/lib/api-client";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: "default" | "destructive";
}) => void;

type UseManuscriptQualityOptions = {
  applyServerSection: (manuscript: Manuscript, sceneId: string) => void;
  content: string;
  dirtyScenes: Record<string, boolean>;
  persistSection: (sceneId: string, html: string, silent?: boolean) => Promise<void>;
  selectedManuscriptId: string;
  selectedSceneId: string;
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
  applyServerSection,
  content,
  dirtyScenes,
  persistSection,
  selectedManuscriptId,
  selectedSceneId,
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
      await runTrackedAiOperation(api.v2.quality.startAnalyzeScene(selectedManuscriptId, sceneId));
      const run = await fetchSlopQualityRun(selectedManuscriptId, sceneId);
      queryClient.setQueryData(slopQualityQueryKey(selectedManuscriptId, sceneId), run);
      setSidebarTab("plot");
      toast({ title: t("manuscriptQuality.slopDone"), description: run.safeClaim || qualityStatusText(run) });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("manuscriptQuality.slopFailed"), description: localizedErrorMessage(e) });
    } finally {
      setIsSlopBusy(false);
    }
  }, [ensureSceneSaved, queryClient, selectedManuscriptId, selectedSceneId, setSidebarTab, toast]);

  const copySlopRewriteTask = useCallback(async (task: NetworkObject, index: number) => {
    const title = slopRewriteTaskTitle(task, index);
    const lines = [
      `${t("manuscriptQuality.taskLabel")} ${title}`,
      task?.problem ? `${t("manuscriptQuality.problemLabel")}：${task.problem}` : "",
      task?.repair_goal || task?.repairGoal ? `${t("manuscriptQuality.repairGoalLabel")}：${task.repair_goal || task.repairGoal}` : "",
      Array.isArray(task?.constraints) && task.constraints.length ? `${t("manuscriptQuality.constraintsLabel")}：${task.constraints.join("；")}` : "",
      t("manuscriptQuality.rewriteInstruction"),
    ].filter(Boolean);
    try {
      await navigator.clipboard.writeText(lines.join("\n"));
      toast({ title: t("manuscriptQuality.taskCopied") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("manuscriptQuality.copyFailed"), description: localizedErrorMessage(e) });
    }
  }, [toast]);

  const runPlotDiagnosis = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    setIsPlotBusy(true);
    try {
      await ensureSceneSaved(sceneId);
      await runTrackedAiOperation(api.v2.plotQuality.startAnalyzeScene(selectedManuscriptId, sceneId));
      const run = await fetchPlotRun(selectedManuscriptId, sceneId);
      if (!run) throw new Error(t("manuscriptQuality.noPlotResult"));
      const trend = await api.v2.plotQuality.getTrend(selectedManuscriptId);
      queryClient.setQueryData(plotRunQueryKey(selectedManuscriptId, sceneId), run);
      queryClient.setQueryData(plotTrendQueryKey(selectedManuscriptId), trend);
      setSidebarTab("plot");
      toast({ title: t("manuscriptQuality.plotDone"), description: plotStatusText(run) });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("manuscriptQuality.plotFailed"), description: localizedErrorMessage(e) });
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
      await runTrackedAiOperation(api.v2.plotQuality.startGenerateRevisionCandidate(selectedManuscriptId, selectedPlotRun.id));
      const run = await fetchPlotRun(selectedManuscriptId, sceneId);
      if (!run) throw new Error(t("manuscriptQuality.noRevisionResult"));
      queryClient.setQueryData(plotRunQueryKey(selectedManuscriptId, sceneId), run);
      toast({ title: t("manuscriptQuality.revisionGenerated") });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("manuscriptQuality.revisionGenerateFailed"), description: localizedErrorMessage(e) });
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
      applyServerSection(manuscript, sceneId);
      queryClient.setQueryData(plotRunQueryKey(selectedManuscriptId, sceneId), run);
      await loadPlotQuality(sceneId);
      toast({ title: t("manuscriptQuality.revisionApplied") });
    } catch (e: unknown) {
      toast({
        variant: "destructive",
        title: t("manuscriptQuality.revisionApplyFailed"),
        description: isApiError(e) && e.status === 409 ? t("manuscriptQuality.sceneChangedHint") : localizedErrorMessage(e),
      });
    } finally {
      setIsPlotRevisionBusy(false);
    }
  }, [
    applyServerSection,
    ensureSceneSaved,
    loadPlotQuality,
    queryClient,
    selectedManuscriptId,
    selectedPlotRun,
    selectedSceneId,
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
