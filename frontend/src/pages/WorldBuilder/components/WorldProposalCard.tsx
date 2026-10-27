import { WorldDateEditor, WorldReferenceChoices } from './WorldTimeline';
import type { WorldDateRange } from '@/types';
import { Input } from '@/components/ui/input';
import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api, isApiError } from "@/lib/api-client";
import type { WorldProposal } from "@/lib/api/domains/world-assistant";
import { localizedErrorMessage } from "@/lib/error-messages";
import { StoryDiscussionSources } from "@/components/ai/StoryDiscussionSources";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { WorldEditorState } from "../useWorldDiscussion";
import { WorldListInput } from "./WorldListInput";
import { WorldChangeList } from "./WorldVersionViews";
import { WORLD_ENTRY_TYPES } from "../world-draft";
import { WorldProposalDiff } from "./WorldProposalDiff";

type Receipt={action:"edit"|"accept"|"reject";key:string;revision:number;draftRevision:number;ack:boolean;candidate:Record<string,unknown>};
export type ProposalDraft={revision:number;candidate:Record<string,unknown>;changed:boolean;pending?:Receipt};
const text=(v:unknown)=>Array.isArray(v)?v.join("、"):v==null?"":typeof v==="object"?JSON.stringify(v,null,2):String(v);
export function WorldConflictHints({proposal}:{proposal:Pick<WorldProposal,"conflicts"|"sources">}) {
  const {t}=useTranslation();if(!proposal.conflicts.length)return null;
  return <aside className="space-y-3 border-l-2 border-amber-500 bg-amber-50/40 p-3 text-sm dark:bg-amber-950/20"><p className="font-medium">{t("world3.possibleConflict")}</p>{proposal.conflicts.map((c,i)=><details key={i} open><summary>{c.title}</summary><p className="mt-2 whitespace-pre-wrap">{c.explanation}</p><StoryDiscussionSources sources={proposal.sources.filter(s=>c.sourceIds.includes(s.id))}/></details>)}</aside>;
}
export function WorldProposalCard({world,proposalId,editor,working,drafts,onDirty,onRefine,onReorganize}:{world:string;proposalId:string;editor:WorldEditorState;working:boolean;drafts:Map<string,ProposalDraft>;onDirty:()=>void;onRefine:(p:WorldProposal)=>void;onReorganize:(p:WorldProposal)=>void}) {
  const {t}=useTranslation(),client=useQueryClient();const key=["world-assistant",world,"proposal",proposalId];
  const query=useQuery({queryKey:key,queryFn:({signal})=>api.worldAssistant.proposal(world,proposalId,signal),retry:false});const p=query.data;
  const [draft,setDraft]=useState<ProposalDraft|null>(()=>drafts.get(proposalId)||null),[error,setError]=useState(""),[saving,setSaving]=useState(false),[ack,setAck]=useState(false);
  const lock=useRef(false);
  const keep=(next:ProposalDraft)=>{drafts.set(proposalId,next);setDraft(next);onDirty();};
  useEffect(()=>{if(p&&!draft){const initial={revision:p.revision,candidate:structuredClone(p.candidate),changed:false};drafts.set(proposalId,initial);setDraft(initial);}},[p,draft,drafts,proposalId]);
  useEffect(()=>setAck(false),[p?.draftRevision,p?.revision]);
  const candidate=useMemo(()=>draft?.candidate||p?.candidate||{},[draft?.candidate,p?.candidate]);
  const fields=p?.resourceType==="ENTRY"?["title","type","summary","content","aliases","tags"]:p?.resourceType==="RELATION"?["sourceId","targetId","type","description"]:p?.resourceType==="ERA"?["name","description","date","sortOrder"]:p?.resourceType==="EVENT"?["title","summary","content","aliases","tags","date","entryIds","eraIds","sortOrder"]:p?.resourceType==="METADATA"?["value"]:["content"];
  if(query.error)return <p role="alert">{localizedErrorMessage(query.error)} <Button onClick={()=>void query.refetch()}>{t("common.retry")}</Button></p>;
  if(!p||!draft)return <p>{t("common.loading")}</p>;
  const changed=JSON.stringify(candidate)!==JSON.stringify(p.candidate),stale=draft.revision!==p.revision,unavailable=working||saving||!!draft.pending;
  const label=(field:string)=>["name","description","date","sortOrder","entryIds","eraIds"].includes(field)?t(`world5.${field}`):field==="value"?t(`worldMeta.${p.fieldKey}`):t(`world2.${field==="sourceId"?"source":field==="targetId"?"target":field}`,{defaultValue:field});
  const display=(field:string,value:unknown)=>field==="type"&&p.resourceType==="ENTRY"&&text(value)?t(`world2.types.${text(value)}`,{defaultValue:text(value)}):text(value);
  const title=text(candidate.title)||text(candidate.name)||(p.resourceType==="METADATA"?t(`worldMeta.${p.fieldKey}`):t(`world3.type.${p.resourceType}`));
  const updateValue=(field:string,value:unknown)=>{const next={...candidate,[field]:value};keep({...draft,candidate:next,changed:JSON.stringify(next)!==JSON.stringify(p.candidate)});};
  const update=(field:string,value:string)=>updateValue(field,["aliases","tags"].includes(field)||p.fieldKey==="themes"?value.split(/[、,，\n]/).map(s=>s.trim()).filter(Boolean):value);
  const execute=async(receipt:Receipt)=>{
    if(lock.current)return;if(receipt.action==="accept"&&!editor.canAct()){setError(t("world3.saveFirst"));return;}
    lock.current=true;setSaving(true);setError("");keep({...draft,pending:receipt});
    try {
      const updated=receipt.action==="edit"?await api.worldAssistant.editProposal(world,p.id,receipt.revision,receipt.candidate,receipt.key):(await api.worldAssistant.decide(world,p.id,receipt.action,receipt.revision,receipt.draftRevision,receipt.ack,receipt.key)).proposal;
      keep({revision:updated.revision,candidate:structuredClone(updated.candidate),changed:false});client.setQueryData(key,updated);
      if(receipt.action==="accept")await editor.refresh();await client.invalidateQueries({queryKey:["world-assistant",world]});
    }catch(e){setError(localizedErrorMessage(e));if(isApiError(e)&&e.status>=400&&e.status<500&&e.status!==408){keep({...draft,pending:undefined});await Promise.all([query.refetch(),editor.refresh()]);}}
    finally{lock.current=false;setSaving(false);}
  };
  const act=(action:Receipt["action"])=>execute({action,key:crypto.randomUUID(),revision:p.revision,draftRevision:p.draftRevision,ack,candidate:structuredClone(candidate)});
  const reset=()=>{if(!changed||confirm(t("storyAssistant.discardEdit")))keep({revision:p.revision,candidate:structuredClone(p.candidate),changed:false});};
  return <article aria-label={t("world3.proposal")} className="min-w-0 space-y-4 rounded-lg border bg-background p-4 md:p-5">
    <header className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="min-w-0 break-words font-semibold [overflow-wrap:anywhere]">{title}</h3><span className="text-xs text-muted-foreground">{t(`storyAssistant.action.${p.action}`)} · {t(`world3.type.${p.resourceType}`)} · {t(`assistant.proposal.${p.status}`)}</span></header>
    <p className="whitespace-pre-wrap break-words text-sm [overflow-wrap:anywhere]">{p.rationale}</p><p className="text-xs text-muted-foreground">{t("world3.acceptHelp")}</p>
    {p.parentProposalId&&<p className="text-xs text-muted-foreground">{t("world3.childProposal")}</p>}
    {p.status==="READY"&&(!p.applicable||stale)&&<div role="status" className="space-y-2 text-sm text-amber-800 dark:text-amber-200"><p>{t(stale?"world3.candidateStale":`world3.reason.${p.staleReason}`,{defaultValue:t("world3.stale")})}</p><Button variant="outline" size="sm" disabled={unavailable||changed} onClick={()=>onReorganize(p)}>{t("world3.reorganize")}</Button></div>}
    {p.status==="READY"&&p.contextChanged&&p.applicable&&<label className="flex items-start gap-2 rounded border border-amber-300/60 p-3 text-sm"><input type="checkbox" checked={ack} onChange={e=>setAck(e.target.checked)} className="mt-1"/>{t("world3.reviewContext")}</label>}
    {!!p.contextChanges.length&&<details><summary className="cursor-pointer text-sm">{t("world3.contextDiff")}</summary><div className="mt-3"><WorldChangeList diff={{fromVersionId:null,toVersionId:null,draftRevision:p.draftRevision,changes:p.contextChanges.map(c=>({...c,before:c.before,after:c.after}))}}/></div></details>}
    <WorldConflictHints proposal={p}/>{!!p.conflicts.length&&(p.candidateEdited||changed)&&<p className="text-xs text-amber-700">{t("world3.reviewHints")}</p>}
    <WorldProposalDiff fields={fields.filter(field=>text(p.before?.[field])!==text(candidate[field])).map(field=>({key:field,label:label(field),before:display(field,p.before?.[field]),after:display(field,candidate[field])}))} beforeAbsent={p.before==null}/>
    <details><summary className="cursor-pointer text-sm">{t("world3.compare")}</summary><div className="mt-3 grid gap-5 lg:grid-cols-3">{[{label:t("world2.before"),value:p.before},{label:t("world3.current"),value:p.current},{label:t("world3.originalCandidate"),value:p.modelCandidate}].map(column=><section key={column.label} className="min-w-0"><h4 className="mb-3 text-xs font-semibold">{column.label}</h4>{fields.map(field=><div key={field} className="mb-3 text-sm"><p className="text-xs text-muted-foreground">{label(field)}</p><p className="whitespace-pre-wrap break-words [overflow-wrap:anywhere]">{display(field,column.value?.[field])||"—"}</p></div>)}</section>)}</div></details>
    <details><summary className="cursor-pointer text-sm">{t(p.status==="READY"?"storyAssistant.editCandidate":"storyAssistant.fullCandidate")}</summary><div className="mt-4 space-y-4">{fields.map(field=><div key={field} className="block space-y-2 text-sm"><span>{label(field)}</span>{field==="date"?<WorldDateEditor key={`${p.id}:${p.revision}`} value={candidate.date as WorldDateRange|null} disabled={p.status!=="READY"||unavailable||stale} onChange={value=>updateValue(field,value)}/>:field==="sortOrder"?<Input type="number" step="1" value={text(candidate[field])} disabled={p.status!=="READY"||unavailable||stale} onChange={e=>updateValue(field,Number(e.target.value))}/>:field==="entryIds"||field==="eraIds"?<WorldReferenceChoices label={label(field)} values={Array.isArray(candidate[field])?candidate[field] as string[]:[]} options={field==="entryIds"?(editor.draft?.entries||[]).map(e=>({id:e.id,label:e.title})):(editor.draft?.timeline?.eras||[]).map(e=>({id:e.id,label:e.name}))} disabled={p.status!=="READY"||unavailable||stale} onChange={value=>updateValue(field,value)}/>: ["aliases","tags"].includes(field)||p.fieldKey==="themes"?<WorldListInput label={label(field)} values={Array.isArray(candidate[field])?(candidate[field] as unknown[]).map(String):[]} disabled={p.status!=="READY"||unavailable||stale} onChange={values=>update(field,values.join(","))}/>:field==="type"&&p.resourceType==="ENTRY"?<select aria-label={label(field)} className="block rounded border bg-background p-2" value={text(candidate[field])} disabled={p.status!=="READY"||unavailable||stale} onChange={e=>update(field,e.target.value)}>{WORLD_ENTRY_TYPES.map(type=><option key={type} value={type}>{t(`world2.types.${type}`)}</option>)}</select>:p.resourceType==="RELATION"&&["sourceId","targetId"].includes(field)?<select aria-label={label(field)} className="block max-w-full rounded border bg-background p-2" value={text(candidate[field])} disabled={p.status!=="READY"||unavailable||stale} onChange={e=>update(field,e.target.value)}>{!editor.draft?.entries?.some(e=>e.id===candidate[field])&&<option value={text(candidate[field])}>{t("world3.pendingEndpoint")}</option>}{editor.draft?.entries?.map(e=><option key={e.id} value={e.id}>{e.title}</option>)}</select>:<Textarea aria-label={label(field)} value={text(candidate[field])} disabled={p.status!=="READY"||unavailable||stale} rows={field==="content"?8:2} onChange={e=>update(field,e.target.value)}/>}</div>)}</div></details>
    <StoryDiscussionSources sources={p.sources}/>
    {!!p.sourceTurnIds.length&&<details className="text-xs text-muted-foreground"><summary>{t("world3.discussionOrigin")}</summary>{p.sourceTurnIds.map(id=><p key={id} className="break-all">{id}</p>)}</details>}
    {error&&<p role="alert" className="text-sm text-destructive">{error}</p>}
    {p.status==="READY"&&!editor.canAct()&&<div className="space-y-2 text-sm"><p>{t("world3.saveFirst")}</p><Button variant="outline" disabled={editor.saving||!!editor.remote||editor.conflict} onClick={()=>void editor.save()}>{t("world3.saveDraft")}</Button></div>}
    {changed&&<p className="text-xs text-amber-700">{t("storyAssistant.candidateUnsaved")}</p>}
    {draft.pending&&<div className="text-sm"><p>{t("assistant.receiptPending")}</p><Button disabled={saving} onClick={()=>void execute(draft.pending!)}>{t("assistant.checkSave")}</Button></div>}
    <footer className="flex flex-wrap gap-2">{p.status==="READY"&&<><Button disabled={unavailable||stale||changed||!p.applicable||!editor.canAct()||(p.contextChanged&&!ack)} onClick={()=>void act("accept")}>{t("world3.acceptSave")}</Button><Button variant="outline" disabled={unavailable||stale||changed} onClick={()=>void act("reject")}>{t("assistant.reject")}</Button><Button variant="outline" disabled={unavailable||stale||changed||!p.applicable||!editor.canAct()} onClick={()=>onRefine(p)}>{t("assistant.refine")}</Button>{changed&&<Button variant="secondary" disabled={unavailable||stale} onClick={()=>void act("edit")}>{t("storyAssistant.saveCandidate")}</Button>}</>}{(changed||stale)&&<Button variant="ghost" disabled={unavailable} onClick={reset}>{t("storyAssistant.reloadCandidate")}</Button>}</footer>
  </article>;
}
