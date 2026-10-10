import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Ban, Loader2, RotateCcw, X, Minus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  resumeTrackedAiOperation,
  cancelTrackedAiOperation,
  retryTrackedAiOperation,
  useTrackedAiOperation,
  setTrackedAiUser,
  useTrackedAiVisibility,
  setTrackedAiVisibility,
  refreshTrackedAiOperation,
} from "@/lib/ai-operation-store";
import { useAuth } from "@/contexts/auth-state";
import { localizedErrorMessage, localizedOperationFailure } from "@/lib/error-messages";

export function AiOperationProgressPanel() {
  const { isAuthenticated, user } = useAuth();
  const { t } = useTranslation();
  const operation = useTrackedAiOperation();
  const visibility = useTrackedAiVisibility();
  const [pending, setPending] = useState(false);
  const [actionError, setActionError] = useState("");
  const lock = useRef(false);
  const action = async (work: () => Promise<void>) => {
    if (lock.current) return;
    lock.current = true; setPending(true); setActionError("");
    try { await work(); } catch (error) { setActionError(localizedErrorMessage(error)); }
    finally { lock.current = false; setPending(false); }
  };
  useEffect(() => {
    setTrackedAiUser(isAuthenticated ? user?.id || null : null);
    if (isAuthenticated) void resumeTrackedAiOperation();
  }, [isAuthenticated, user?.id]);
  useEffect(() => setActionError(""), [operation?.id]);
  if (!isAuthenticated || !operation) return null;
  if (visibility !== "open") return <Button className="fixed bottom-2 right-2 z-30 shadow" variant="secondary" onClick={() => setTrackedAiVisibility("open")}>查看 AI 任务</Button>;
  const percent = operation.totalSteps > 0
    ? Math.min(100, Math.round((operation.completedSteps / operation.totalSteps) * 100))
    : 0;
  const retryable = operation.status === "FAILED" || operation.status === "RECOVERY_REQUIRED";
  const running = ["QUEUED", "RUNNING", "STREAMING"].includes(operation.status);

  return (
    <aside aria-label="AI 任务进度" className="fixed bottom-4 right-4 z-30 max-h-[45dvh] overflow-y-auto w-[calc(100vw-2rem)] max-w-sm rounded-xl border bg-background/95 p-4 shadow-xl backdrop-blur">
      <div className="flex justify-end gap-1">
        <Button size="icon" variant="ghost" aria-label="最小化任务" onClick={() => setTrackedAiVisibility("minimized")}><Minus className="h-4 w-4" /></Button>
        <Button size="icon" variant="ghost" aria-label="关闭任务面板" onClick={() => setTrackedAiVisibility("closed")}><X className="h-4 w-4" /></Button>
      </div>
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
          {operation.errorMessage ? <p className="text-xs text-destructive"><span>{localizedOperationFailure(operation.errorCode)}</span> · 阶段：{operation.currentStep || operation.completedSteps}。任务编号：{operation.id}</p> : null}
          {operation.status === "RECOVERY_REQUIRED" && <p className="text-xs">任务结果需要核对。恢复原任务不会绕过积分对账；关闭面板不影响任务记录。</p>}
          {actionError && <p role="alert" className="text-xs text-destructive">{actionError}</p>}
          <div className="flex justify-end gap-2">
            {!running && <Button size="sm" variant="ghost" disabled={pending} onClick={() => void action(() => refreshTrackedAiOperation(operation.id))}>刷新状态</Button>}
            {running ? (
              <Button size="sm" variant="outline" disabled={pending} onClick={() => void action(() => cancelTrackedAiOperation(operation.id))}>
                <Ban className="mr-1 h-3.5 w-3.5" />{t("ai.cancel")}
              </Button>
            ) : null}
            {retryable ? (
              <Button size="sm" variant="outline" disabled={pending} onClick={() => void action(() => retryTrackedAiOperation(operation.id))}>
                <RotateCcw className="mr-1 h-3.5 w-3.5" />{operation.status === "RECOVERY_REQUIRED" ? "恢复原任务" : t("common.retry")}
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </aside>
  );
}
