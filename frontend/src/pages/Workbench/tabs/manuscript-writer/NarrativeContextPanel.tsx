import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { localizedErrorMessage } from '@/lib/error-messages';
import { useAuth } from '@/contexts/auth-state';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Input } from '@/components/ui/input';
import { KnowledgeViewEditor } from './KnowledgeViewEditor';
import { StableAttributeEditor } from './StableAttributeEditor';
import type { Manuscript } from '@/types';
import type { NarrativeRecord, NarrativeSource } from '@/types/narrative';
import type { ContextDocument, ContextEntry, ContextPreview, ContextState, ContextUpdate, ContextView, KnowledgeGrant, RetainedCandidate } from '@/types/narrative-context';

type Props = { active: boolean; manuscript: Manuscript; sceneId: string; disabled: boolean;
  characters: Array<{ id: string; name: string }>; scenes: Array<{ id: string; title: string }>; records: NarrativeRecord[] };
type Draft = { base: ContextState; document: ContextDocument; enabled: boolean; key: string; edited: boolean };
const selectClass = 'w-full rounded border bg-background p-2 text-sm';
const emptyDocument = (): ContextDocument => ({ policy: { perspective: 'LIMITED_THIRD', allowInner: true, viewpointByScene: {} }, grants: [], entries: [] });
function restore(key: string): Draft | null {
  try { const d = JSON.parse(sessionStorage.getItem(key) || 'null'); return d?.base && d?.document?.policy && Array.isArray(d.document.grants) && Array.isArray(d.document.entries) && typeof d.key === 'string' ? d : null; }
  catch { return null; }
}

export function NarrativeContextPanel({ active, manuscript, sceneId, disabled, characters, scenes, records }: Props) {
  const { user } = useAuth(); const client = useQueryClient();
  const mid=manuscript.id, bid=manuscript.currentBranchId || '';
  const storageKey=`ainovel.h2:${user?.id}:${mid}:${bid}`;
  const [draft,setDraft]=useState<Draft | null>(()=>restore(storageKey));
  const [error,setError]=useState(''); const [working,setWorking]=useState(false);
  const [view,setView]=useState<ContextView>('SCENE'); const [who,setWho]=useState('');
  const [preview,setPreview]=useState<ContextPreview | null>(null);
  const [source,setSource]=useState<NarrativeSource | null>(null);
  const [grant,setGrant]=useState<KnowledgeGrant | null>(()=>{try{return JSON.parse(sessionStorage.getItem(storageKey+':grant') || 'null');}catch{return null;}});
  const [history,setHistory]=useState<Array<{ revision:number; createdAt:string; document:ContextDocument }>>([]);
  const [candidates,setCandidates]=useState<RetainedCandidate[]>([]);
  const query=useQuery({queryKey:['narrative-context',mid,bid,manuscript.version],queryFn:()=>api.narrativeContext.state(mid,bid),enabled:active && !!bid,retry:false});
  useEffect(()=>{ if(query.data && (!draft || !draft.edited && JSON.stringify(draft.base)!==JSON.stringify(query.data))) setDraft({base:query.data,document:query.data.document || emptyDocument(),enabled:query.data.enabled,key:crypto.randomUUID(),edited:false}); },[query.data,draft]);
  useEffect(()=>{if(draft)try{sessionStorage.setItem(storageKey,JSON.stringify(draft));}catch{/* In-memory edits remain available. */}},[draft,storageKey]);
  useEffect(()=>{try{sessionStorage.setItem(storageKey+':grant',JSON.stringify(grant));}catch{/* Keep in-memory draft. */}},[grant,storageKey]);
  useEffect(()=>{if(grant?.approvalId) void api.narrative.evidence(mid,bid,grant.approvalId).then(setSource).catch(()=>setSource(null));},[mid,bid,grant?.approvalId]);
  useEffect(()=>{setPreview(null);},[sceneId,manuscript.version]);
  const run=async(action:()=>Promise<void>)=>{setWorking(true);setError('');try{await action();}catch(e){setError(localizedErrorMessage(e));}finally{setWorking(false);}};
  const edit=(document:ContextDocument,enabled=draft?.enabled || false)=>{if(draft){setDraft({...draft,document,enabled,key:crypto.randomUUID(),edited:true});setPreview(null);}};
  const save=()=>run(async()=>{
    if(!draft)return;
    const request:ContextUpdate={enabled:draft.enabled,document:draft.document,expectedManuscriptVersion:draft.base.manuscriptVersion,
      expectedCanonRevision:draft.base.canonRevision,expectedRevision:draft.base.revision,expectedSettingsRevision:draft.base.settingsRevision};
    const saved=await api.narrativeContext.update(mid,bid,request,draft.key);
    setDraft({base:saved,document:saved.document || emptyDocument(),enabled:saved.enabled,key:crypto.randomUUID(),edited:false});
    await client.invalidateQueries({queryKey:['narrative-context',mid,bid]});
  });
  const reload=()=>run(async()=>{const saved=await api.narrativeContext.state(mid,bid);setDraft({base:saved,document:saved.document || emptyDocument(),enabled:saved.enabled,key:crypto.randomUUID(),edited:false});setGrant(null);setPreview(null);});
  const updateEntry=(index:number,patch:Partial<ContextEntry>)=>draft && edit({...draft.document,entries:draft.document.entries.map((e,i)=>i===index?{...e,...patch}:e)});
  const addEntry=()=>draft && edit({...draft.document,entries:[...draft.document.entries,{id:crypto.randomUUID(),kind:'PLAN',text:'',fromSceneId:sceneId,characterIds:[],narratorVisible:false,dependencyRecordIds:[],stale:false}]});
  const selectEvidence=(approvalId:string)=>run(async()=>{const result=await api.narrative.evidence(mid,bid,approvalId);setSource(result);setGrant(g=>g?{...g,approvalId,evidence:[{blockId:'',quote:''}]}:g);});
  const sceneOptions=scenes.map(s=><option key={s.id} value={s.id}>{s.title}</option>);
  const characterOptions=<><option value="">请选择人物</option>{characters.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</>;
  const validRecords=records.filter(r=>r.status==='CONFIRMED');
  const lock=disabled || working;
  return <details className="rounded border p-3 space-y-3">
    <summary className="cursor-pointer text-sm font-medium">人物认知与隔离 · {query.data?(query.data.enabled?'已开启':'沿用原上下文'):'正在读取状态'}</summary>
    <p className="text-xs text-muted-foreground">按作品开启。知情不等于真实，计划不等于已发生。未分类资料不会进入隔离上下文。</p>
    {(error || query.error) && <p role="alert" className="text-sm text-destructive">{error || localizedErrorMessage(query.error)}</p>}
    {!draft ? <Button variant="outline" disabled={working} onClick={()=>void reload()}>读取隔离配置</Button> : <>
      <fieldset disabled={lock} className="space-y-3 min-w-0">
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={draft.enabled} onChange={e=>edit(draft.document,e.target.checked)}/>启用本作品的上下文隔离</label>
        <label className="block text-xs">叙事视角<select className={selectClass} value={draft.document.policy.perspective} onChange={e=>edit({...draft.document,policy:{...draft.document.policy,perspective:e.target.value as ContextDocument['policy']['perspective']}})}>
          <option value="LIMITED_THIRD">第三人称限知</option><option value="FIRST_PERSON">第一人称</option><option value="OMNISCIENT">全知叙述（人物仍有限知情）</option>
        </select></label>
        <label className="flex gap-2 text-xs"><input type="checkbox" checked={draft.document.policy.allowInner} onChange={e=>edit({...draft.document,policy:{...draft.document.policy,allowInner:e.target.checked}})}/>允许展示人物内心</label>
        <label className="block text-xs">当前场景的视角人物<select className={selectClass} value={draft.document.policy.viewpointByScene[sceneId] || ''} onChange={e=>{
          const viewpoints={...draft.document.policy.viewpointByScene};if(e.target.value)viewpoints[sceneId]=e.target.value;else delete viewpoints[sceneId];
          edit({...draft.document,policy:{...draft.document.policy,viewpointByScene:viewpoints}});
        }}>{characterOptions}</select></label>
        <p className="text-xs">已保存修订 {draft.base.revision} · {draft.edited?'有未保存配置':'配置已保存'}。刷新保留配置草稿；重新载入会放弃本地配置修改。</p>
        <div className="flex flex-wrap gap-2"><Button size="sm" onClick={()=>void save()}>保存隔离配置</Button><Button size="sm" variant="outline" onClick={()=>void reload()}>重新载入服务端</Button></div>
        <details className="space-y-2"><summary className="cursor-pointer text-sm">作者条目：背景、计划与读者假设</summary>
          {draft.document.entries.map((entry,index)=><div key={entry.id} className="rounded border p-2 space-y-2">
            <fieldset disabled={entry.stale} className="space-y-2">
              <label className="block text-xs">条目类型<select className={selectClass} value={entry.kind} onChange={e=>updateEntry(index,{kind:e.target.value as ContextEntry['kind']})}><option value="PLAN">本场计划（尚未发生）</option><option value="BACKGROUND">作者背景设定</option><option value="READER_HYPOTHESIS">读者假设（不进入生成）</option></select></label>
              <label className="block text-xs">条目内容<Textarea maxLength={2000} value={entry.text} onChange={e=>updateEntry(index,{text:e.target.value})}/></label>
              <label className="block text-xs">适用起点（计划仅用于此场）<select className={selectClass} value={entry.fromSceneId} onChange={e=>updateEntry(index,{fromSceneId:e.target.value})}>{sceneOptions}</select></label>
              {entry.kind==='BACKGROUND' && <><p className="text-xs">哪些人物已知此背景：</p>{characters.map(c=><label key={c.id} className="flex gap-2 text-xs"><input type="checkbox" checked={entry.characterIds.includes(c.id)} onChange={e=>updateEntry(index,{characterIds:e.target.checked?[...entry.characterIds,c.id]:entry.characterIds.filter(id=>id!==c.id)})}/>{c.name}</label>)}<label className="flex gap-2 text-xs"><input type="checkbox" checked={entry.narratorVisible} onChange={e=>updateEntry(index,{narratorVisible:e.target.checked})}/>允许全知叙述者使用</label></>}
              <details><summary className="cursor-pointer text-xs">关联前文证据（读者假设必选）</summary>{validRecords.map(r=><label key={r.id} className="flex gap-2 text-xs"><input type="checkbox" checked={entry.dependencyRecordIds.includes(r.id)} onChange={e=>updateEntry(index,{dependencyRecordIds:e.target.checked?[...entry.dependencyRecordIds,r.id]:entry.dependencyRecordIds.filter(id=>id!==r.id)})}/>{r.assertion.statement}</label>)}</details>
            </fieldset>
            {entry.stale && <p className="text-xs">待复核，已停止使用；可补充新条目。</p>}
            <Button size="sm" variant="ghost" onClick={()=>edit({...draft.document,entries:draft.document.entries.filter((_,i)=>i!==index)})}>移除此条目</Button>
          </div>)}
          <Button size="sm" variant="outline" onClick={addEntry}>补充作者条目</Button>
          <StableAttributeEditor characters={characters} sceneId={sceneId} onAdd={entry=>edit({...draft.document,entries:[...draft.document.entries,entry]})}/>
        </details>
        <details className="space-y-2"><summary className="cursor-pointer text-sm">补充已确认记录的知情依据</summary>
          {draft.document.grants.map(g=><div key={g.id} className="rounded border p-2 text-xs"><p>{characters.find(c=>c.id===g.characterId)?.name}：{records.find(r=>r.id===g.recordId)?.assertion.statement || '历史记录'} · {g.stale?'待复核':'作者确认'}</p><p>{g.evidence.map(e=>e.quote).join('；')}</p>{!g.stale && <KnowledgeViewEditor value={g.view} kind={records.find(r=>r.id===g.recordId)?.assertion.kind || 'FACT'} onChange={view=>edit({...draft.document,grants:draft.document.grants.map(v=>v.id===g.id?{...v,view}:v)})}/>}<Button size="sm" variant="ghost" onClick={()=>edit({...draft.document,grants:draft.document.grants.filter(v=>v.id!==g.id)})}>移除此知情标记</Button></div>)}
          {!grant ? <Button size="sm" variant="outline" disabled={!validRecords.length} onClick={()=>setGrant({id:crypto.randomUUID(),recordId:'',characterId:'',approvalId:'',evidence:[{blockId:'',quote:''}],fromSceneId:sceneId,uncertainty:'',stale:false})}>补充知情标记</Button> : <div className="space-y-2">
            <label className="block text-xs">关联陈述<select className={selectClass} value={grant.recordId} onChange={e=>setGrant({...grant,recordId:e.target.value,view:null})}><option value="">选择已确认记录</option>{validRecords.map(r=><option key={r.id} value={r.id}>{r.assertion.statement}</option>)}</select></label>
            <label className="block text-xs">获知人物<select className={selectClass} value={grant.characterId} onChange={e=>setGrant({...grant,characterId:e.target.value})}>{characterOptions}</select></label>
            <label className="block text-xs">获知证据来源<select className={selectClass} value={grant.approvalId} onChange={e=>void selectEvidence(e.target.value)}><option value="">选择已确认原文</option>{records.filter((r,i,a)=>a.findIndex(v=>v.approvalId===r.approvalId)===i).map(r=><option key={r.approvalId} value={r.approvalId}>{r.disclosedAt.sceneTitle} · {r.disclosedAt.chapterTitle}</option>)}</select></label>
            <label className="block text-xs">证据段落<select className={selectClass} value={grant.evidence[0].blockId} onChange={e=>setGrant({...grant,evidence:[{blockId:e.target.value,quote:''}]})}><option value="">选择段落</option>{source?.blocks.map(b=><option key={b.id} value={b.id}>{b.id} · {b.text}</option>)}</select></label>
            <label className="block text-xs">逐字引文<Textarea value={grant.evidence[0].quote} onChange={e=>setGrant({...grant,evidence:[{...grant.evidence[0],quote:e.target.value}]})}/></label>
            <label className="block text-xs">开始知情的场景<select className={selectClass} value={grant.fromSceneId} onChange={e=>setGrant({...grant,fromSceneId:e.target.value})}>{sceneOptions}</select></label>
            <label className="block text-xs">作者审阅说明（不进入人物输入）<Input value={grant.uncertainty} maxLength={500} onChange={e=>setGrant({...grant,uncertainty:e.target.value})}/></label>
            <KnowledgeViewEditor value={grant.view} kind={records.find(r=>r.id===grant.recordId)?.assertion.kind || 'FACT'} onChange={view=>setGrant({...grant,view})}/>
            <Button size="sm" disabled={!grant.recordId || !grant.characterId || !grant.approvalId || !grant.evidence[0].quote} onClick={()=>{edit({...draft.document,grants:[...draft.document.grants,grant]});setGrant(null);}}>加入配置草稿</Button>
            <Button size="sm" variant="ghost" onClick={()=>setGrant(null)}>取消补充</Button>
          </div>}
        </details>
      </fieldset>
      <div className="border-t pt-3 space-y-2">
        <label className="block text-xs">预览视图<select className={selectClass} value={view} onChange={e=>{setView(e.target.value as ContextView);setPreview(null);}}><option value="SCENE">当前场景生成</option><option value="CHARACTER">人物知情</option><option value="READER">读者（截止当前场景之前）</option></select></label>
        {view==='CHARACTER' && <label className="block text-xs">查询人物<select className={selectClass} value={who} onChange={e=>{setWho(e.target.value);setPreview(null);}}>{characterOptions}</select></label>}
        <Button size="sm" variant="outline" disabled={lock || draft.edited || view==='CHARACTER' && !who} onClick={()=>void run(async()=>setPreview(await api.narrativeContext.preview(mid,bid,sceneId,view,who || undefined)))}>预览实际上下文</Button>
        {preview && <div className="space-y-2"><p className="text-xs">采用 {preview.included.length} 项，排除 {preview.excluded.length} 项；预算 {preview.tokenUsed}/{preview.tokenBudget}</p><p className="text-xs break-all">分支 {preview.stamp.branchId} · 正文 {preview.stamp.manuscriptVersion} · 账本 {preview.stamp.canonRevision} · 配置 {preview.stamp.contextRevision}/{preview.stamp.settingsRevision}</p><p className="text-xs break-all">提示词：{preview.promptVersion || '旧预览未记录'} · 上下文 SHA-256：{preview.contextHash}</p><pre className="whitespace-pre-wrap break-words text-xs">{preview.content}</pre><details><summary className="cursor-pointer text-xs">采用与排除依据</summary>{[...preview.included,...preview.excluded].map((f,i)=><p key={i} className="text-xs break-words">{f.category}：{f.reason}{f.content && <span className="block whitespace-pre-wrap">{f.content}</span>}</p>)}</details></div>}
      </div>
      <div className="flex flex-wrap gap-2"><Button size="sm" variant="outline" disabled={working} onClick={()=>void run(async()=>setHistory(await api.narrativeContext.history(mid,bid)))}>查看配置历史</Button><Button size="sm" variant="outline" disabled={working} onClick={()=>void run(async()=>setCandidates(await api.narrativeContext.candidates(mid,bid)))}>查看保留生成稿</Button></div>
      {history.map(h=><details key={h.revision}><summary className="text-xs cursor-pointer">修订 {h.revision} · {new Date(h.createdAt).toLocaleString()}</summary><p className="text-xs">知情补充 {h.document.grants.length} 条，作者条目 {h.document.entries.length} 条</p><pre className="text-xs whitespace-pre-wrap break-words">{JSON.stringify(h.document.policy,null,2)}</pre>{h.document.grants.map(g=><HistoricalKnowledge key={g.id} grant={g} name={characters.find(c=>c.id===g.characterId)?.name}/>)}{h.document.entries.map(e=><p key={e.id} className="text-xs whitespace-pre-wrap">{e.text} · {e.stale?'待复核':'当时有效'}</p>)}</details>)}
      {candidates.map(c=><details key={c.id}><summary className="text-xs cursor-pointer">{new Date(c.createdAt).toLocaleString()} · {c.status==='APPLIED'?'已写入正文':c.status==='LENGTH_REJECTED'?'篇幅未达标，原稿已保留':'保留候选，未写入正文'}</summary><Textarea readOnly value={new DOMParser().parseFromString(c.content,'text/html').body.textContent || ''}/>{c.stampJson && <pre className="text-xs whitespace-pre-wrap break-all">{c.stampJson}</pre>}</details>)}
    </>}
  </details>;
}

function HistoricalKnowledge({grant:g,name}:{grant:KnowledgeGrant;name?:string}) {
  return <div className="rounded border p-2 text-xs space-y-1">
    <p>{name} · {g.evidence.map(e=>e.quote).join('；')} · {g.stale?'待复核':'当时有效'}</p>
    <p>作者审阅说明：{g.uncertainty || '未填写'}</p>
    {g.view ? <><p>当时的人物可用表述：{g.view.content} · {g.view.kind} / {g.view.certainty}</p><p>当时的事件实施者：{g.view.eventActor || '未提供'}</p><p>当时的获知依据：{g.view.acquisitionBasis || '未提供'}</p></> : <p>当时未补充人物可用表述，不进入严格人物输入。</p>}
  </div>;
}
