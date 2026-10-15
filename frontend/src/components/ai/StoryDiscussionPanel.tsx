import { useContext, useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { Loader2, Send, X } from "lucide-react";
import { api } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useStoryDiscussion } from "@/pages/Workbench/hooks/useStoryDiscussion";
import { StoryDiscussionWorkspaceContext } from "./story-discussion-context";
import { StoryDiscussionSources } from "./StoryDiscussionSources";
import { StoryProposalCard } from "./StoryProposalCard";
import { storyAssistantWarning } from "@/lib/story-assistant-messages";

type Props = { storyId: string; outlineId: string; visible: boolean; onClose: () => void; onSelectionChange: (selection: { storyId?: string; outlineId?: string }) => void };
export function StoryDiscussionPanel({ storyId, outlineId, visible, onClose, onSelectionChange }: Props) {
  const { t } = useTranslation();
  const assistant = useStoryDiscussion(storyId, outlineId);
  const workspace = useContext(StoryDiscussionWorkspaceContext);
  const stories = useQuery({ queryKey: ["story-discussion-stories"], queryFn: () => api.stories.list(), enabled: visible, retry: false });
  const outlines = useQuery({ queryKey: ["story-discussion-outlines", storyId], queryFn: () => api.outlines.listByStory(storyId), enabled: !!storyId && visible, retry: false });
  const [topicMode, setTopicMode] = useState<"create" | "rename" | null>(null);
  const [topicTitle, setTopicTitle] = useState("");
  const input = useRef<HTMLTextAreaElement>(null);
  const log = useRef<HTMLDivElement>(null);
  const scrollPositions = useRef(new Map<string, number>());
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  useEffect(() => { setTopicMode(null); setTopicTitle(""); }, [assistant.identity]);
  useEffect(() => { if (log.current) log.current.scrollTop = scrollPositions.current.get(assistant.identity) || 0; }, [assistant.identity, visible]);
  const dirty = !!workspace?.dirtyTargets.size;
  const topic = assistant.sessions.data?.find(item => item.id === assistant.selectedSessionId);
  const busy = assistant.working || assistant.session.isPending;
  const targetOutlineId = assistant.parent ? assistant.parent.outlineId || "" : outlineId;
  const latestProposal = [...assistant.turns].reverse().find(turn => turn.proposalIds.length)?.proposalIds[0];
  const titleAction = () => {
    const title = topicTitle.trim(); if (!title) return;
    void (topicMode === "rename" ? assistant.renameSession(title) : assistant.createSession(title)).then(success => { if (success) setTopicMode(null); });
  };
  return <section hidden={!visible} aria-label={t("storyAssistant.title")} className={visible ? "flex h-full min-h-0 min-w-0 flex-col bg-card" : "hidden"}>
    <header className="shrink-0 space-y-2 border-b p-3">
      <div className="flex items-center gap-2"><h2 className="font-semibold">{t("storyAssistant.title")}</h2><Button variant="ghost" size="sm" className="ml-auto" onClick={() => void assistant.refresh()}>{t("assistant.refresh")}</Button><Button variant="ghost" size="icon" onClick={onClose} aria-label={t("common.close")}><X className="h-4 w-4" /></Button></div>
      <label className="block text-xs space-y-1"><span>{t("common.selectStory")}</span><select aria-label={t("storyAssistant.story")} className="w-full min-w-0 rounded border bg-background p-2" value={storyId} onChange={event => onSelectionChange({ storyId: event.target.value, outlineId: "" })}><option value="">{t("common.selectStory")}</option>{stories.data?.map(story => <option key={story.id} value={story.id}>{story.title}</option>)}</select></label>
      {storyId && <><div className="flex min-w-0 gap-2"><select aria-label={t("storyAssistant.topic")} className="min-w-0 flex-1 rounded border bg-background p-2 text-xs" value={assistant.selectedSessionId} onChange={event => assistant.chooseSession(event.target.value)}><option value="" disabled>{t("storyAssistant.chooseTopic")}</option>{assistant.sessions.data?.map(session => <option key={session.id} value={session.id}>{session.title}</option>)}</select><Button size="sm" variant="outline" disabled={assistant.working} onClick={() => { setTopicMode("create"); setTopicTitle(t("storyAssistant.defaultTopic", { date: new Date().toLocaleDateString() })); }}>{t("storyAssistant.newTopic")}</Button><Button size="sm" variant="ghost" disabled={!assistant.selectedSessionId || assistant.working} onClick={() => { setTopicMode("rename"); setTopicTitle(topic?.title || assistant.session.data?.title || ""); }}>{t("storyAssistant.rename")}</Button></div>
      {topicMode && <form className="flex gap-2" onSubmit={event => { event.preventDefault(); titleAction(); }}><Input aria-label={t("storyAssistant.topicTitle")} value={topicTitle} onChange={event => setTopicTitle(event.target.value)} /><Button size="sm" type="submit" disabled={assistant.working || !topicTitle.trim()}>{t("common.save")}</Button><Button size="sm" type="button" variant="ghost" onClick={() => setTopicMode(null)}>{t("common.cancel")}</Button></form>}
      <label className="block text-xs space-y-1"><span>{t("storyAssistant.outline")}</span><select aria-label={t("storyAssistant.outline")} disabled={!!assistant.parent} className="w-full min-w-0 rounded border bg-background p-2 disabled:opacity-60" value={outlines.data?.some(item => item.id === targetOutlineId) ? targetOutlineId : ""} onChange={event => onSelectionChange({ outlineId: event.target.value })}><option value="">{t("storyAssistant.noOutline")}</option>{outlines.data?.map(outline => <option key={outline.id} value={outline.id}>{outline.title}</option>)}</select>{assistant.parent && <span className="block text-muted-foreground">{t("storyAssistant.refineTarget")}</span>}</label></>}
      <p className="text-xs text-muted-foreground">{t("storyAssistant.boundary")}</p>
      {dirty && <p role="status" className="text-xs text-amber-700">{t("storyAssistant.unsavedContext")}</p>}
    </header>
    <div ref={log} onScroll={() => { if (log.current) scrollPositions.current.set(assistant.identity, log.current.scrollTop); }} className="min-h-0 flex-1 space-y-4 overflow-y-auto p-3 break-words [overflow-wrap:anywhere]" role="log" aria-live="polite">
      {!storyId ? <p className="text-sm">{t("storyAssistant.chooseStory")} <Link className="text-primary underline" to="/novels/create">{t("storyAssistant.createStory")}</Link></p> : <>
        {assistant.session.isPending && <p>{t("assistant.loading")}</p>}
        {!assistant.session.isPending && !assistant.session.data?.id && <div className="space-y-2 text-sm"><p>{t("storyAssistant.startTopic")}</p><Button size="sm" disabled={assistant.working} onClick={() => void assistant.createSession(t("storyAssistant.defaultTopic", { date: new Date().toLocaleDateString() }))}>{t("storyAssistant.newTopic")}</Button></div>}
        {assistant.session.data?.id && !assistant.turns.length && <p className="text-sm text-muted-foreground">{t("storyAssistant.welcome")}</p>}
        {assistant.hasOlder && <Button size="sm" variant="outline" disabled={assistant.loadingOlder} onClick={() => void assistant.loadOlder()}>{t("assistant.older")}</Button>}
        {assistant.turns.map(turn => <article key={turn.id} className="space-y-2 text-sm"><p className="whitespace-pre-wrap rounded-lg bg-secondary p-3">{turn.instruction}</p><div className="space-y-3 rounded-lg border p-3">
          <p className="text-xs text-muted-foreground">{t(`assistant.state.${turn.resultKind || turn.status}`, { defaultValue: t("storyAssistant.turnStatus", { status: turn.status }) })}</p>
          {turn.answer && <p className="whitespace-pre-wrap leading-relaxed">{turn.answer}</p>}
          {turn.warnings.map((warning, index) => <p className="text-xs text-amber-700" key={index}>{storyAssistantWarning(warning)}</p>)}
          <StoryDiscussionSources sources={turn.sources} />
          {turn.suggestions.map(suggestion => <div key={suggestion.id} className="space-y-2 rounded border p-2"><p className="font-medium">{suggestion.title}</p><p className="text-xs text-muted-foreground">{suggestion.reason}</p><Button size="sm" variant="outline" disabled={busy || dirty} onClick={() => void assistant.propose(turn, suggestion)}>{t("storyAssistant.prepare")}</Button></div>)}
          {turn.answer && turn.intent === "DISCUSS" && !turn.suggestions.length && <Button size="sm" variant="outline" disabled={busy || dirty} onClick={() => void assistant.propose(turn)}>{t("storyAssistant.prepareThis")}</Button>}
          {(!!turn.suggestions.length || (!!turn.answer && turn.intent === "DISCUSS")) && <p className="text-xs text-muted-foreground">{t("storyAssistant.prepareNote")}</p>}
          <p className="text-xs text-muted-foreground">{t(["FAILED", "RECOVERY_REQUIRED", "CANCELLED"].includes(turn.status) ? "assistant.confirmedUsage" : "assistant.usage", { input: turn.usage.inputTokens, output: turn.usage.outputTokens, cost: turn.usage.cost, calls: turn.usage.modelCalls })}</p>
          {turn.operationId && ["FAILED", "RECOVERY_REQUIRED"].includes(turn.status) && <Button size="sm" variant="outline" disabled={busy} onClick={() => void assistant.recover(turn.operationId!)}>{t("assistant.recover")}</Button>}
          {turn.proposalIds.map(proposalId => <div key={proposalId} className="space-y-2"><Button size="sm" variant="outline" onClick={() => setExpanded(current => ({ ...current, [proposalId]: !(current[proposalId] ?? proposalId === latestProposal) }))}>{t("assistant.viewProposal")}</Button>{(expanded[proposalId] ?? proposalId === latestProposal) && <StoryProposalCard storyId={storyId} proposalId={proposalId} working={assistant.working} onRefine={proposal => { assistant.refine(proposal); input.current?.focus(); }} />}</div>)}
        </div></article>)}
      </>}
    </div>
    <form className="shrink-0 space-y-2 border-t p-3" onSubmit={event => { event.preventDefault(); if (!assistant.parent || !dirty) void assistant.submit(); }}>
      {assistant.error && <p role="alert" className="text-xs text-destructive">{assistant.error}</p>}
      {assistant.parent && <div className="flex items-center justify-between gap-2 rounded border p-2 text-xs"><span>{t("assistant.refining")} · {assistant.parent.label}</span><Button type="button" size="sm" variant="ghost" onClick={assistant.clearParent}>{t("assistant.clear")}</Button></div>}
      <Textarea ref={input} aria-label={t("storyAssistant.instruction")} placeholder={t("storyAssistant.placeholder")} rows={3} disabled={!assistant.session.data?.id || assistant.working} value={assistant.input} onChange={event => assistant.setInput(event.target.value)} />
      <div className="flex items-center justify-between gap-2"><p className="text-[11px] text-muted-foreground">{t("assistant.callNote")}</p><Button size="sm" disabled={!assistant.session.data?.id || busy || !assistant.input.trim() || (!!assistant.parent && dirty)}>{assistant.working ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Send className="mr-1 h-4 w-4" />}{t("assistant.send")}</Button></div>
    </form>
  </section>;
}
