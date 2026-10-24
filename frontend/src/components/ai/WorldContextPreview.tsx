import { useEffect, useState, useSyncExternalStore } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api-client';
import { localizedErrorMessage } from '@/lib/error-messages';
import { finishWorldPreview, pendingWorldPreview, subscribeWorldPreview } from '@/lib/world-context-preview';
import type { WorldChoice, WorldSelection } from '@/lib/api/domains/world-context';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function WorldSources({selection, onChoice,reviewedHashes,choiceDisabled=false}:{selection:WorldSelection;onChoice?:(id:string,value:WorldChoice)=>void;reviewedHashes?:Record<string,string>;choiceDisabled?:boolean}) {
  const {t}=useTranslation();
  const included=new Set(selection.included.map(source=>source.id));
  return <div className="space-y-3 text-sm">
    <p className="font-medium">{selection.binding ? `${selection.binding.name} · v${selection.binding.sequenceNumber} · ${t(`world4.${selection.binding.sourceType}`)}` : t('world4.unbound')}</p>
    <p className="text-muted-foreground">{t('world4.capacity',{used:selection.tokenUsed,limit:selection.tokenBudget})}</p>
    {selection.issues.map(issue=><p role="alert" className="text-destructive break-words" key={issue}>{t(`world4.${issue.split(':')[0]}`,{defaultValue:issue})}</p>)}
    {[...selection.included,...selection.excluded].map(source=><details key={source.id} className="rounded border p-3">
      <summary className="cursor-pointer break-words">{source.title} · {t(included.has(source.id)?'world4.included':'world4.excluded')}</summary>
      {reviewedHashes?.[source.id]&&reviewedHashes[source.id]!==source.contentHash&&<p className="my-2 text-amber-700">{t('world4.sourceChanged')}</p>}
      <p className="my-2 text-muted-foreground">{t(`world4.${source.reason.split(':')[0]}`,{defaultValue:source.reason})}{source.reason.includes(':')?` · ${source.reason.substring(source.reason.indexOf(':')+1)}`:''} · {source.estimatedTokens} tokens</p>
      {onChoice&&<label className="flex flex-wrap items-center gap-2">{t('world4.choice')}
        <select disabled={choiceDisabled} className="rounded border bg-background p-2" aria-label={`${source.title} ${t('world4.choice')}`} value={source.choice} onChange={e=>onChoice(source.id,e.target.value as WorldChoice)}>
          {(['AUTO','INCLUDE','EXCLUDE'] as const).map(choice=><option key={choice} value={choice}>{t(`world4.${choice}`)}</option>)}
        </select></label>}
      <p className="my-2 text-xs text-muted-foreground">{t(source.type==='AUTHOR_NOTES'?'world4.authorNotes':'world4.versionText')}</p>
      <pre className="whitespace-pre-wrap break-words font-sans">{source.content}</pre>
      <details className="mt-2 text-xs text-muted-foreground"><summary>{t('world4.technical')}</summary><p className="break-all">{source.id}<br/>{source.contentHash}</p></details>
    </details>)}
    <details className="text-xs text-muted-foreground"><summary>{t('world4.technical')}</summary><p className="break-all">{selection.compilerVersion}<br/>{selection.contextHash}</p></details>
  </div>;
}
export function WorldContextPreview() {
  const pending=useSyncExternalStore(subscribeWorldPreview,pendingWorldPreview);const {t}=useTranslation();
  const [value,setValue]=useState(pending?.preview);const [busy,setBusy]=useState(false);const [error,setError]=useState('');
  useEffect(()=>{setValue(pending?.preview);setBusy(false);setError('');},[pending]);
  const change=async(id:string,choice:WorldChoice)=>{
    if(!pending||!value||busy)return;setBusy(true);setError('');
    try {await api.worldContext.save(pending.path,value,{...value.preferences.choices,[id]:choice});setValue(await api.worldContext.preview(pending.path,pending.request));}
    catch(e){setError(localizedErrorMessage(e));}finally{setBusy(false);}
  };
  const refresh=async()=>{if(!pending||busy)return;setBusy(true);try{setValue(await api.worldContext.preview(pending.path,pending.request));setError('');}catch(e){setError(localizedErrorMessage(e));}finally{setBusy(false);}};
  const acknowledge=async()=>{if(!pending||!value||busy)return;setBusy(true);try{await api.worldContext.save(pending.path,value,value.preferences.choices);setValue(await api.worldContext.preview(pending.path,pending.request));setError('');}catch(e){setError(localizedErrorMessage(e));}finally{setBusy(false);}};
  const known=new Set([...(value?.selection?.included??[]),...(value?.selection?.excluded??[])].map(s=>s.id));
  const missing=Object.entries(value?.preferences.choices??{}).filter(([id])=>!known.has(id));
  return <Dialog open={!!pending} onOpenChange={open=>{if(!open&&pending&&!busy)finishWorldPreview(pending);}}>
    <DialogContent className="max-w-3xl max-h-[90dvh] overflow-y-auto">
      <DialogHeader><DialogTitle>{t('world4.preview')}</DialogTitle><DialogDescription>{t('world4.help')}</DialogDescription></DialogHeader>
      {value?.selection&&<WorldSources selection={value.selection} reviewedHashes={value.preferences.reviewedHashes} choiceDisabled={busy} onChoice={(id,choice)=>void change(id,choice)}/>}
      {!!value?.preferences.reviewedVersionId&&value.preferences.reviewedVersionId!==value.selection?.binding?.versionId&&<div className="rounded border p-3 text-sm"><p>{t('world4.versionChanged')}</p><Button variant="outline" size="sm" disabled={busy} onClick={()=>void acknowledge()}>{t('world4.reviewVersion')}</Button></div>}
      {missing.map(([id])=><div className="break-all text-sm" key={id}>{t('world4.missing')} {id} <Button variant="outline" size="sm" disabled={busy} onClick={()=>void change(id,'AUTO')}>{t('world4.resetMissing')}</Button></div>)}
      {!!value?.compiledContext&&<details><summary>{t('world4.sent')}</summary><pre className="whitespace-pre-wrap break-words text-xs">{value.compiledContext}</pre></details>}
      {!!value?.firstSources?.length&&<details><summary>{t('world4.firstStep')}</summary>{value.firstSources.map(s=><div key={s.id} className="my-2 text-sm"><b>{s.title}</b><p className="whitespace-pre-wrap break-words">{s.content}</p></div>)}</details>}
      {!!value?.availableSources?.length&&<details><summary>{t('world4.available')}</summary>{value.availableSources.map(s=><details key={s.id} className="my-2 text-sm"><summary>{s.title}</summary><p className="whitespace-pre-wrap break-words">{s.content}</p></details>)}</details>}
      {!!error&&<p role="alert" className="text-destructive">{error}</p>}
      <DialogFooter><Button variant="outline" disabled={busy} onClick={()=>void refresh()}>{t('world4.refresh')}</Button><Button variant="outline" disabled={busy} onClick={()=>pending&&finishWorldPreview(pending)}>{t('common.cancel')}</Button><Button disabled={busy||!!error||!!value?.selection?.issues.length||!value} onClick={()=>pending&&value&&finishWorldPreview(pending,value)}>{t('world4.confirm')}</Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
