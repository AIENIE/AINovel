import type { PlotQualityRun, SlopQualityRun } from "@/types";

export const qualityStatusText = (run?: SlopQualityRun | null) => {
  if (!run) return "未运行质量门禁";
  if (run.analysisMode === "manual_scene") {
    if (run.status === "DEGRADED") return "文本诊断降级";
    if (run.status === "ACCEPTED_WITH_ISSUES" || ["high", "critical"].includes(run.riskLabel || "")) return "文本风险待处理";
    return "文本诊断通过";
  }
  if (run.status === "REVISED" || run.revised) return "已自动修订";
  if (run.status === "ACCEPTED_WITH_ISSUES" || ["HIGH", "BLOCKING"].includes(run.maxSeverity)) return "仍有建议";
  return "质量门禁通过";
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
      return "表层模板";
    case "voice_fit":
      return "语域贴合";
    case "consistency_assimilation":
      return "设定吸收";
    case "breath_focus_pacing":
      return "呼吸节奏";
    case "human_trace":
      return "作者痕迹";
    default:
      return module || "文本风险";
  }
};

export const slopRewriteTaskTitle = (task: any, index: number) => task?.task_id || task?.taskId || `R${index + 1}`;

export const plotStatusText = (run?: PlotQualityRun | null) => {
  if (!run) return "未运行剧情诊断";
  if (run.revisionApplied) return "候选已采纳";
  if (run.status === "DEGRADED") return "诊断降级";
  if (["HIGH", "BLOCKING"].includes(run.maxSeverity) || run.overallRiskScore >= 70) return "剧情高风险";
  if (run.status === "ACCEPTED_WITH_ISSUES" || run.overallRiskScore >= 40) return "剧情需关注";
  return "剧情风险低";
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
    GOAL_CONFLICT: "目标冲突",
    CAUSALITY: "因果链",
    AGENCY: "角色能动性",
    STAKES: "风险收益",
    FORESHADOW_PAYOFF: "伏笔回收",
    REPETITION: "重复套路",
    SCENE_FUNCTION: "场景功能",
    READER_CURIOSITY: "读者悬念",
  };
  return labels[String(dimension || "")] || String(dimension || "未分类");
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
  if (type === "auto") return "自动";
  if (type === "branch_point") return "分支点";
  if (type === "merge") return "合并";
  return "手动";
};
