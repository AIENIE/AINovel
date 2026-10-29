import type { NetworkObject } from "@/lib/api-client";
import { t } from "@/i18n";
import type { PlotQualityRun, SlopQualityRun, SlopRewriteTask } from "@/types";

export const qualityStatusText = (run?: SlopQualityRun | null) => {
  if (!run) return t("qualityStatus.notRun");
  if (run.review) {
    if (run.review.coverage !== "COMPLETE") return "正文未完整检查";
    return run.review.findings.some(f => f.judgment === "CONFIRMED" && ["LANGUAGE", "OUTPUT_ARTIFACT"].includes(f.kind) && f.location.start !== null)
      ? "冻结正文有确认问题" : "冻结正文未见确认问题";
  }
  if (run.status === "DEGRADED") return "历史检查不完整";
  if (run.issues?.length || (run.overallRiskScore ?? -1) >= 40) return "历史报告有建议";
  return "历史报告（未验证当前正文）";
};

export const qualityStatusClass = (run?: SlopQualityRun | null) => {
  if (!run) return "border-zinc-300 text-zinc-500";
  if (run.analysisMode === "manual_scene" && run.status === "DEGRADED") return "border-amber-300 bg-amber-50 text-amber-800";
  if (run.status === "REVISED" || run.revised) return "border-amber-300 bg-amber-50 text-amber-800";
  if (run.status === "ACCEPTED_WITH_ISSUES" || ["HIGH", "BLOCKING"].includes(run.maxSeverity ?? "")) return "border-red-300 bg-red-50 text-red-700";
  return "border-zinc-300 bg-zinc-50 text-zinc-700";
};

export const qualityRiskText = (run?: SlopQualityRun | null) =>
  run?.review && run.review.coverage !== "COMPLETE" ? "未检查" : run?.overallRiskScore ?? "-";

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

export const slopRewriteTaskTitle = (task: SlopRewriteTask, index: number) => task?.task_id || task?.taskId || `R${index + 1}`;

export const plotStatusText = (run?: PlotQualityRun | null) => {
  if (!run) return t("plotStatus.notRun");
  if (run.revisionApplied) return t("plotStatus.candidateAdopted");
  if (run.status === "DEGRADED") return t("plotStatus.degraded");
  if (["HIGH", "BLOCKING"].includes(run.maxSeverity ?? "") || (run.overallRiskScore ?? -1) >= 70) return t("plotStatus.highRisk");
  if (run.status === "ACCEPTED_WITH_ISSUES" || (run.overallRiskScore ?? -1) >= 40) return t("plotStatus.needsAttention");
  return t("plotStatus.lowRisk");
};

export const plotStatusClass = (run?: PlotQualityRun | null) => {
  if (!run) return "border-zinc-300 text-zinc-500";
  if (run.revisionApplied) return "border-blue-300 bg-blue-50 text-blue-700";
  if (run.status === "DEGRADED") return "border-zinc-300 bg-zinc-50 text-zinc-700";
  if (["HIGH", "BLOCKING"].includes(run.maxSeverity ?? "") || (run.overallRiskScore ?? -1) >= 70) return "border-red-300 bg-red-50 text-red-700";
  if (run.status === "ACCEPTED_WITH_ISSUES" || (run.overallRiskScore ?? -1) >= 40) return "border-amber-300 bg-amber-50 text-amber-800";
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

export const formatDateTime = (value: unknown) => {
  if (!value) return "-";
  const d = new Date(String(value));
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

export const versionWordCount = (version: NetworkObject): { value: number | null; invalid: boolean } => {
  const metadata = version?.metadata?.word_count ?? version?.metadata?.wordCount;
  if (metadata != null) {
    const value = Number(metadata);
    return Number.isFinite(value) && value >= 0 ? { value: Math.round(value), invalid: false } : { value: null, invalid: true };
  }
  if (version?.sectionsJson == null) return { value: null, invalid: false };
  try {
    const sections: unknown = typeof version.sectionsJson === "string" ? JSON.parse(version.sectionsJson) : version.sectionsJson;
    if (!sections || typeof sections !== "object" || Array.isArray(sections)) return { value: null, invalid: true };
    const values = Object.values(sections);
    if (values.some(scene => typeof scene !== "string")) return { value: null, invalid: true };
    return { value: (values as string[]).reduce((total, scene) => total + countWords(stripHtml(scene)), 0), invalid: false };
  } catch { return { value: null, invalid: true }; }
};

export const snapshotTypeLabel = (snapshotType: unknown) => {
  const type = String(snapshotType || "manual").toLowerCase();
  if (type === "auto") return t("snapshotType.auto");
  if (type === "branch_point") return t("snapshotType.branchPoint");
  if (type === "merge") return t("snapshotType.merge");
  return t("snapshotType.manual");
};
