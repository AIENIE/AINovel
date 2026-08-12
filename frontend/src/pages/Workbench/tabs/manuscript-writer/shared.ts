import { t } from "@/i18n";
import type { PlotQualityRun, SlopQualityRun } from "@/types";

export const qualityStatusText = (run?: SlopQualityRun | null) => {
  if (!run) return t("qualityStatus.notRun");
  if (run.analysisMode === "manual_scene") {
    if (run.status === "DEGRADED") return t("qualityStatus.degraded");
    if (run.status === "ACCEPTED_WITH_ISSUES" || ["high", "critical"].includes(run.riskLabel || "")) return t("qualityStatus.textPending");
    return t("qualityStatus.textPassed");
  }
  if (run.status === "REVISED" || run.revised) return t("qualityStatus.revised");
  if (run.status === "ACCEPTED_WITH_ISSUES" || ["HIGH", "BLOCKING"].includes(run.maxSeverity)) return t("qualityStatus.hasSuggestions");
  return t("qualityStatus.passed");
};

export const qualityStatusClass = (run?: SlopQualityRun | null) => {
  if (!run) return "border-zinc-300 text-zinc-500";
  if (run.analysisMode === "manual_scene" && run.status === "DEGRADED") return "border-amber-300 bg-amber-50 text-amber-800";
  if (run.status === "REVISED" || run.revised) return "border-amber-300 bg-amber-50 text-amber-800";
  if (run.status === "ACCEPTED_WITH_ISSUES" || ["HIGH", "BLOCKING"].includes(run.maxSeverity)) return "border-red-300 bg-red-50 text-red-700";
  return "border-emerald-300 bg-emerald-50 text-emerald-700";
};

export const slopModuleLabel = (module?: string) => {
  switch (module) {
    case "surface_template":
      return t("slopModule.surfaceTemplate");
    case "voice_fit":
      return t("slopModule.voiceFit");
    case "consistency_assimilation":
      return t("slopModule.assimilation");
    case "breath_focus_pacing":
      return t("slopModule.breathPacing");
    case "human_trace":
      return t("slopModule.humanTrace");
    default:
      return module || t("slopModule.textRisk");
  }
};

export const slopRewriteTaskTitle = (task: any, index: number) => task?.task_id || task?.taskId || `R${index + 1}`;

export const plotStatusText = (run?: PlotQualityRun | null) => {
  if (!run) return t("plotStatus.notRun");
  if (run.revisionApplied) return t("plotStatus.candidateAdopted");
  if (run.status === "DEGRADED") return t("plotStatus.degraded");
  if (["HIGH", "BLOCKING"].includes(run.maxSeverity) || run.overallRiskScore >= 70) return t("plotStatus.highRisk");
  if (run.status === "ACCEPTED_WITH_ISSUES" || run.overallRiskScore >= 40) return t("plotStatus.needsAttention");
  return t("plotStatus.lowRisk");
};

export const plotStatusClass = (run?: PlotQualityRun | null) => {
  if (!run) return "border-zinc-300 text-zinc-500";
  if (run.revisionApplied) return "border-blue-300 bg-blue-50 text-blue-700";
  if (run.status === "DEGRADED") return "border-zinc-300 bg-zinc-50 text-zinc-700";
  if (["HIGH", "BLOCKING"].includes(run.maxSeverity) || run.overallRiskScore >= 70) return "border-red-300 bg-red-50 text-red-700";
  if (run.status === "ACCEPTED_WITH_ISSUES" || run.overallRiskScore >= 40) return "border-amber-300 bg-amber-50 text-amber-800";
  return "border-emerald-300 bg-emerald-50 text-emerald-700";
};

export const plotDimensionLabel = (dimension?: string) => {
  const labels: Record<string, string> = {
    GOAL_CONFLICT: "plotDimension.goalConflict",
    CAUSALITY: "plotDimension.causality",
    AGENCY: "plotDimension.agency",
    STAKES: "plotDimension.stakes",
    FORESHADOW_PAYOFF: "plotDimension.foreshadowPayoff",
    REPETITION: "plotDimension.repetition",
    SCENE_FUNCTION: "plotDimension.sceneFunction",
    READER_CURIOSITY: "plotDimension.readerCuriosity",
  };
  return labels[String(dimension || "")] ? t(labels[String(dimension || "")]) : String(dimension || t("plotDimension.unclassified"));
};

export const formatDateTime = (value: any) => {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
};

export const stripHtml = (html: string) => {
  const div = document.createElement("div");
  div.innerHTML = html || "";
  return (div.textContent || div.innerText || "").trim();
};

export const countWords = (text: string) => {
  if (!text) return 0;
  return text.replace(/\s+/g, "").trim().length;
};

export const versionWordCount = (version: any) => {
  const fromMeta = Number(version?.metadata?.word_count ?? version?.metadata?.wordCount);
  if (Number.isFinite(fromMeta) && fromMeta >= 0) return Math.round(fromMeta);
  try {
    const sections = typeof version?.sectionsJson === "string" ? JSON.parse(version.sectionsJson) : version?.sectionsJson;
    if (!sections || typeof sections !== "object") return 0;
    return Object.values(sections as Record<string, unknown>).reduce<number>(
      (total, scene) => total + countWords(stripHtml(String(scene || ""))),
      0,
    );
  } catch {
    return 0;
  }
};

export const snapshotTypeLabel = (snapshotType: any) => {
  const type = String(snapshotType || "manual").toLowerCase();
  if (type === "auto") return t("snapshotType.auto");
  if (type === "branch_point") return t("snapshotType.branchPoint");
  if (type === "merge") return t("snapshotType.merge");
  return t("snapshotType.manual");
};
