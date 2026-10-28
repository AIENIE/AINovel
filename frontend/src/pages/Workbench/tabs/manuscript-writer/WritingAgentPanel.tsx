import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { assistantPlainText, assistantTextDiff, sameAssistantDocument } from "@/lib/assistant-editor";
import type { AgentCandidate, AgentRun } from "@/lib/api/domains/writing-agent";
import { agentActive, useWritingAgent, type WritingAgentOptions } from "../../hooks/useWritingAgent";
import { isWritingAgentPreparationFailure, localizedOperationFailure } from "@/lib/error-messages";

const record = (value: unknown): Record<string, unknown> => value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
const text = (value: unknown): string => typeof value === "string" ? value : "";
const money = (value: string | number) => Number(value).toFixed(6);
const message = (value: string) => /^(?:WRITING_AGENT_|WORLD_AGENT_|AI_[A-Z_]+$|H2_[A-Z_]+$|AUTHOR_QA_READ_ONLY$)/.test(value) ? localizedOperationFailure(value) : value;
const displayedRunStatus = (run: AgentRun) => run.status === "PAUSED_BUDGET" && isWritingAgentPreparationFailure(text(run.progress.message)) ? "PAUSED_INPUT" : run.status;
function InlineDiff({ before, after }: { before: string; after: string }) {
  const diff = useMemo(() => assistantTextDiff(before, after), [before, after]);
  return <div className="whitespace-pre-wrap break-words leading-7 [overflow-wrap:anywhere]">{diff.map((part, index) => part.kind === "removed" ? <del className="bg-red-100 text-red-900" key={index}>{part.text}</del> : part.kind === "added" ? <ins className="bg-green-100 text-green-900 no-underline" key={index}>{part.text}</ins> : <span key={index}>{part.text}</span>)}</div>;
}
function ActualResult({ value, modelStep = false }: { value: unknown; modelStep?: boolean }) {
  const { t } = useTranslation();const result = record(value);const data = Object.keys(record(result.data)).length ? record(result.data) : Object.keys(record(result.report)).length ? record(result.report) : result;
  const items = Array.isArray(data.results) ? data.results : [], issues = Array.isArray(data.issues) ? data.issues : [];
  const sources = Array.isArray(result.sources) ? result.sources : [];
  const planned = Array.isArray(data.tools) ? data.tools.filter((tool): tool is string => typeof tool === "string") : [];
  const body = text(data.text) || text(data.content) || text(data.snippet), summary = text(data.summary) || text(data.answer) || text(data.message);
  return <div className="min-w-0 space-y-2 text-sm [overflow-wrap:anywhere]">
    {!!text(data.title) && <p className="font-medium">{text(data.title)}</p>}
    {!!text(data.type) && <p className="text-xs text-muted-foreground">{t(`agent.source.${text(data.type)}`, { defaultValue: t(`world2.types.${text(data.type)}`, { defaultValue: text(data.type) === "EVENT" ? t("world5.eventSource") : text(data.type) === "AUTHOR_NOTES" ? t("world4.authorNotes") : text(data.type) }) })}</p>}
    {!!text(data.reason) && <p className="text-xs text-muted-foreground">{text(data.reason).includes(":") ? `${t(`world4.${text(data.reason).split(":")[0]}`, { defaultValue: t("agent.reason") })} · ${text(data.reason).substring(text(data.reason).indexOf(":") + 1)}` : t(`agent.tool.${text(data.reason).replace(/\./g, "_")}`, { defaultValue: t(`world4.${text(data.reason)}`, { defaultValue: text(data.reason) }) })}</p>}
    {!!summary && <p className="whitespace-pre-wrap">{message(summary)}</p>}
    {!!body && <pre className="whitespace-pre-wrap break-words font-sans leading-7">{body}</pre>}
    {typeof data.start === "number" && typeof data.end === "number" && <p className="text-xs text-muted-foreground">{t("agent.range", { start: data.start, end: data.end })}</p>}
    {data.complete === false && <p className="text-amber-700">{t("agent.partial")}</p>}
    {!!planned.length && <p className="text-xs text-muted-foreground">{t("agent.plannedTools")}：{planned.map(tool => t(`agent.tool.${tool.replace(/\./g, "_")}`, { defaultValue: tool })).join("、")}</p>}
    {!!data.error && <p className="text-amber-700">{localizedOperationFailure(text(data.error))}</p>}
    {!!items.length && <p className="text-xs font-medium text-muted-foreground">{t("agent.searchMatches")}</p>}
    {items.map((item, index) => <section className="min-w-0 rounded border p-2" key={index}><ActualResult value={item} /></section>)}
    {!!sources.length && <section className="min-w-0 space-y-2"><p className="text-xs font-medium text-muted-foreground">{t(modelStep ? "agent.modelSources" : "agent.toolFragments")}</p>{sources.map((source, index) => <section className="min-w-0 rounded border p-2" key={index}><ActualResult value={source} /></section>)}</section>}
    {issues.map((issue, index) => { const item = record(issue);return <section className="min-w-0 space-y-1 rounded border p-2" key={index}>
      <p className="font-medium">{t("agent.evidence")}</p><p className="whitespace-pre-wrap">{text(item.quote) || text(item.evidence)}</p>
      <p>{t("agent.reason")}：{text(item.impact) || text(item.why_it_matters)}</p><p>{t("agent.fix")}：{text(item.direction) || text(item.minimal_fix)}</p>
      {(item.certainty === "UNCERTAIN" || item.kind === "OBSERVATION") && <p className="text-amber-700">{t("agent.observation")}</p>}
    </section>; })}
    {!summary && !body && !items.length && !issues.length && !sources.length && <p className="text-muted-foreground">{t("agent.noResult")}</p>}
    <details className="min-w-0 text-xs text-muted-foreground"><summary>{t("agent.technical")}</summary><pre className="whitespace-pre-wrap break-all">{JSON.stringify(value, null, 2)}</pre></details>
  </div>;
}
function CandidateReview({ run, candidate, dirty, disabled, onAccept }: { run: AgentRun; candidate: AgentCandidate; dirty: boolean; disabled: boolean; onAccept: () => void }) {
  const { t } = useTranslation();const [view, setView] = useState<"comparison" | "diff">("comparison");
  const before = assistantPlainText(run.sourceHtml), after = assistantPlainText(candidate.html);
  return <section aria-label={t("agent.after")} className="min-w-0 space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2"><p className="font-medium">{t("agent.candidate", { number: candidate.sequence, han: candidate.actualHan })}</p>
      <div role="group" aria-label={t("agent.compare")} className="flex flex-wrap gap-2"><Button size="sm" variant={view === "comparison" ? "secondary" : "outline"} aria-pressed={view === "comparison"} onClick={() => setView("comparison")}>{t("agent.compare")}</Button><Button size="sm" variant={view === "diff" ? "secondary" : "outline"} aria-pressed={view === "diff"} onClick={() => setView("diff")}>{t("agent.diff")}</Button></div>
    </div>
    {view === "comparison" ? <div className="grid min-w-0 gap-3 md:grid-cols-2">{[{ label: t("agent.before"), text: before }, { label: t("agent.after"), text: after }].map(column => <section className="min-w-0 rounded border p-3" key={column.label}><h4 className="mb-2 text-xs font-medium text-muted-foreground">{column.label}</h4><p className="whitespace-pre-wrap break-words leading-7 [overflow-wrap:anywhere]">{column.text || t("worlds.emptyField")}</p></section>)}</div> : <InlineDiff before={before} after={after} />}
    {dirty && <p role="status" className="text-sm text-amber-700">{t("agent.saveFirst")}</p>}
    <Button size="sm" disabled={disabled || dirty || run.intent === "AUTHOR_QA"} onClick={onAccept}>{t("agent.adopt")}</Button>
    {!!candidate.sources.length && <details className="min-w-0"><summary>{t("agent.sources")}</summary><div className="mt-2 space-y-2">{candidate.sources.map((source, index) => <ActualResult key={index} value={source} />)}</div></details>}
  </section>;
}
export type WritingAgentPanelProps = Omit<WritingAgentOptions, "open">;
export function WritingAgentPanel(props: WritingAgentPanelProps) {
  const { t } = useTranslation();const [open, setOpen] = useState(false);
  const agent = useWritingAgent({ ...props, open });const run = agent.selectedRun;
  const [candidateId, setCandidateId] = useState("");
  const [answer, setAnswer] = useState("");
  useEffect(() => { setCandidateId("");setAnswer(""); }, [run?.id]);
  const candidate = run?.candidates.find(item => item.id === candidateId) || run?.candidates.at(-1);
  const modelSteps = run?.steps.filter(step => step.kind === "MODEL") || [];
  const savedModelResults = modelSteps.filter(step => ["COMPLETED", "SUCCEEDED"].includes(step.status) && step.result != null).length;
  const active = !!agent.history.data?.some(item => agentActive(item.status));
  const locked = agent.working || props.busy || props.locked;
  const startDisabled = locked || active || !!agent.pending || !props.manuscript?.currentBranchId || !props.sceneId || !agent.input.trim() || agent.intent === "EDIT" && props.dirty;
  const canAccept = !!run && !agentActive(run.status) && run.status !== "ACCEPTED" && !locked && !agent.pendingAdoption && props.manuscript?.version === run.sourceVersion && sameAssistantDocument(props.content, run.sourceHtml);
  const status = (value: string) => t(`agent.status.${value}`, { defaultValue: t("agent.unknown") });
  const toolLabel = (value: string) => t(`agent.tool.${value.replace(/\./g, "_")}`, { defaultValue: value });
  return <Dialog open={open} onOpenChange={setOpen}>
    <DialogTrigger asChild><Button size="sm" variant="outline" disabled={!props.manuscript?.id || !props.sceneId}>{t("agent.title")}</Button></DialogTrigger>
    <DialogContent className="flex max-h-[92dvh] w-[96vw] max-w-5xl flex-col overflow-hidden">
      <DialogHeader><DialogTitle>{t("agent.title")}</DialogTitle><DialogDescription>{t("agent.help")}<br />{t("agent.transportHelp")}</DialogDescription></DialogHeader>
      <div className="min-h-0 min-w-0 space-y-5 overflow-y-auto pr-1 [overflow-wrap:anywhere]">
        <section className="min-w-0 space-y-3">
          <label className="block space-y-1 text-sm"><span>{t("agent.instruction")}</span><textarea className="min-h-28 w-full min-w-0 rounded border bg-background p-3" value={agent.input} onChange={e => agent.updateInput(e.target.value)} maxLength={8000} placeholder={t("agent.placeholder")} /></label>
          <div className="grid min-w-0 gap-3 sm:grid-cols-2">
            <label className="space-y-1 text-sm">{t("agent.intent")}<select className="block w-full min-w-0 rounded border bg-background p-2" value={agent.intent} onChange={e => agent.setIntent(e.target.value as "EDIT" | "AUTHOR_QA")}><option value="EDIT">{t("agent.edit")}</option><option value="AUTHOR_QA">{t("agent.qa")}</option></select></label>
            <label className="space-y-1 text-sm">{t("agent.mode")}<select className="block w-full min-w-0 rounded border bg-background p-2" value={agent.mode} onChange={e => agent.setMode(e.target.value as "fast" | "crafted")}><option value="fast">{t("agent.fast")}</option><option value="crafted">{t("agent.crafted")}</option></select></label>
          </div>
          {agent.intent === "EDIT" ? <div className="flex flex-wrap gap-3 text-sm"><label className="flex items-start gap-2"><input type="checkbox" checked={agent.languageCheck} onChange={e => agent.setLanguageCheck(e.target.checked)} />{t("agent.language")}</label><label className="flex items-start gap-2"><input type="checkbox" checked={agent.plotCheck} onChange={e => agent.setPlotCheck(e.target.checked)} />{t("agent.plot")}</label></div> : <p className="text-sm text-muted-foreground">{t("agent.readOnly")}</p>}
          {props.target?.supported && <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={agent.useSelection} onChange={e => agent.setUseSelection(e.target.checked)} />{t("agent.selection")}</label>}
          <p className="text-xs text-muted-foreground">{t("agent.wholeScene")}</p>
          {props.dirty && <p className="text-sm text-amber-700">{t(agent.intent === "EDIT" ? "agent.saveRequired" : "agent.unsaved")}</p>}
          <fieldset className="min-w-0 space-y-2 rounded border p-3"><legend className="px-1 text-sm font-medium">{t("agent.budget")}</legend>
            <div className="grid min-w-0 gap-3 sm:grid-cols-3">{[{ label: t("agent.cny"), value: agent.maxCost, set: agent.setMaxCost, step: "0.01", min: "0.000001" }, { label: t("agent.credits"), value: agent.maxCredits, set: agent.setMaxCredits, step: "1", min: "1" }].map(field => <label key={field.label} className="block space-y-1 text-xs">{field.label}<input className="block w-full min-w-0 rounded border bg-background p-2 text-sm" type="number" min={field.min} step={field.step} value={field.value} onChange={e => field.set(e.target.value)} /></label>)}
              <label className="block space-y-1 text-xs">{t("agent.calls")}<input className="block w-full min-w-0 rounded border bg-background p-2 text-sm" type="number" min="1" max="24" step="1" value={agent.maxCalls} onChange={e => agent.setMaxCalls(Number(e.target.value))} /></label>
            </div><p className="text-xs leading-6 text-muted-foreground">{t("agent.budgetHelp")}</p>
          </fieldset>
          <div className="flex flex-wrap gap-2"><Button className="h-auto max-w-full whitespace-normal py-2" size="sm" variant="outline" disabled={startDisabled} onClick={() => void agent.prepare()}>{t("agent.preview")}</Button><Button className="h-auto max-w-full whitespace-normal py-2" size="sm" disabled={startDisabled || !agent.preview || !!agent.preview.issues.length} onClick={() => void agent.start()}>{t("agent.start")}</Button></div>
          {agent.preview && <section aria-label={t("agent.preview")} className="min-w-0 space-y-2 rounded border p-3 text-sm"><p className="font-medium">{agent.preview.binding ? `${agent.preview.binding.name} · v${agent.preview.binding.sequenceNumber} · ${t(`world4.${agent.preview.binding.sourceType}`)}` : t("agent.noWorld")}</p><p className="text-muted-foreground">{t("agent.previewHelp")}</p>
            {agent.preview.h2 && <p>{t("agent.h2")}</p>}<p>{t("agent.tools")}：{agent.preview.tools.map(toolLabel).join("、")}</p>
            {agent.preview.issues.map(issue => <p role="alert" className="text-amber-700" key={issue}>{t(`world4.${issue.split(":")[0]}`, { defaultValue: t("agent.issue") })}</p>)}
            {!!agent.preview.firstInput.length && <details className="min-w-0"><summary className="cursor-pointer">{t("agent.firstInput")}</summary><p className="my-2 text-xs text-muted-foreground">{t("agent.firstInputHelp")}</p>{agent.preview.firstInput.map((entry, index) => <section className="min-w-0 space-y-1 border-t py-2" key={index}><p className="text-xs font-medium">{t(`agent.role.${entry.role}`, { defaultValue: t("agent.technical") })}</p><pre className="whitespace-pre-wrap break-words font-sans text-xs leading-6">{entry.content}</pre></section>)}</details>}
            <details className="text-xs text-muted-foreground"><summary>{t("agent.technical")}</summary><p className="break-all">{agent.preview.protocol}<br />{agent.preview.contextHash}</p></details>
          </section>}
          {agent.pending && <section className="space-y-2 rounded border p-3 text-sm"><p>{t("agent.pendingSubmit")}</p><Button size="sm" variant="outline" disabled={locked} onClick={() => void agent.reconcileSubmit()}>{t("agent.checkSubmit")}</Button></section>}
          {agent.pendingAdoption && <section className="space-y-2 rounded border p-3 text-sm"><p>{t("agent.pendingAccept")}</p><Button size="sm" variant="outline" disabled={locked} onClick={() => void agent.reconcileAccept()}>{t("agent.checkAccept")}</Button></section>}
          {!!agent.error && <p role="alert" className="text-sm text-destructive">{agent.error}</p>}{!!agent.notice && <p role="status" className="text-sm">{agent.notice}</p>}
        </section>
        <section className="min-w-0 space-y-3 border-t pt-4"><div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-medium">{t("agent.history")}</h3><Button variant="ghost" size="sm" disabled={agent.history.isFetching} onClick={() => void agent.refresh()}>{t("agent.refresh")}</Button></div>
          {agent.history.data?.length ? <select aria-label={t("agent.history")} className="block w-full min-w-0 rounded border bg-background p-2 text-sm" value={run?.id || ""} onChange={e => agent.setSelectedRunId(e.target.value)}>{agent.history.data.map(item => <option value={item.id} key={item.id}>{new Date(item.createdAt).toLocaleString()} · {status(displayedRunStatus(item))} · {item.id.slice(0, 8)}</option>)}</select> : <p className="text-sm text-muted-foreground">{t("agent.empty")}</p>}
          {run && <div className="min-w-0 space-y-4"><div className="space-y-2 text-sm"><p className="font-medium">{status(displayedRunStatus(run))}</p>{!!text(run.progress.message) && <p className="whitespace-pre-wrap">{message(text(run.progress.message))}</p>}{!!text(run.progress.answer) && <p className="whitespace-pre-wrap">{message(text(run.progress.answer))}</p>}
            <p className="text-xs text-muted-foreground">{t("agent.frozenWorld")}：{run.worldBinding ? `${run.worldBinding.name} · v${run.worldBinding.sequenceNumber} · ${t(`world4.${run.worldBinding.sourceType}`)}` : t("agent.noWorld")}</p>
            <p>{t("agent.cost", { settled: money(run.settledCny), reserved: money(run.reservedCny), credits: run.projectCredits })}</p><p className="text-xs text-muted-foreground">{t("agent.limit", { cny: run.maxCostCny, credits: run.maxProjectCredits })}</p>
            <p className="text-xs text-muted-foreground">{t("agent.usedSteps", { steps: modelSteps.length, results: savedModelResults, candidates: run.candidates.length })}</p>
            {run.status === "WAITING_AUTHOR" && <label className="block space-y-1">{t("agent.authorAnswer")}<textarea className="block min-h-24 w-full min-w-0 rounded border bg-background p-2" value={answer} onChange={e => setAnswer(e.target.value)} maxLength={8000} /></label>}
            {displayedRunStatus(run) === "PAUSED_BUDGET" && <p className="text-amber-700">{t("agent.budgetPaused")}</p>}
            <div className="flex flex-wrap gap-2">{agentActive(run.status) && <Button size="sm" variant="outline" disabled={locked || run.status === "PAUSE_REQUESTED"} onClick={() => void agent.control(run, "pause")}>{t("agent.pause")}</Button>}{["PAUSED_USER", "WAITING_AUTHOR", "RECOVERY_REQUIRED"].includes(run.status) && <Button size="sm" variant="outline" disabled={locked || run.status === "WAITING_AUTHOR" && !answer.trim()} onClick={() => void agent.control(run, "resume", run.status === "WAITING_AUTHOR" ? answer.trim() : undefined)}>{t("agent.resume")}</Button>}{!["ACCEPTED", "CANCELLED"].includes(run.status) && <Button size="sm" variant="outline" disabled={locked} onClick={() => void agent.control(run, "cancel")}>{t("agent.cancel")}</Button>}</div><p className="text-xs text-muted-foreground">{t("agent.pauseHelp")}</p>
          </div>
            {!!run.candidates.length && <label className="block space-y-1 text-sm">{t("agent.candidates")}<select className="block w-full min-w-0 rounded border bg-background p-2" value={candidate?.id || ""} onChange={e => setCandidateId(e.target.value)}>{run.candidates.map(item => <option value={item.id} key={item.id}>{t("agent.candidate", { number: item.sequence, han: item.actualHan })}</option>)}</select></label>}
            {candidate && <CandidateReview run={run} candidate={candidate} dirty={props.dirty} disabled={!canAccept} onAccept={() => void agent.accept(run, candidate)} />}
            {candidate && !canAccept && !props.dirty && !locked && run.status !== "ACCEPTED" && <p className="text-sm text-amber-700">{t("agent.stale")}</p>}
            {!!run.steps.length && <section className="min-w-0 space-y-2"><h4 className="text-sm font-medium">{t("agent.steps")}</h4>{run.steps.map(step => <details className="min-w-0 rounded border p-3 text-sm" key={step.sequence}><summary className="cursor-pointer break-words">{step.sequence} · {step.kind === "MODEL" ? t("agent.stepModel") : toolLabel(step.feature || step.kind) || t("agent.stepTool")} · {step.status === "COMPLETED" ? t("agent.status.SUCCEEDED") : status(step.status)}</summary><div className="mt-2"><ActualResult value={step.result} modelStep={step.kind === "MODEL"} /></div></details>)}</section>}
          </div>}
        </section>
      </div>
    </DialogContent>
  </Dialog>;
}
