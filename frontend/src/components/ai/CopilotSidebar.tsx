import { WorldSources } from './WorldContextPreview';
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Bot, Loader2, Send } from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useWritingAssistantContext } from "./writing-assistant-context";
import { AssistantProposalCard } from "./AssistantProposalCard";
import type { AssistantIntent } from "@/lib/api/domains/writing-assistant";
import { assistantWarning, assistantSourceReason } from "@/lib/assistant-messages";

interface CopilotSidebarProps { context?: unknown; className?: string }
export default function CopilotSidebar({ className }: CopilotSidebarProps) {
  const assistant = useWritingAssistantContext();
  const { t } = useTranslation();
  const location = useLocation();
  const [expanded, setExpanded] = useState<string | null>(null);
  useEffect(() => setExpanded(null), [assistant?.identity]);
  if (!assistant?.id || !assistant.sceneId) return <p className="p-4 text-sm">{t("assistant.chooseScene")}</p>;
  const latestProposal = [...assistant.turns].reverse().find(turn => turn.proposalId)?.proposalId;
  return <section aria-label={t("assistant.title")} className={cn("flex h-full min-h-0 min-w-0 flex-col bg-card", className)}>
    <header className="shrink-0 border-b p-3 space-y-2">
      <div className="flex items-center gap-2 font-semibold"><Bot className="h-5 w-5" />{t("assistant.title")}<Button variant="ghost" size="sm" className="ml-auto" onClick={() => void assistant.refresh()}>{t("assistant.refresh")}</Button></div>
      <p className="break-words text-xs text-muted-foreground">{assistant.sceneTitle} · {t("assistant.version", { version: assistant.manuscriptVersion })}</p>
      <p className="text-xs">{assistant.session.data?.h2Enabled ? t("assistant.h2Note") : t("assistant.draftNote")}</p>
    </header>
    <div className="min-h-0 flex-1 overflow-y-auto p-3 space-y-4 break-words [overflow-wrap:anywhere]" role="log" aria-live="polite">
      {assistant.hasOlder && <Button variant="outline" size="sm" disabled={assistant.loadingOlder} onClick={() => void assistant.loadOlder()}>{t("assistant.older")}</Button>}
      {assistant.session.isPending && <p>{t("assistant.loading")}</p>}
      {!assistant.session.isPending && !assistant.turns.length && <p className="text-sm text-muted-foreground">{t("assistant.welcome")}</p>}
      {assistant.turns.map(turn => <article key={turn.id} className="space-y-2 text-sm">
        <p className="rounded-lg bg-secondary p-3 whitespace-pre-wrap">{turn.instruction}</p>
        <div className="space-y-2 rounded-lg border p-3">
          <p className="text-xs text-muted-foreground">{t(`assistant.state.${turn.resultKind || turn.status}`, { defaultValue: turn.status })}</p>
          {turn.answer && <p className="whitespace-pre-wrap leading-relaxed">{turn.answer}</p>}
          {turn.worldContext&&<details><summary className="cursor-pointer text-xs">{t('world4.frozenRange')}</summary><WorldSources selection={turn.worldContext}/></details>}
          {turn.steps?.map(step=><details key={step.step}><summary className="cursor-pointer text-xs">{t('world4.actual')} · {step.step==='plan'?1:2}</summary>{step.sources.map(source=><details key={source.id} className="my-2 rounded border p-2"><summary>{source.title}</summary><p className="whitespace-pre-wrap break-words">{source.content}</p></details>)}</details>)}
          {turn.warnings.map((warning, index) => <p key={index} className="text-xs text-amber-700">{assistantWarning(warning)}</p>)}
          {!!turn.sources.length && <details><summary className="cursor-pointer text-xs">{t("assistant.sources", { count: turn.sources.length })}</summary>
            <div className="mt-2 space-y-3">{turn.sources.map((source, index) => {
              const params = new URLSearchParams(location.search); if (source.sceneId) params.set("sceneId", source.sceneId);
              return <details className="rounded border p-2" key={`${source.id}:${index}`}><summary className="cursor-pointer">{source.title}</summary>
                <p className="whitespace-pre-wrap leading-relaxed mt-2">{source.content}</p>
                {source.truncated && <p className="text-xs text-amber-700">{t("assistant.sourceTruncated")}</p>}{source.reason && <p className="text-xs">{assistantSourceReason(source.reason)}</p>}
                {source.sceneId && <Link className="text-primary underline text-xs" to={`${location.pathname}?${params}`}>{t("assistant.openSource")}</Link>}
              </details>;
            })}</div></details>}
          <p className="text-xs text-muted-foreground">{t(["FAILED", "RECOVERY_REQUIRED", "CANCELLED"].includes(turn.status) ? "assistant.confirmedUsage" : "assistant.usage", { input: turn.usage.inputTokens, output: turn.usage.outputTokens, cost: turn.usage.cost, calls: turn.usage.modelCalls })}</p>
          {turn.operationId && ["FAILED", "RECOVERY_REQUIRED"].includes(turn.status) && <Button size="sm" variant="outline" disabled={assistant.working} onClick={() => void assistant.recover(turn.operationId!)}>{t("assistant.recover")}</Button>}
          {turn.proposalId && <>
            <Button size="sm" variant="outline" onClick={() => setExpanded(expanded === turn.proposalId ? "" : turn.proposalId)}>{t("assistant.viewProposal")}</Button>
            {(expanded === turn.proposalId || (expanded === null && turn.proposalId === latestProposal)) && <AssistantProposalCard key={`${assistant.identity}:${turn.proposalId}`} proposalId={turn.proposalId} />}
          </>}
        </div>
      </article>)}
    </div>
    <form className="shrink-0 border-t p-3 space-y-2" onSubmit={event => { event.preventDefault(); void assistant.submit(); }}>
      {assistant.notice && <p role="status" className="text-xs break-words">{assistant.notice}</p>}
      {assistant.error && <p role="alert" className="text-xs text-destructive break-words">{assistant.error}</p>}
      {assistant.parent && <div className="text-xs rounded border p-2">{t("assistant.refining")}<Button type="button" size="sm" variant="ghost" onClick={assistant.clearParent}>{t("assistant.clear")}</Button></div>}
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <select aria-label={t("assistant.intent")} className="min-w-0 rounded border bg-background p-1" value={assistant.intent} onChange={event => assistant.setIntent(event.target.value as AssistantIntent)}>
          <option value="AUTO">{t("assistant.autoIntent")}</option><option value="EDIT">{t("assistant.editIntent")}</option><option value="AUTHOR_QA">{t("assistant.qaIntent")}</option>
        </select>
        <span>{assistant.parent ? t("assistant.parentScope") : assistant.target?.selection && !(assistant.intent === "AUTHOR_QA" && !assistant.target.supported) ? t(assistant.target.text ? "assistant.selectionScope" : "assistant.cursorScope") : t("assistant.sceneScope")}</span>
        {assistant.target && <Button size="sm" type="button" variant="ghost" onClick={() => assistant.capture(null)}>{t("assistant.wholeScene")}</Button>}
      </div>
      {assistant.target && !assistant.target.supported && assistant.intent === "AUTHOR_QA" && <p className="text-xs text-muted-foreground">{t("assistant.qaFixedScope")}</p>}
      {assistant.target?.text && <details className="text-xs"><summary className="cursor-pointer">{t("assistant.selectedText")}</summary><p className="max-h-28 overflow-auto whitespace-pre-wrap border-l-2 pl-2">{assistant.target.text}</p></details>}
      <Textarea aria-label={t("assistant.instruction")} placeholder={t("assistant.placeholder")} value={assistant.input} onChange={event => assistant.setInput(event.target.value)} rows={3} disabled={assistant.working} />
      <div className="flex items-center justify-between gap-2"><p className="text-[11px] text-muted-foreground">{t("assistant.callNote")}</p><Button type="submit" size="sm" disabled={assistant.disabled || !assistant.input.trim()}>{assistant.working ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Send className="h-4 w-4 mr-1" />}{t("assistant.send")}</Button></div>
    </form>
  </section>;
}
