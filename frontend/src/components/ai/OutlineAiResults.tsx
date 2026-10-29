import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api-client';
import type { Outline } from '@/types';
import { outlineContextPath, type OutlineAiResult } from '@/lib/api/domains/world-context';
import { reviewWorldPreview } from '@/lib/world-context-preview';
import { WorldSources } from './WorldContextPreview';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { localizedErrorMessage } from '@/lib/error-messages';

export function OutlineAiResults({outline,revision,dirty,onAdopt,onUpdated}:{outline:Outline;revision:number;dirty:boolean;onAdopt:(result:OutlineAiResult,text:string)=>void;onUpdated:(id:string)=>Promise<void>}) {
  const {t}=useTranslation();const [results,setResults]=useState<OutlineAiResult[]>([]);const [error,setError]=useState('');const [busy,setBusy]=useState(false);const [candidates,setCandidates]=useState<Record<string,string>>({});
  const resultTitle=(result:OutlineAiResult)=>result.action==='CHAPTER_GENERATE'
    ? result.candidate?.title||t('outline.generateNextChapter')
    : `${t(`world4.${result.action}`)} · ${outline.chapters.flatMap(chapter=>[chapter,...chapter.scenes]).find(node=>node.id===result.nodeId)?.title||result.nodeId}`;
  const refresh=async()=>{try{setResults(await api.worldContext.outlineResults(outline.id));setError('');}catch(e){setError(localizedErrorMessage(e));}};
  useEffect(()=>{let active=true;setResults([]);setCandidates({});void api.worldContext.outlineResults(outline.id).then(rows=>{if(active)setResults(rows);}).catch(e=>{if(active)setError(localizedErrorMessage(e));});return ()=>{active=false;};},[outline.id,revision]);
  const append=async(result:OutlineAiResult)=>{
    if(dirty||busy)return;setBusy(true);setError('');
    try {const current=await api.outlines.get(outline.id),path=outlineContextPath(outline.id),request={action:'CHAPTER_GENERATE',chapterNumber:result.chapterNumber,sectionsPerChapter:result.scenesCount,instruction:result.instruction,expectedRevision:current.revision};
      const preview=await reviewWorldPreview(path,request,await api.worldContext.preview(path,request));
      await api.worldContext.acceptOutline(outline.id,result,current.revision??0,preview.contextHash,crypto.randomUUID());await onUpdated(outline.id);await refresh();
    }catch(e){if(e instanceof Error&&e.message==='WORLD_CONTEXT_CANCELLED')return;setError(localizedErrorMessage(e));}finally{setBusy(false);}
  };
  return <details className="mt-4 rounded border p-3"><summary className="cursor-pointer font-medium">{t('world4.results')}</summary>
    <Button size="sm" variant="outline" className="my-3" disabled={busy} onClick={()=>void refresh()}>{t('world4.refreshResults')}</Button>
    {!!error&&<p role="alert" className="text-destructive">{error}</p>}{!results.length&&<p className="text-sm text-muted-foreground">{t('world4.emptyResults')}</p>}
    {results.map(result=><details key={result.id} className="my-2 rounded border p-3 text-sm"><summary className="cursor-pointer">{resultTitle(result)} · {t(`world4.${result.status}`,{defaultValue:result.status})}</summary>
      <p className="my-2 text-muted-foreground">{result.createdAt}</p>
      {result.sourceRevision!==outline.revision&&result.status!=='APPLIED'&&<p className="my-2 text-amber-700">{t('world4.stale')}</p>}
      {!!result.beforeText&&<details><summary>{t('world4.before')}</summary><p className="whitespace-pre-wrap">{result.beforeText}</p></details>}
      {result.candidate?.text!==undefined?<><label>{t('world4.candidate')}<Textarea className="my-2 min-h-36" value={candidates[result.id]??result.candidate.text} onChange={e=>setCandidates(current=>({...current,[result.id]:e.target.value}))}/></label><Button size="sm" disabled={busy||!String(candidates[result.id]??result.candidate.text).trim()} onClick={()=>onAdopt(result,candidates[result.id]??result.candidate?.text??'')}>{t('world4.adopt')}</Button></>:<pre className="my-2 whitespace-pre-wrap break-words font-sans">{result.candidate?JSON.stringify(result.candidate,null,2):result.rawResult}</pre>}
      {result.action==='CHAPTER_GENERATE'&&result.status==='READY'&&<Button size="sm" disabled={busy||dirty} onClick={()=>void append(result)}>{t('world4.append')}</Button>}
      <details className="mt-3"><summary>{t(result.rawResult?'world4.actual':'world4.frozen')}</summary><WorldSources selection={result.worldContext}/></details>
    </details>)}
  </details>;
}
