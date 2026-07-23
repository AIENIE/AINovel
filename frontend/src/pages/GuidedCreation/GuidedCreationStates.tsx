import { Link } from "react-router-dom";
import { AlertCircle, BookOpen, Check, ChevronRight, Coins, Loader2, RefreshCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { CreationWorkflow } from "@/types";

export function GenerationState({ workflow }: { workflow: CreationWorkflow }) {
  const progress = workflow.activeJob?.progress ?? (workflow.autoRun ? 12 : 0);
  const stepLabel = workflow.activeJob?.operation === "OUTLINE_DEVELOP" ? "发展大纲方向"
    : workflow.activeJob?.operation === "OUTLINE_REWRITE" ? "重写大纲方向"
      : workflow.activeJob?.operation === "OUTLINE_EXPAND" ? "展开完整章节大纲"
        : workflow.currentStep === "PREMISE" ? "生成故事方向"
          : workflow.currentStep === "WORLD" ? "生成世界设定"
            : workflow.currentStep === "CHARACTERS" ? "生成角色阵容"
              : workflow.currentStep === "OUTLINE" ? "生成大纲方向"
                : "完成创作初始化";
  const baseCompleted = workflow.currentStep === "WORLD" ? 1
    : workflow.currentStep === "CHARACTERS" ? 2
      : workflow.currentStep === "OUTLINE" ? (workflow.activeJob?.operation === "OUTLINE_EXPAND" ? 4 : 3)
        : workflow.currentStep === "COMPLETED" ? 5 : 0;
  const remaining = Math.max(0, 5 - baseCompleted);
  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center border-y border-zinc-200 py-16 text-center">
      <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-800"><Loader2 className="h-5 w-5 animate-spin" /></div>
      <h3 className="text-base font-semibold">当前步骤：{stepLabel}</h3>
      <div className="mt-6 w-full max-w-xs"><Progress value={progress} className="h-1.5" /></div>
      <div className="mt-3 space-y-1 text-xs text-zinc-500">
        <p>{workflow.activeJob?.status === "CALLING_AI" ? "AI 生成中" : "任务已进入后台队列"}</p>
        <p>已完成 {baseCompleted} 步 · 剩余 {remaining} 步</p>
        <p>当前步骤已输出 {(workflow.activeJob?.outputTokens ?? 0).toLocaleString()} token{workflow.activeJob?.outputTokensEstimated ? "（估算）" : ""}</p>
      </div>
    </div>
  );
}

export function FailureState({ workflow, busy, onRetry }: { workflow: CreationWorkflow; busy: boolean; onRetry: () => void }) {
  const insufficient = workflow.errorMessage?.includes("积分不足");
  return (
    <div className="mx-auto flex min-h-[520px] max-w-xl flex-col items-center justify-center text-center">
      <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-700"><AlertCircle className="h-5 w-5" /></div>
      <h2 className="text-xl font-semibold">{insufficient ? "项目积分不足" : "本步骤未完成"}</h2>
      <p className="mt-3 max-w-md text-sm leading-6 text-zinc-600">{workflow.errorMessage || "生成失败，请稍后重试。"}</p>
      <div className="mt-7 flex gap-3">
        {insufficient ? <Button variant="outline" asChild><Link to="/profile"><Coins className="mr-2 h-4 w-4" /> 兑换积分</Link></Button> : null}
        <Button className="bg-zinc-950 text-white hover:bg-zinc-800" disabled={busy} onClick={onRetry}>{busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCcw className="mr-2 h-4 w-4" />}重试</Button>
      </div>
    </div>
  );
}

export function CompletedState({ workflow, onOpenWorkbench }: { workflow: CreationWorkflow; onOpenWorkbench: () => void }) {
  return (
    <div className="mx-auto flex min-h-[560px] max-w-xl flex-col items-center justify-center text-center animate-in fade-in zoom-in-95 duration-300">
      <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-700 text-white"><Check className="h-6 w-6" /></div>
      <p className="mb-2 text-xs font-semibold uppercase text-emerald-700">Step 04 complete</p>
      <h2 className="text-2xl font-semibold md:text-3xl">创作准备完成</h2>
      <p className="mt-3 text-sm text-zinc-500">故事、角色和章节大纲已进入工作台。</p>
      <div className="mt-8 flex gap-3">
        <Button variant="outline" asChild><Link to="/novels"><BookOpen className="mr-2 h-4 w-4" /> 小说列表</Link></Button>
        <Button className="bg-zinc-950 text-white hover:bg-zinc-800" onClick={onOpenWorkbench}>进入工作台 <ChevronRight className="ml-2 h-4 w-4" /></Button>
      </div>
      <p className="mt-6 text-xs text-zinc-400">{workflow.targetChapterCount} 章 · {workflow.autoRun ? "自动完成" : "逐步确认"}</p>
    </div>
  );
}

export function LoadingState() {
  return <div className="flex min-h-[520px] items-center justify-center"><Loader2 className="h-5 w-5 animate-spin text-emerald-700" /></div>;
}
