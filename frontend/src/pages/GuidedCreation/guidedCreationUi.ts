import { CreationWorkflow, GuidedCreationCandidate, GuidedCreationStep } from "@/types";

export const GUIDED_STEPS: { key: Exclude<GuidedCreationStep, "COMPLETED">; label: string; number: string }[] = [
  { key: "PREMISE", label: "故事方向", number: "01" },
  { key: "WORLD", label: "世界设定", number: "02" },
  { key: "CHARACTERS", label: "角色阵容", number: "03" },
  { key: "OUTLINE", label: "章节大纲", number: "04" },
];

export const GUIDED_STEP_TITLES: Record<GuidedCreationStep, string> = {
  PREMISE: "选择故事方向",
  WORLD: "确定世界设定",
  CHARACTERS: "组建主要角色",
  OUTLINE: "确认章节大纲",
  COMPLETED: "创作准备完成",
};

export function selectedSummary(candidate?: GuidedCreationCandidate) {
  if (!candidate) return "已确认";
  for (const key of ["title", "name", "label", "synopsis"]) {
    if (typeof candidate[key] === "string" && candidate[key]) return String(candidate[key]);
  }
  return "已确认";
}

export function guidedStatusLabel(workflow: CreationWorkflow) {
  if (workflow.status === "COMPLETED") return "已完成";
  if (workflow.status === "FAILED") return "需要处理";
  if (workflow.status === "AUTO_RUNNING") return "自动推进中";
  if (workflow.activeJob) return "生成中";
  return "等待选择";
}

export function guidedStepNumber(step: GuidedCreationStep) {
  const index = GUIDED_STEPS.findIndex((item) => item.key === step);
  return index >= 0 ? `Step 0${index + 1}` : "Complete";
}
