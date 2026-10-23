import { useCallback, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import type { WorldProposal } from "@/lib/api/domains/world-assistant";
import { localizedErrorMessage } from "@/lib/error-messages";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { StoryDiscussionSources } from "@/components/ai/StoryDiscussionSources";
import { WorldProposalCard, WorldConflictHints, type ProposalDraft } from "./WorldProposalCard";
import { useWorldDiscussion, type WorldEditorState } from "../useWorldDiscussion";
export type SingleWorldAction={entryId:string|null;label:string};
const selectClass="h-10 rounded-md border border-input bg-background px-3 text-sm";
export function WorldAssistantPanel({world,editor,single,onSingleClose,onDirty}:{world:string;editor:WorldEditorState;single:SingleWorldAction|null;onSingleClose:()=>void;onDirty:(dirty:boolean)=>void}) {
  const {t}=useTranslation();const discussion=useWorldDiscussion(world,editor);
  const [view,setView]=useState("discussion"),[topic,setTopic]=useState<{mode:"create"|"rename";title:string}|null>(null);
  const [singleInstruction,setSingleInstruction]=useState("");const drafts=useRef(new Map<string,ProposalDraft>());
  const dirty=useCallback(()=>onDirty([...drafts.current.values()].some(d=>d.changed||!!d.pending)),[onDirty]);
  const [filters,setFilters]=useState({status:"READY",type:"",sessionId:""}),[before,setBefore]=useState<string|undefined>();
  const library=useQuery({queryKey:["world-assistant",world,"proposal","library",filters,before,editor.draft?.revision],queryFn:({signal})=>api.worldAssistant.proposals(world,{...filters,before},signal),enabled:view==="library",retry:false});
  const refine=(p:WorldProposal)=>{discussion.refine(p);setView("discussion");};
  const reorganize=(p:WorldProposal)=>{discussion.reorganize(p,t("world3.reorganizeInstruction",{target:String(p.candidate.title||t(`world3.type.${p.resourceType}`)),candidate:Object.entries(p.candidate).map(([k,v])=>`${k}: ${Array.isArray(v)?v.join("、"):v??""}`).join("\n")}));setView("discussion");};
  const card=(id:string)=><WorldProposalCard key={id} world={world} proposalId={id} editor={editor} working={discussion.working} drafts={drafts.current} onDirty={dirty} onRefine={refine} onReorganize={reorganize}/>;
  const setFilter=(key:keyof typeof filters,value:string)=>{setFilters(f=>({...f,[key]:value}));setBefore(undefined);};
  return <section className="space-y-5">
    <header className="max-w-3xl space-y-2"><h2 className="text-lg font-semibold">{t("world3.title")}</h2><p className="text-sm leading-6 text-muted-foreground">{t("world3.help")}</p></header>
    <Tabs value={view} onValueChange={setView} className="space-y-5"><TabsList><TabsTrigger value="discussion">{t("world3.discuss")}</TabsTrigger><TabsTrigger value="library">{t("world3.library")}</TabsTrigger></TabsList>
      <TabsContent value="discussion">
        <div className="grid gap-6 lg:grid-cols-[230px_minmax(0,1fr)]">
          <aside className="space-y-3 lg:border-r lg:pr-5"><div className="flex items-center justify-between"><h3 className="text-sm font-medium">{t("world3.topics")}</h3><Button size="sm" variant="ghost" onClick={()=>setTopic({mode:"create",title:""})} disabled={discussion.working}>{t("world3.newTopic")}</Button></div>
            <nav aria-label={t("world3.topics")} className="flex gap-1 overflow-x-auto lg:block lg:space-y-1">{discussion.sessions.data?.map(s=><button key={s.id} className={`shrink-0 rounded-md px-3 py-2 text-left text-sm lg:w-full ${discussion.selected===s.id?"bg-primary/10 font-medium text-primary":"text-muted-foreground hover:bg-muted"}`} onClick={()=>discussion.chooseSession(s.id)}>{s.title}</button>)}</nav>
            {discussion.selected&&<Button variant="outline" size="sm" onClick={()=>setTopic({mode:"rename",title:discussion.session.data?.title||""})}>{t("world3.renameTopic")}</Button>}
          </aside>
          <div className="min-w-0 space-y-6">
            {discussion.hasOlder&&<Button variant="outline" disabled={discussion.loadingOlder} onClick={()=>void discussion.loadOlder()}>{t("world3.older")}</Button>}
            {!discussion.turns.length&&<p className="py-8 text-sm leading-7 text-muted-foreground">{t(discussion.selected?"world3.emptyDiscussion":"world3.createFirst")}</p>}
            {discussion.turns.map(turn=><article key={turn.id} className="space-y-4 border-b pb-6"><header className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="whitespace-pre-wrap font-medium">{turn.instruction}</h3><time className="text-xs text-muted-foreground">{new Date(turn.createdAt).toLocaleString()}</time></header>
              <div className="space-y-3 border-l-2 border-primary/30 pl-4"><span className="text-xs text-muted-foreground">{t("world3.unconfirmed")}</span><p className="whitespace-pre-wrap break-words text-sm leading-7">{turn.answer||t(`worlds.taskStatus.${turn.status}`,{defaultValue:t("common.loading")})}</p><WorldConflictHints proposal={turn}/><StoryDiscussionSources sources={turn.sources}/>
              {turn.warnings.some(w=>w.startsWith("WORLD_UNREAD_RESOURCES"))&&<p className="text-xs text-muted-foreground">{t("world3.partialRead")}</p>}
              {turn.usage.modelCalls>0&&<p className="text-xs text-muted-foreground">{t("world3.usage",{calls:turn.usage.modelCalls,cost:turn.usage.cost.toLocaleString()})}</p>}
              {turn.answer&&<div className="flex flex-wrap gap-2">{turn.suggestions.map(s=><Button key={s.id} variant="outline" size="sm" disabled={discussion.working||!editor.canAct()} title={s.reason} onClick={()=>void discussion.propose(turn,s)}>{t("world3.proposeSuggestion",{title:s.title})}</Button>)}{turn.intent==="DISCUSS"&&<Button size="sm" variant="outline" disabled={discussion.working||!editor.canAct()} onClick={()=>void discussion.propose(turn)}>{t("world3.propose")}</Button>}</div>}
              {["FAILED","RECOVERY_REQUIRED"].includes(turn.status)&&turn.operationId&&<Button variant="outline" disabled={discussion.working} onClick={()=>void discussion.recover(turn.operationId!)}>{t("worlds.recoverTask")}</Button>}
              </div>{turn.proposalIds.map(card)}
            </article>)}
            {!!discussion.selected&&<div className="space-y-3 rounded-lg border bg-muted/20 p-4">{discussion.parent&&<div className="flex items-center justify-between gap-3 text-sm"><span>{t("world3.refining",{target:String(discussion.parent.candidate.title||t(`world3.type.${discussion.parent.resourceType}`))})}</span><Button variant="ghost" size="sm" onClick={discussion.clearParent}>{t("common.cancel")}</Button></div>}
              <label className="block space-y-2 text-sm"><span>{t("world3.instruction")}</span><Textarea value={discussion.input} onChange={e=>discussion.setInput(e.target.value)} rows={4} maxLength={8000} placeholder={t("world3.placeholder")}/></label>
              {!editor.canAct()&&<p className="text-sm text-amber-700">{t("world3.saveFirst")}</p>}
              <div className="flex flex-wrap gap-2"><Button disabled={!discussion.input.trim()||discussion.working||!editor.canAct()} onClick={()=>void discussion.submit()}>{t(discussion.parent?"assistant.refine":"world3.send")}</Button>{!discussion.parent&&<Button variant="outline" disabled={!discussion.input.trim()||discussion.working||!editor.canAct()} onClick={()=>void discussion.submit("PROPOSE")}>{t("world3.propose")}</Button>}<Button variant="ghost" onClick={()=>void discussion.refresh()}>{t("worlds.refreshTask")}</Button></div>
            </div>}
          </div>
        </div>
      </TabsContent>
      <TabsContent value="library" className="space-y-4">
        <div className="flex flex-wrap gap-3"><select aria-label={t("world3.statusFilter")} className={selectClass} value={filters.status} onChange={e=>setFilter("status",e.target.value)}><option value="">{t("world3.allStatuses")}</option>{["READY","ACCEPTED","REJECTED"].map(s=><option key={s} value={s}>{t(`assistant.proposal.${s}`)}</option>)}</select>
          <select aria-label={t("world3.typeFilter")} className={selectClass} value={filters.type} onChange={e=>setFilter("type",e.target.value)}><option value="">{t("world3.allTypes")}</option>{["METADATA","ENTRY","RELATION","OVERVIEW"].map(s=><option key={s} value={s}>{t(`world3.type.${s}`)}</option>)}</select>
          <select aria-label={t("world3.topicFilter")} className={selectClass} value={filters.sessionId} onChange={e=>setFilter("sessionId",e.target.value)}><option value="">{t("world3.allTopics")}</option>{discussion.sessions.data?.map(s=><option key={s.id} value={s.id}>{s.title}</option>)}</select><Button variant="ghost" onClick={()=>void library.refetch()}>{t("worlds.refreshTask")}</Button></div>
        {library.isLoading&&<p>{t("common.loading")}</p>}{library.error&&<p role="alert" className="text-sm text-destructive">{localizedErrorMessage(library.error)}</p>}
        {!library.isLoading&&!library.data?.items.length&&<p className="py-8 text-sm text-muted-foreground">{t("world3.noProposals")}</p>}
        {library.data?.items.map(p=>card(p.id))}<div className="flex gap-3">{before&&<Button variant="outline" onClick={()=>setBefore(undefined)}>{t("world3.firstPage")}</Button>}{library.data?.nextBefore&&<Button variant="outline" onClick={()=>setBefore(library.data!.nextBefore!)}>{t("world3.nextPage")}</Button>}</div>
      </TabsContent>
    </Tabs>
    {discussion.error&&<p role="alert" className="text-sm text-destructive">{discussion.error}</p>}
    <Dialog open={!!topic} onOpenChange={open=>{if(!open)setTopic(null);}}><DialogContent><DialogHeader><DialogTitle>{t(topic?.mode==="rename"?"world3.renameTopic":"world3.newTopic")}</DialogTitle><DialogDescription>{t("world3.topicHelp")}</DialogDescription></DialogHeader><Input aria-label={t("world3.topicTitle")} value={topic?.title||""} maxLength={120} onChange={e=>setTopic(v=>v?{...v,title:e.target.value}:v)}/><DialogFooter><Button variant="outline" onClick={()=>setTopic(null)}>{t("common.cancel")}</Button><Button disabled={!topic?.title.trim()||discussion.working} onClick={async()=>{if(topic&&await(topic.mode==="create"?discussion.create(topic.title):discussion.rename(topic.title)))setTopic(null);}}>{t("common.save")}</Button></DialogFooter></DialogContent></Dialog>
    <Dialog open={!!single} onOpenChange={open=>{if(!open&&!discussion.working){onSingleClose();setSingleInstruction("");}}}><DialogContent><DialogHeader><DialogTitle>{single?.entryId?t("world3.refineEntry",{title:single.label}):t("world3.integrate")}</DialogTitle><DialogDescription>{t(single?.entryId?"world3.refineHelp":"world3.integrateHelp")}</DialogDescription></DialogHeader><label className="space-y-2 text-sm">{t("world3.instruction")}<Textarea value={singleInstruction} onChange={e=>setSingleInstruction(e.target.value)} maxLength={8000}/></label>{!editor.canAct()&&<p className="text-sm text-amber-700">{t("world3.saveFirst")}</p>}{discussion.error&&<p role="alert" className="text-sm text-destructive">{discussion.error}</p>}<DialogFooter><Button variant="outline" disabled={discussion.working} onClick={()=>{onSingleClose();setSingleInstruction("");}}>{t("common.cancel")}</Button><Button disabled={!single||discussion.working||!editor.canAct()} onClick={async()=>{if(single&&await discussion.single(single.entryId,singleInstruction)){onSingleClose();setSingleInstruction("");setView("discussion");}}}>{t("world3.generateProposal")}</Button></DialogFooter></DialogContent></Dialog>
  </section>;
}
