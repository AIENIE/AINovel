import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, isApiError } from "@/lib/api-client";
import { retryTrackedAiOperation, runTrackedAiOperation } from "@/lib/ai-operation-store";
import type { WorldProposal, WorldTurn, WorldTurnRequest } from "@/lib/api/domains/world-assistant";
import { localizedErrorMessage } from "@/lib/error-messages";
import type { useWorldDraft } from "./useWorldDraft";
export type WorldEditorState=ReturnType<typeof useWorldDraft>;
const active=(turn:WorldTurn)=>["PENDING","QUEUED","RUNNING","STREAMING","PROCESSING"].includes(turn.status);
export function useWorldDiscussion(world:string,editor:WorldEditorState) {
  const client=useQueryClient();
  const [selected,setSelected]=useState(()=>{try{return sessionStorage.getItem(`ainovel.world-topic:${world}`)||"";}catch{return "";}});
  const [inputs,setInputs]=useState<Record<string,string>>({}),[parents,setParents]=useState<Record<string,WorldProposal|null>>({});
  const [error,setError]=useState(""),[busy,setBusy]=useState(false);
  const [older,setOlder]=useState<Record<string,{turns:WorldTurn[];next:string|null}>>({});
  const [loadingOlder,setLoadingOlder]=useState(false);
  const lock=useRef(false),keys=useRef(new Map<string,string>());
  const chooseSession=useCallback((id:string)=>{setSelected(id);try{sessionStorage.setItem(`ainovel.world-topic:${world}`,id);}catch{/* Server topics persist. */}},[world]);
  const refresh=useCallback(async()=>{await client.invalidateQueries({queryKey:["world-assistant",world]});},[client,world]);
  const sessions=useQuery({queryKey:["world-assistant",world,"sessions"],queryFn:({signal})=>api.worldAssistant.sessions(world,signal),retry:false});
  const session=useQuery({queryKey:["world-assistant",world,"session",selected],queryFn:({signal})=>api.worldAssistant.session(world,selected||undefined,undefined,signal),retry:false,refetchInterval:q=>q.state.data?.turns.some(active)?2000:false});
  useEffect(()=>{if(!selected&&session.data?.id)chooseSession(session.data.id);},[selected,session.data?.id,chooseSession]);
  useEffect(()=>{const changed=()=>{void refresh();};window.addEventListener("ainovel:world-assistant-updated",changed);return()=>window.removeEventListener("ainovel:world-assistant-updated",changed);},[refresh]);
  useEffect(()=>{void client.invalidateQueries({queryKey:["world-assistant",world,"proposal"]});},[client,world,editor.draft?.revision,editor.remote?.revision]);
  const turns=useMemo(()=>[...new Map([...(older[selected]?.turns||[]),...(session.data?.turns||[])].map(turn=>[turn.id,turn])).values()].sort((a,b)=>a.createdAt.localeCompare(b.createdAt)),[older,selected,session.data?.turns]);
  const perform=async(work:()=>Promise<void>)=>{if(lock.current)return;lock.current=true;setBusy(true);setError("");try{await work();return true;}catch(e){if(!(e instanceof Error&&e.message==="AI_QUOTE_CANCELLED"))setError(localizedErrorMessage(e));return false;}finally{lock.current=false;setBusy(false);await refresh();}};
  const keyed=async<T,>(name:string,work:(key:string)=>Promise<T>)=>{const key=keys.current.get(name)||crypto.randomUUID();keys.current.set(name,key);try{const value=await work(key);keys.current.delete(name);return value;}catch(e){if(isApiError(e)&&e.status>=400&&e.status<500&&e.status!==408)keys.current.delete(name);throw e;}};
  const saved=()=>{if(!editor.canAct()||!editor.draft)throw new Error("WORLD_DRAFT_SAVE_REQUIRED");return editor.draft.revision;};
  const input=inputs[selected]||"",parent=parents[selected]||null;
  const setInput=(value:string)=>setInputs(v=>({...v,[selected]:value}));
  const clearParent=()=>setParents(v=>({...v,[selected]:null}));
  const send=(request:Omit<WorldTurnRequest,"expectedDraftRevision">)=>perform(async()=>{
    if(turns.some(active))return;const body={...request,expectedDraftRevision:saved()};
    const submittedInput=input,submittedParent=parent;
    const accepted=await keyed(`turn:${JSON.stringify(body)}`,key=>api.worldAssistant.submit(world,body,key));
    setInputs(v=>v[selected]===submittedInput?{...v,[selected]:""}:v);
    setParents(v=>v[selected]===submittedParent?{...v,[selected]:null}:v);
    await refresh();await runTrackedAiOperation(Promise.resolve(accepted));
  });
  return {selected,sessions,session,turns,input,setInput,parent,clearParent,chooseSession,refresh,error:error||(session.error||sessions.error?localizedErrorMessage(session.error||sessions.error):""),working:busy||turns.some(active),
    create:(title:string)=>perform(async()=>{if(!title.trim())return;const s=await keyed(`create:${title}`,key=>api.worldAssistant.createSession(world,title.trim(),key));chooseSession(s.id);}),
    rename:(title:string)=>perform(async()=>{if(!selected||!title.trim())return;await keyed(`rename:${selected}:${title}`,key=>api.worldAssistant.renameSession(world,selected,title.trim(),key));}),
    submit:(intent:"DISCUSS"|"PROPOSE"="DISCUSS")=>selected&&input.trim()?send({sessionId:selected,intent:parent?"REFINE":intent,instruction:input.trim(),...(parent?{parentProposalId:parent.id}:{})}):Promise.resolve(),
    propose:(turn:WorldTurn,suggestion?:WorldTurn["suggestions"][number])=>send({sessionId:selected,intent:"PROPOSE",instruction:suggestion?.title||turn.instruction,sourceTurnIds:suggestion?.sourceTurnIds.length?suggestion.sourceTurnIds:[...new Set([...turn.sources.filter(s=>s.type==="DISCUSSION").map(s=>s.id.replace(/^turn:/,"")),turn.id])].slice(-8),...(suggestion?{suggestionId:suggestion.id}:{})}),
    refine:(p:WorldProposal)=>{chooseSession(p.sessionId);setParents(v=>({...v,[p.sessionId]:p}));setInputs(v=>({...v,[p.sessionId]:""}));},
    reorganize:(p:WorldProposal,instruction:string)=>{chooseSession(p.sessionId);setParents(v=>({...v,[p.sessionId]:null}));setInputs(v=>({...v,[p.sessionId]:instruction}));},
    single:(entry:string|null,instruction:string)=>perform(async()=>{const revision=saved();const a=await keyed(`single:${entry}:${revision}:${instruction}`,key=>api.worldAssistant.single(world,entry,revision,instruction,key));chooseSession(a.sessionId);await refresh();await runTrackedAiOperation(Promise.resolve(a));}),
    recover:(id:string)=>perform(()=>retryTrackedAiOperation(id)),
    loadingOlder,hasOlder:!!(older[selected]?older[selected].next:session.data?.nextBefore),
    loadOlder:()=>perform(async()=>{const before=older[selected]?.next??session.data?.nextBefore;if(!before)return;setLoadingOlder(true);try{const page=await api.worldAssistant.session(world,selected,before);setOlder(v=>({...v,[selected]:{turns:[...page.turns,...(v[selected]?.turns||[])],next:page.nextBefore}}));}finally{setLoadingOlder(false);}}),
  };
}
