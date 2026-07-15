import { Check, CheckCircle2, Circle, Menu, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { CreationWorkflow } from "@/types";
import { cn } from "@/lib/utils";
import { GUIDED_STEPS, guidedStatusLabel, selectedSummary } from "./guidedCreationUi";

type ContextProps = {
  workflow: CreationWorkflow | null;
  totalCharged: number;
  remainingCredits: number | null;
};

export function WorkflowRail({
  runs, workflow, onOpen, onNew,
}: { runs: CreationWorkflow[]; workflow: CreationWorkflow | null; onOpen: (id: string) => void; onNew: () => void }) {
  const activeIndex = workflow ? GUIDED_STEPS.findIndex((step) => step.key === workflow.currentStep) : -1;
  return (
    <aside className="hidden border-r border-zinc-800 bg-zinc-950 text-zinc-100 lg:flex lg:flex-col">
      <div className="p-5">
        <Button className="w-full bg-emerald-700 text-white hover:bg-emerald-600" onClick={onNew}>
          <Plus className="mr-2 h-4 w-4" /> 新草稿
        </Button>
      </div>
      <nav className="border-y border-zinc-800 px-5 py-6">
        <p className="mb-4 text-[11px] font-semibold uppercase text-zinc-500">创作步骤</p>
        <ol className="space-y-1">
          {GUIDED_STEPS.map((step, index) => {
            const complete = Boolean(workflow?.steps[step.key]?.selected || workflow?.steps[step.key]?.skipped || activeIndex > index || workflow?.status === "COMPLETED");
            const current = workflow?.currentStep === step.key;
            return (
              <li key={step.key} className={cn("flex items-center gap-3 rounded px-2 py-2.5 text-sm", current ? "bg-zinc-900 text-white" : "text-zinc-500")}>
                {complete ? <Check className="h-4 w-4 text-emerald-500" /> : current ? <Circle className="h-4 w-4 fill-emerald-500 text-emerald-500" /> : <span className="w-4 text-[10px]">{step.number}</span>}
                <span>{step.label}</span>
              </li>
            );
          })}
        </ol>
      </nav>
      <div className="min-h-0 flex-1 overflow-y-auto p-5">
        <p className="mb-3 text-[11px] font-semibold uppercase text-zinc-500">最近草稿</p>
        <div className="space-y-1">
          {runs.slice(0, 8).map((run) => (
            <button key={run.id} type="button" onClick={() => onOpen(run.id)} className={cn("w-full rounded px-2 py-2.5 text-left transition-colors hover:bg-zinc-900", workflow?.id === run.id && "bg-zinc-900")}>
              <p className="line-clamp-2 text-xs leading-5 text-zinc-300">{run.seedIdea}</p>
              <p className="mt-1 text-[10px] text-zinc-600">{guidedStatusLabel(run)}</p>
            </button>
          ))}
        </div>
      </div>
    </aside>
  );
}

export function ContextPanel(props: ContextProps) {
  return (
    <aside className="hidden border-l border-zinc-200 bg-white p-6 lg:block">
      <ContextContent {...props} />
    </aside>
  );
}

export function ContextSheet(props: ContextProps) {
  return (
    <Sheet>
      <SheetTrigger asChild><Button variant="ghost" size="icon" className="lg:hidden" title="创作上下文"><Menu className="h-4 w-4" /></Button></SheetTrigger>
      <SheetContent side="right" className="w-[86vw] max-w-sm bg-white">
        <SheetHeader><SheetTitle>创作上下文</SheetTitle></SheetHeader>
        <div className="mt-6"><ContextContent {...props} /></div>
      </SheetContent>
    </Sheet>
  );
}

export function MobileSteps({ workflow }: { workflow: CreationWorkflow | null }) {
  const active = workflow ? Math.max(0, GUIDED_STEPS.findIndex((item) => item.key === workflow.currentStep)) : 0;
  return (
    <div className="flex h-12 items-center border-b border-zinc-200 bg-white px-4 lg:hidden">
      {GUIDED_STEPS.map((step, index) => (
        <div key={step.key} className="flex min-w-0 flex-1 items-center">
          <span className={cn("flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold", index <= active ? "bg-zinc-950 text-white" : "bg-zinc-100 text-zinc-400")}>{index + 1}</span>
          {index < GUIDED_STEPS.length - 1 ? <span className={cn("mx-1 h-px flex-1", index < active ? "bg-zinc-900" : "bg-zinc-200")} /> : null}
        </div>
      ))}
    </div>
  );
}

function ContextContent({ workflow, totalCharged, remainingCredits }: ContextProps) {
  if (!workflow) return <p className="text-sm text-zinc-500">尚未建立草稿</p>;
  const selected = GUIDED_STEPS.map((step) => ({ step, data: workflow.steps[step.key] })).filter((item) => item.data?.selected || item.data?.skipped);
  return (
    <div className="space-y-7">
      <div>
        <p className="mb-3 text-[11px] font-semibold uppercase text-zinc-400">创作起点</p>
        <p className="text-sm leading-6 text-zinc-700">{workflow.seedIdea}</p>
        <div className="mt-3 flex flex-wrap gap-2 text-xs text-zinc-500">
          <span>{workflow.genre || "未定类型"}</span><span>·</span><span>{workflow.tone || "未定基调"}</span><span>·</span><span>{workflow.targetChapterCount} 章</span>
        </div>
      </div>
      <div className="border-y border-zinc-200 py-5">
        <p className="mb-4 text-[11px] font-semibold uppercase text-zinc-400">已确认</p>
        <div className="space-y-4">
          {selected.length ? selected.map(({ step, data }) => (
            <div key={step.key} className="flex gap-3">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" />
              <div><p className="text-xs font-medium text-zinc-900">{step.label}</p><p className="mt-1 line-clamp-2 text-xs leading-5 text-zinc-500">{data?.skipped ? "已跳过" : selectedSummary(data?.selected)}</p></div>
            </div>
          )) : <p className="text-xs text-zinc-400">等待首次确认</p>}
        </div>
      </div>
      <div>
        <p className="mb-3 text-[11px] font-semibold uppercase text-zinc-400">本草稿用量</p>
        <div className="space-y-2 text-xs text-zinc-600">
          <div className="flex justify-between"><span>已扣项目积分</span><strong className="font-semibold tabular-nums text-zinc-900">{totalCharged}</strong></div>
          <div className="flex justify-between"><span>项目积分余额</span><strong className="font-semibold tabular-nums text-zinc-900">{remainingCredits ?? "-"}</strong></div>
        </div>
      </div>
    </div>
  );
}
