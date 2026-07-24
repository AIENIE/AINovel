import { useCallback, useState } from "react";
import { api } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import type { Manuscript } from "@/types";
import { qualityStatusText, stripHtml } from "@/pages/Workbench/tabs/manuscript-writer/shared";

type GenerationMode = "fast" | "crafted";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: "default" | "destructive";
}) => void;

type UseManuscriptSceneGenerationOptions = {
  loadPlotQuality: (sceneId?: string, manuscriptId?: string) => Promise<{ run: unknown; trend: unknown }>;
  loadSlopQuality: (sceneId?: string, manuscriptId?: string) => Promise<unknown>;
  applyServerSection: (manuscript: Manuscript, sceneId: string) => void;
  cancelPendingSectionSave: (sceneId: string) => void;
  selectedManuscriptId: string;
  selectedSceneId: string;
  toast: ToastFn;
};

const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

async function readGeneratedManuscript(manuscriptId: string, sceneId: string): Promise<Manuscript> {
  let latest: Manuscript | null = null;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    latest = await api.manuscripts.get(manuscriptId);
    const generatedHtml = latest.sections?.[sceneId];
    if (typeof generatedHtml === "string" && stripHtml(generatedHtml)) return latest;
    if (attempt < 2) await wait(500);
  }
  throw new Error("生成结果没有可用正文，请重试");
}

export function useManuscriptSceneGeneration({
  loadPlotQuality,
  loadSlopQuality,
  applyServerSection,
  cancelPendingSectionSave,
  selectedManuscriptId,
  selectedSceneId,
  toast,
}: UseManuscriptSceneGenerationOptions) {
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationMode, setGenerationMode] = useState<GenerationMode>("fast");

  const generateScene = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    cancelPendingSectionSave(sceneId);
    setIsGenerating(true);
    try {
      await runTrackedAiOperation(api.manuscripts.startGenerateScene(selectedManuscriptId, sceneId, generationMode));
      const saved = await readGeneratedManuscript(selectedManuscriptId, sceneId);
      const generatedHtml = saved.sections?.[sceneId];
      if (typeof generatedHtml !== "string" || !stripHtml(generatedHtml)) {
        throw new Error("生成结果没有可用正文，请重试");
      }
      applyServerSection(saved, sceneId);
      const latestRun = await loadSlopQuality(sceneId, saved.id).catch((): null => null);
      await loadPlotQuality(sceneId, saved.id).catch((): { run: null; trend: null } => ({ run: null, trend: null }));
      toast({
        title: "已生成场景正文",
        description: qualityStatusText(latestRun as any),
      });
    } catch (e: any) {
      toast({ variant: "destructive", title: "生成失败", description: e.message });
    } finally {
      setIsGenerating(false);
    }
  }, [
    generationMode,
    loadPlotQuality,
    loadSlopQuality,
    applyServerSection,
    cancelPendingSectionSave,
    selectedManuscriptId,
    selectedSceneId,
    toast,
  ]);

  return {
    generateScene,
    generationMode,
    isGenerating,
    setGenerationMode,
  };
}
