import { useCallback, useState } from "react";
import { api } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import { qualityStatusText } from "@/pages/Workbench/tabs/manuscript-writer/shared";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: string;
}) => void;

type UseManuscriptSceneGenerationOptions = {
  loadPlotQuality: (sceneId?: string, manuscriptId?: string) => Promise<unknown>;
  loadSlopQuality: (sceneId?: string, manuscriptId?: string) => Promise<unknown>;
  replaceManuscript: (manuscript: Manuscript) => void;
  selectedManuscriptId: string;
  selectedSceneId: string;
  setContent: (content: string) => void;
  toast: ToastFn;
};

export function useManuscriptSceneGeneration({
  loadPlotQuality,
  loadSlopQuality,
  replaceManuscript,
  selectedManuscriptId,
  selectedSceneId,
  setContent,
  toast,
}: UseManuscriptSceneGenerationOptions) {
  const [isGenerating, setIsGenerating] = useState(false);

  const generateScene = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    const sceneId = selectedSceneId;
    setIsGenerating(true);
    try {
      const saved = await api.manuscripts.generateScene(selectedManuscriptId, sceneId);
      replaceManuscript(saved);
      setContent(saved.sections?.[sceneId] || "");
      const latestRun = await loadSlopQuality(sceneId, saved.id).catch(() => null);
      await loadPlotQuality(sceneId, saved.id).catch(() => ({ run: null, trend: null }));
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
    loadPlotQuality,
    loadSlopQuality,
    replaceManuscript,
    selectedManuscriptId,
    selectedSceneId,
    setContent,
    toast,
  ]);

  return {
    generateScene,
    isGenerating,
  };
}
