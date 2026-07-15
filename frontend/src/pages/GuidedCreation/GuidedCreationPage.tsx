import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, ArrowRight, FilePlus2, Loader2, Sparkles, WandSparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { GuidedCreationCandidate } from "@/types";
import { cn } from "@/lib/utils";
import GuidedCreationCandidates from "./GuidedCreationCandidates";
import GuidedCreationCandidateEditor from "./GuidedCreationCandidateEditor";
import GuidedCreationSeedForm from "./GuidedCreationSeedForm";
import { ContextPanel, ContextSheet, MobileSteps, WorkflowRail } from "./GuidedCreationChrome";
import { CompletedState, FailureState, GenerationState, LoadingState } from "./GuidedCreationStates";
import { GUIDED_STEP_TITLES, guidedStatusLabel, guidedStepNumber } from "./guidedCreationUi";
import { useGuidedCreation } from "./useGuidedCreation";

const EMPTY_CANDIDATES: GuidedCreationCandidate[] = [];

export default function GuidedCreationPage() {
  const navigate = useNavigate();
  const guided = useGuidedCreation();
  const { workflow } = guided;
  const stepData = workflow && workflow.currentStep !== "COMPLETED"
    ? workflow.steps[workflow.currentStep]
    : undefined;
  const candidates = stepData?.candidates ?? EMPTY_CANDIDATES;
  const [selectedId, setSelectedId] = useState<string>();
  const [draftCandidate, setDraftCandidate] = useState<GuidedCreationCandidate>();

  useEffect(() => {
    const preferred = candidates.find((item) => item.candidateId === stepData?.recommendedCandidateId)
      ?? candidates[0];
    setSelectedId(preferred?.candidateId);
    setDraftCandidate(preferred ? structuredClone(preferred) : undefined);
  }, [candidates, stepData?.generatedAt, stepData?.recommendedCandidateId, workflow?.currentStep]);

  const selectCandidate = (candidate: GuidedCreationCandidate) => {
    setSelectedId(candidate.candidateId);
    setDraftCandidate(structuredClone(candidate));
  };

  return (
    <div className="min-h-screen bg-[#f5f6f3] text-zinc-950">
      <header className="sticky top-0 z-40 flex h-14 items-center border-b border-zinc-200 bg-white/95 px-3 backdrop-blur md:px-5">
        <Button variant="ghost" size="icon" asChild title="返回小说列表">
          <Link to="/novels"><ArrowLeft className="h-4 w-4" /></Link>
        </Button>
        <div className="ml-2 flex items-center gap-3">
          <div className="flex h-7 w-7 items-center justify-center rounded bg-zinc-950 text-white">
            <WandSparkles className="h-4 w-4" />
          </div>
          <div>
            <h1 className="text-sm font-semibold">引导创作</h1>
            <p className="hidden text-[11px] text-zinc-500 sm:block">{workflow ? workflow.seedIdea : "新草稿"}</p>
          </div>
        </div>
        <div className="ml-auto flex items-center gap-2">
          {workflow ? (
            <span className={cn(
              "hidden items-center gap-1.5 text-xs font-medium sm:flex",
              workflow.status === "FAILED" ? "text-red-700" : "text-zinc-500",
            )}>
              <span className={cn(
                "h-1.5 w-1.5 rounded-full",
                workflow.status === "FAILED" ? "bg-red-600" : guided.shouldPoll ? "animate-pulse bg-emerald-600" : "bg-zinc-400",
              )} />
              {guidedStatusLabel(workflow)}
            </span>
          ) : null}
          <ContextSheet workflow={workflow} totalCharged={guided.totalCharged} remainingCredits={guided.remainingCredits} />
          <Button variant="outline" size="sm" className="hidden sm:flex" onClick={guided.newDraft}>
            <FilePlus2 className="mr-2 h-4 w-4" /> 新草稿
          </Button>
        </div>
      </header>

      <div className="grid min-h-[calc(100vh-3.5rem)] lg:grid-cols-[232px_minmax(0,1fr)_280px]">
        <WorkflowRail runs={guided.runs} workflow={workflow} onOpen={(id) => void guided.open(id)} onNew={guided.newDraft} />

        <main className="min-w-0 bg-[#f8f8f5]">
          <MobileSteps workflow={workflow} />
          <div className="mx-auto w-full max-w-[1080px] px-4 py-6 md:px-8 md:py-10">
            {guided.loading ? <LoadingState /> : null}
            {!guided.loading && !workflow ? <GuidedCreationSeedForm busy={guided.busy} onSubmit={(seed) => void guided.create(seed)} /> : null}
            {!guided.loading && workflow?.status === "COMPLETED" ? (
              <CompletedState workflow={workflow} onOpenWorkbench={() => navigate(`/workbench?id=${workflow.storyId}`)} />
            ) : null}
            {!guided.loading && workflow?.status === "FAILED" ? (
              <FailureState workflow={workflow} busy={guided.busy} onRetry={() => void guided.retry()} />
            ) : null}
            {!guided.loading && workflow && workflow.status !== "COMPLETED" && workflow.status !== "FAILED" ? (
              <section className="animate-in fade-in slide-in-from-bottom-2 duration-300">
                <div className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                  <div>
                    <p className="mb-2 text-xs font-semibold uppercase text-emerald-700">{guidedStepNumber(workflow.currentStep)}</p>
                    <h2 className="text-2xl font-semibold text-zinc-950 md:text-3xl">{GUIDED_STEP_TITLES[workflow.currentStep]}</h2>
                  </div>
                  {!workflow.autoRun ? (
                    <Button variant="outline" className="border-emerald-700 text-emerald-800 hover:bg-emerald-50" disabled={guided.busy || guided.shouldPoll} onClick={() => void guided.startAuto()}>
                      <Sparkles className="mr-2 h-4 w-4" /> 自动完成后续
                    </Button>
                  ) : null}
                </div>

                {guided.shouldPoll || candidates.length === 0 ? (
                  <GenerationState workflow={workflow} />
                ) : (
                  <div className="space-y-8">
                    <GuidedCreationCandidates
                      step={workflow.currentStep}
                      candidates={candidates}
                      recommendedId={stepData?.recommendedCandidateId}
                      selectedId={selectedId}
                      onSelect={selectCandidate}
                    />
                    {draftCandidate ? (
                      <GuidedCreationCandidateEditor step={workflow.currentStep} value={draftCandidate} onChange={setDraftCandidate} />
                    ) : null}
                    <div className="flex flex-col-reverse gap-3 border-t border-zinc-200 pt-5 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        {workflow.currentStep === "WORLD" ? <Button variant="ghost" disabled={guided.busy} onClick={() => void guided.skipWorld()}>跳过世界设定</Button> : null}
                      </div>
                      <Button className="bg-zinc-950 px-6 text-white hover:bg-zinc-800" disabled={!draftCandidate || guided.busy} onClick={() => draftCandidate && void guided.confirm(draftCandidate)}>
                        {guided.busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                        确认并继续 <ArrowRight className="ml-2 h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                )}
              </section>
            ) : null}
          </div>
        </main>

        <ContextPanel workflow={workflow} totalCharged={guided.totalCharged} remainingCredits={guided.remainingCredits} />
      </div>
    </div>
  );
}
