import { useCallback, useEffect, useState } from "react";
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
  const [qualityRunsByScene, setQualityRunsByScene] = useState<Record<string, SlopQualityRun | null>>({});
  const [plotRunsByScene, setPlotRunsByScene] = useState<Record<string, PlotQualityRun | null>>({});
  const [plotTrend, setPlotTrend] = useState<PlotQualityTrend | null>(null);
  const [isSlopBusy, setIsSlopBusy] = useState(false);
  const [isPlotBusy, setIsPlotBusy] = useState(false);
  const [isPlotRevisionBusy, setIsPlotRevisionBusy] = useState(false);

  const selectedQualityRun = selectedSceneId ? qualityRunsByScene[selectedSceneId] : null;
  const selectedPlotRun = selectedSceneId ? plotRunsByScene[selectedSceneId] : null;

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
      const runs = await api.v2.quality.listRuns(manuscriptId, sceneId);
      const latestRun = runs[0] || null;
      setQualityRunsByScene((prev) => ({ ...prev, [sceneId]: latestRun }));
      return latestRun;
    },
    [selectedManuscriptId, selectedSceneId],
  );

  const loadPlotQuality = useCallback(
    async (sceneId = selectedSceneId, manuscriptId = selectedManuscriptId) => {
      if (!manuscriptId || !sceneId) return { run: null, trend: null };
      const [runs, trend] = await Promise.all([
        api.v2.plotQuality.listRuns(manuscriptId, sceneId),
        api.v2.plotQuality.getTrend(manuscriptId),
      ]);
      const latestRun = runs[0] || null;
      setPlotRunsByScene((prev) => ({ ...prev, [sceneId]: latestRun }));
      setPlotTrend(trend);
      return { run: latestRun, trend };
    },
    [selectedManuscriptId, selectedSceneId],
  );

  const runSlopDiagnosis = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    setIsSlopBusy(true);
    try {
      await ensureSceneSaved(sceneId);
      const run = await api.v2.quality.analyzeScene(selectedManuscriptId, sceneId);
      setQualityRunsByScene((prev) => ({ ...prev, [sceneId]: run }));
      setSidebarTab("plot");
      toast({ title: "文本 Slop 诊断已完成", description: run.safeClaim || qualityStatusText(run) });
    } catch (e: any) {
      toast({ variant: "destructive", title: "文本诊断失败", description: e.message });
    } finally {
      setIsSlopBusy(false);
    }
  }, [ensureSceneSaved, selectedManuscriptId, selectedSceneId, setSidebarTab, toast]);

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
  }, [ensureSceneSaved, selectedManuscriptId, selectedSceneId, setSidebarTab, toast]);

  const generatePlotRevisionCandidate = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId || !selectedPlotRun) return;
    const sceneId = selectedSceneId;
    setIsPlotRevisionBusy(true);
    try {
      await ensureSceneSaved(sceneId);
      const run = await api.v2.plotQuality.generateRevisionCandidate(selectedManuscriptId, selectedPlotRun.id);
      setPlotRunsByScene((prev) => ({ ...prev, [sceneId]: run }));
      toast({ title: "候选修订已生成" });
    } catch (e: any) {
      toast({ variant: "destructive", title: "生成候选失败", description: e.message });
    } finally {
      setIsPlotRevisionBusy(false);
    }
  }, [ensureSceneSaved, selectedManuscriptId, selectedPlotRun, selectedSceneId, toast]);

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
  }, [
    applyFetchedManuscript,
    ensureSceneSaved,
    loadPlotQuality,
    selectedManuscriptId,
    selectedPlotRun,
    selectedSceneId,
    setContent,
    toast,
  ]);

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
