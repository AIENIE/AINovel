import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AlertCircle, BookOpen, Check, ChevronRight, Coins, Loader2, RefreshCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { CreationWorkflow } from "@/types";

export function GenerationState({ workflow }: { workflow: CreationWorkflow }) {
  const { t } = useTranslation();
  const progress = workflow.activeJob?.progress ?? (workflow.autoRun ? 12 : 0);
  const stepLabel = workflow.activeJob?.operation === "OUTLINE_DEVELOP" ? t("guided.genDevelopDirection")
    : workflow.activeJob?.operation === "OUTLINE_REWRITE" ? t("guided.genRewriteDirection")
      : workflow.activeJob?.operation === "OUTLINE_EXPAND" ? t("guided.genExpandOutline")
        : workflow.currentStep === "PREMISE" ? t("guided.genPremise")
          : workflow.currentStep === "WORLD" ? t("guided.genWorld")
            : workflow.currentStep === "CHARACTERS" ? t("guided.genCharacters")
              : workflow.currentStep === "OUTLINE" ? t("guided.genOutline")
                : t("guided.genInit");
  const baseCompleted = workflow.currentStep === "WORLD" ? 1
    : workflow.currentStep === "CHARACTERS" ? 2
      : workflow.currentStep === "OUTLINE" ? (workflow.activeJob?.operation === "OUTLINE_EXPAND" ? 4 : 3)
        : workflow.currentStep === "COMPLETED" ? 5 : 0;
  const remaining = Math.max(0, 5 - baseCompleted);
  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center border-y border-zinc-200 py-16 text-center">
      <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-800"><Loader2 className="h-5 w-5 animate-spin" /></div>
      <h3 className="text-base font-semibold">{t("guided.currentStepLabel", { label: stepLabel })}</h3>
      <div className="mt-6 w-full max-w-xs"><Progress value={progress} className="h-1.5" /></div>
      <div className="mt-3 space-y-1 text-xs text-zinc-500">
        <p>{workflow.activeJob?.status === "CALLING_AI" ? t("guided.aiGenerating") : t("guided.queued")}</p>
        <p>{t("guided.stepsCompleted", { completed: baseCompleted, remaining })}</p>
        <p>{t("guided.tokensOutput", { count: workflow.activeJob?.outputTokens ?? 0, formatted: (workflow.activeJob?.outputTokens ?? 0).toLocaleString() })}{workflow.activeJob?.outputTokensEstimated ? t("guided.tokensEstimated") : ""}</p>
      </div>
    </div>
  );
}

export function FailureState({ workflow, busy, onRetry }: { workflow: CreationWorkflow; busy: boolean; onRetry: () => void }) {
  const { t } = useTranslation();
  const insufficient = workflow.errorMessage?.includes("积分不足");
  return (
    <div className="mx-auto flex min-h-[520px] max-w-xl flex-col items-center justify-center text-center">
      <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-700"><AlertCircle className="h-5 w-5" /></div>
      <h2 className="text-xl font-semibold">{insufficient ? t("guided.insufficientCredits") : t("guided.stepIncomplete")}</h2>
      <p className="mt-3 max-w-md text-sm leading-6 text-zinc-600">{workflow.errorMessage || t("guided.generateFailedRetry")}</p>
      <div className="mt-7 flex gap-3">
        {insufficient ? <Button variant="outline" asChild><Link to="/profile"><Coins className="mr-2 h-4 w-4" /> {t("guided.redeemCredits")}</Link></Button> : null}
        <Button className="bg-zinc-950 text-white hover:bg-zinc-800" disabled={busy} onClick={onRetry}>{busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCcw className="mr-2 h-4 w-4" />}{t("common.retry")}</Button>
      </div>
    </div>
  );
}

export function CompletedState({ workflow, onOpenWorkbench }: { workflow: CreationWorkflow; onOpenWorkbench: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="mx-auto flex min-h-[560px] max-w-xl flex-col items-center justify-center text-center animate-in fade-in zoom-in-95 duration-300">
      <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-700 text-white"><Check className="h-6 w-6" /></div>
      <p className="mb-2 text-xs font-semibold uppercase text-emerald-700">Step 04 complete</p>
      <h2 className="text-2xl font-semibold md:text-3xl">{t("guided.completedTitle")}</h2>
      <p className="mt-3 text-sm text-zinc-500">{t("guided.completedDesc")}</p>
      <div className="mt-8 flex gap-3">
        <Button variant="outline" asChild><Link to="/novels"><BookOpen className="mr-2 h-4 w-4" /> {t("guided.novelList")}</Link></Button>
        <Button className="bg-zinc-950 text-white hover:bg-zinc-800" onClick={onOpenWorkbench}>{t("guided.enterWorkbench")} <ChevronRight className="ml-2 h-4 w-4" /></Button>
      </div>
      <p className="mt-6 text-xs text-zinc-400">{t("guided.completedMeta", { count: workflow.targetChapterCount, mode: workflow.autoRun ? t("guided.autoMode") : t("guided.stepConfirmMode") })}</p>
    </div>
  );
}

export function LoadingState() {
  return <div className="flex min-h-[520px] items-center justify-center"><Loader2 className="h-5 w-5 animate-spin text-emerald-700" /></div>;
}
