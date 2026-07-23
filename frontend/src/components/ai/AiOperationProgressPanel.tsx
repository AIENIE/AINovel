import { useEffect } from "react";
import { Loader2, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  resumeTrackedAiOperation,
  retryTrackedAiOperation,
  useTrackedAiOperation,
} from "@/lib/ai-operation-store";
import { useAuth } from "@/contexts/AuthContext";

export function AiOperationProgressPanel() {
  const { isAuthenticated } = useAuth();
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
            <div className="font-medium">{operation.currentStep || "AI 任务处理中"}</div>
            <div className="mt-1 text-xs text-muted-foreground">
              已完成 {operation.completedSteps} 步 · 剩余 {operation.remainingSteps} 步
            </div>
          </div>
          <Progress value={operation.status === "SUCCEEDED" ? 100 : percent} className="h-1.5" />
          <div className="flex items-center justify-between text-xs">
            <span>当前步骤已输出 {operation.currentStepOutputTokens.toLocaleString()} token{operation.outputTokensEstimated ? "（估算）" : ""}</span>
            <span>{operation.completedSteps}/{operation.totalSteps}</span>
          </div>
          {operation.errorMessage ? <p className="text-xs text-destructive">{operation.errorMessage}</p> : null}
          <div className="flex justify-end gap-2">
            {retryable ? (
              <Button size="sm" variant="outline" onClick={() => void retryTrackedAiOperation(operation.id)}>
                <RotateCcw className="mr-1 h-3.5 w-3.5" />重试
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </aside>
  );
}
