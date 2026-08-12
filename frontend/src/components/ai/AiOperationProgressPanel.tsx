import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Ban, Loader2, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  resumeTrackedAiOperation,
  cancelTrackedAiOperation,
  retryTrackedAiOperation,
  useTrackedAiOperation,
} from "@/lib/ai-operation-store";
import { useAuth } from "@/contexts/AuthContext";

export function AiOperationProgressPanel() {
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();
  const operation = useTrackedAiOperation();
  useEffect(() => {
    if (isAuthenticated) void resumeTrackedAiOperation();
  }, [isAuthenticated]);
  if (!operation) return null;
  const percent = operation.totalSteps > 0
    ? Math.min(100, Math.round((operation.completedSteps / operation.totalSteps) * 100))
    : 0;
  const retryable = operation.status === "FAILED" || operation.status === "RECOVERY_REQUIRED";
  const running = ["QUEUED", "RUNNING", "STREAMING"].includes(operation.status);

  return (
    <aside className="fixed bottom-4 right-4 z-[80] w-[calc(100vw-2rem)] max-w-sm rounded-xl border bg-background/95 p-4 shadow-xl backdrop-blur">
      <div className="flex items-start gap-3">
        {running ? <Loader2 className="mt-0.5 h-5 w-5 animate-spin text-primary" /> : null}
        <div className="min-w-0 flex-1 space-y-3">
          <div>
            <div className="font-medium">{operation.currentStep || t("ai.operationRunning")}</div>
            <div className="mt-1 text-xs text-muted-foreground">
              {t("ai.stepsCompleted", { completed: operation.completedSteps, remaining: operation.remainingSteps })}
            </div>
          </div>
          <Progress value={operation.status === "SUCCEEDED" ? 100 : percent} className="h-1.5" />
          <div className="flex items-center justify-between text-xs">
            <span>
              {t("ai.tokensOutput", { count: operation.currentStepOutputTokens, formatted: operation.currentStepOutputTokens.toLocaleString() })}
              {operation.outputTokensEstimated ? t("ai.tokensEstimated") : ""}
            </span>
            <span>{operation.completedSteps}/{operation.totalSteps}</span>
          </div>
          {operation.errorMessage ? <p className="text-xs text-destructive">{t("errors.operationFailed")}</p> : null}
          <div className="flex justify-end gap-2">
            {running ? (
              <Button size="sm" variant="outline" onClick={() => void cancelTrackedAiOperation(operation.id)}>
                <Ban className="mr-1 h-3.5 w-3.5" />{t("ai.cancel")}
              </Button>
            ) : null}
            {retryable ? (
              <Button size="sm" variant="outline" onClick={() => void retryTrackedAiOperation(operation.id)}>
                <RotateCcw className="mr-1 h-3.5 w-3.5" />{t("common.retry")}
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </aside>
  );
}
