import { CreationWorkflow, GuidedCreationCandidate, GuidedCreationStep } from "@/types";
import { t } from "@/i18n";

export const GUIDED_STEPS: { key: Exclude<GuidedCreationStep, "COMPLETED">; label: string; number: string }[] = [
  { key: "PREMISE", label: t("guided.stepPremise"), number: "01" },
  { key: "WORLD", label: t("guided.stepWorld"), number: "02" },
  { key: "CHARACTERS", label: t("guided.stepCharacters"), number: "03" },
  { key: "OUTLINE", label: t("guided.stepOutline"), number: "04" },
];

export const GUIDED_STEP_TITLES: Record<GuidedCreationStep, string> = {
  PREMISE: t("guided.titlePremise"),
  WORLD: t("guided.titleWorld"),
  CHARACTERS: t("guided.titleCharacters"),
  OUTLINE: t("guided.titleOutline"),
  COMPLETED: t("guided.titleCompleted"),
};

export function selectedSummary(candidate?: GuidedCreationCandidate) {
  if (!candidate) return t("guided.confirmed");
  for (const key of ["title", "name", "label", "synopsis"]) {
    if (typeof candidate[key] === "string" && candidate[key]) return String(candidate[key]);
  }
  return t("guided.confirmed");
}

export function guidedStatusLabel(workflow: CreationWorkflow) {
  if (workflow.status === "COMPLETED") return t("guided.statusCompleted");
  if (workflow.status === "FAILED") return t("guided.statusFailed");
  if (workflow.status === "AUTO_RUNNING") return t("guided.statusAutoRunning");
  if (workflow.activeJob) return t("guided.statusGenerating");
  return t("guided.statusWaiting");
}

export function guidedStepNumber(step: GuidedCreationStep) {
  const index = GUIDED_STEPS.findIndex((item) => item.key === step);
  return index >= 0 ? `Step 0${index + 1}` : "Complete";
}
