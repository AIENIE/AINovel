import { sceneGenerationOutcome, SCENE_CANDIDATES_EVENT } from "@/lib/scene-candidates";
import { useCallback, useRef, useState } from "react";
import { api } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";
import type { Manuscript, PlotQualityRun, PlotQualityTrend, SlopQualityRun } from "@/types";
import { qualityStatusText, stripHtml } from "@/pages/Workbench/tabs/manuscript-writer/shared";
import { t } from "@/i18n";
import { localizedErrorMessage } from "@/lib/error-messages";
import { sceneContextPath } from '@/lib/api/domains/world-context';
import { reviewWorldPreview } from '@/lib/world-context-preview';

type GenerationMode = "fast" | "crafted";

type ToastFn = (options: {
  description?: string;
  title?: string;
  variant?: "default" | "destructive";
}) => void;

type UseManuscriptSceneGenerationOptions = {
  loadPlotQuality: (sceneId?: string, manuscriptId?: string) => Promise<{ run: PlotQualityRun | null; trend: PlotQualityTrend | null }>;
  loadSlopQuality: (sceneId?: string, manuscriptId?: string) => Promise<SlopQualityRun | null>;
  applyServerSection: (manuscript: Manuscript, sceneId: string) => void;
  cancelPendingSectionSave: (sceneId: string) => void;
  selectedManuscriptId: string;
  selectedSceneId: string;
  toast: ToastFn;
  hasUnsavedChanges?: boolean;
  onSourceResolved?: (manuscript: Manuscript) => void;
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
  throw new Error(t("sceneGeneration.noGeneratedContent"));
}

export function useManuscriptSceneGeneration({
  loadPlotQuality,
  loadSlopQuality,
  applyServerSection,
  cancelPendingSectionSave,
  selectedManuscriptId,
  selectedSceneId,
  toast,
  hasUnsavedChanges = false,
  onSourceResolved,
}: UseManuscriptSceneGenerationOptions) {
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationMode, setGenerationMode] = useState<GenerationMode>("fast");
  const [generationRevision, setGenerationRevision] = useState(0);
  const latest=useRef({selectedManuscriptId,selectedSceneId,hasUnsavedChanges});
  latest.current={selectedManuscriptId,selectedSceneId,hasUnsavedChanges};

  const generateScene = useCallback(async () => {
    if (!selectedManuscriptId || !selectedSceneId) return;
    if (hasUnsavedChanges) { toast({ description: t("sceneCandidates.saveFirst") }); return; }
    const sceneId = selectedSceneId;
    const unchanged=()=>latest.current.selectedManuscriptId===selectedManuscriptId&&latest.current.selectedSceneId===sceneId&&!latest.current.hasUnsavedChanges;
    cancelPendingSectionSave(sceneId);
    setIsGenerating(true);
    try {
      const path=sceneContextPath(selectedManuscriptId,sceneId), request={purpose:'SCENE',mode:generationMode};
      const preview=await reviewWorldPreview(path,request,await api.worldContext.preview(path,request));
      if(!unchanged()){toast({description:t('world4.stale')});return;}
      const operation = await runTrackedAiOperation(api.manuscripts.startGenerateScene(selectedManuscriptId, sceneId, generationMode,{contextHash:preview.contextHash,expectedSelectionRevision:preview.preferences.revision}));
      const outcome = sceneGenerationOutcome(operation.resultJson);
      if(!unchanged()){window.dispatchEvent(new CustomEvent(SCENE_CANDIDATES_EVENT));toast({description:t('world4.stale')});return;}
      if (outcome?.outcome === "NEEDS_REVIEW") {
        // Initial generation can create the main branch even though no scene was saved.
        if (onSourceResolved) onSourceResolved(await api.manuscripts.get(selectedManuscriptId));
        setGenerationRevision(current => current + 1);
        window.dispatchEvent(new CustomEvent(SCENE_CANDIDATES_EVENT, { detail: outcome }));
        toast({ title: t("sceneCandidates.needsReview") });
        return;
      }
      const saved = await readGeneratedManuscript(selectedManuscriptId, sceneId);
      if(!unchanged()){toast({description:t('world4.stale')});return;}
      const generatedHtml = saved.sections?.[sceneId];
      if (typeof generatedHtml !== "string" || !stripHtml(generatedHtml)) {
        throw new Error(t("sceneGeneration.noGeneratedContent"));
      }
      applyServerSection(saved, sceneId);
      setGenerationRevision((current) => current + 1);
      const latestRun = await loadSlopQuality(sceneId, saved.id).catch((): null => null);
      await loadPlotQuality(sceneId, saved.id).catch((): { run: null; trend: null } => ({ run: null, trend: null }));
      toast({
        title: t("sceneGeneration.generated"),
        description: qualityStatusText(latestRun),
      });
    } catch (e: unknown) {
      toast({ variant: "destructive", title: t("sceneGeneration.generationFailed"), description: localizedErrorMessage(e) });
    } finally {
      setIsGenerating(false);
    }
  }, [
    hasUnsavedChanges,
    onSourceResolved,
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
    generationRevision,
    generationMode,
    isGenerating,
    setGenerationMode,
  };
}
