import { WorldTimelineSnapshot } from './WorldTimeline';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { WorldContent, WorldDetail, WorldDiff, WorldVersion } from '@/types';
import { api } from '@/lib/api-client';
import { localizedErrorMessage } from '@/lib/error-messages';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function WorldChangeList({diff}: {diff: WorldDiff}) {
  const {t} = useTranslation();
  const render = (value: unknown) => {
    if (value == null) return t('world2.absent');
    if (typeof value === 'string') return value || t('worlds.emptyField');
    if (Array.isArray(value)) return value.map(item=>typeof item==='object'?JSON.stringify(item,null,2):String(item)).join('、');
    if (typeof value === 'object') return Object.entries(value).filter(([key]) => !['id','legacyModuleKey','legacyFieldKey'].includes(key))
      .map(([key,item]) => `${t(`world5.${key}`,{defaultValue:t(`world2.${key}`,{defaultValue:key})})}: ${key === 'type' ? t(`world2.types.${item}`,{defaultValue:String(item)}) : item!=null&&typeof item==='object' ? JSON.stringify(item,null,2) : item ?? ''}`).join('\n\n');
    return String(value);
  };
  if (!diff.changes.length) return <p className="py-6 text-sm text-muted-foreground">{t('worlds.sameContent')}</p>;
  return <div className="space-y-3">{diff.changes.map(change => <details key={`${change.kind}:${change.id}`} className="rounded-md border px-4 py-3">
    <summary className="cursor-pointer text-sm"><span className="mr-3 text-xs text-muted-foreground">{t(change.before == null ? 'world2.added' : change.after == null ? 'world2.deleted' : 'world2.modified')}</span>{change.kind === 'METADATA' ? t(`worldMeta.${change.id}`) : change.kind==='TIMELINE'?t(`world5.${change.id}`,{defaultValue:change.label}):change.label}</summary>
    <div className="mt-4 grid gap-4 md:grid-cols-2">{[{label:t('world2.before'),value:change.before},{label:t('world2.after'),value:change.after}].map(item => <section key={item.label} className="min-w-0"><h4 className="mb-2 text-xs font-medium text-muted-foreground">{item.label}</h4><div className="whitespace-pre-wrap break-words rounded bg-muted/30 p-3 text-sm leading-6">{render(item.value)}</div></section>)}</div>
  </details>)}</div>;
}

export function WorldSnapshot({content}: {content: WorldContent}) {
  const {t}=useTranslation();
  return <div className="space-y-6">
    <header className="space-y-2 border-b pb-5"><h3 className="text-xl font-semibold">{content.name}</h3><p className="whitespace-pre-wrap text-sm text-muted-foreground">{content.tagline}</p></header>
    {[['themes',content.themes.join('、')],['creativeIntent',content.creativeIntent],['notes',content.notes],['overview',content.overview]].map(([key,value]) => value && <section key={key}><h4 className="mb-2 text-xs text-muted-foreground">{t(`worldMeta.${key}`)}</h4><p className="whitespace-pre-wrap text-sm leading-7">{value}</p></section>)}
    {content.entries.map(entry => <article className="space-y-2 border-t pt-5" key={entry.id}><span className="text-xs text-muted-foreground">{t(`world2.types.${entry.type}`)}</span><h4 className="font-semibold">{entry.title}</h4>{entry.summary && <p className="whitespace-pre-wrap text-sm text-muted-foreground">{entry.summary}</p>}<p className="whitespace-pre-wrap break-words text-sm leading-7">{entry.content || t('world2.blank')}</p>{!!entry.aliases.length && <p className="text-xs text-muted-foreground">{t('world2.aliases')} · {entry.aliases.join('、')}</p>}{!!entry.tags.length && <p className="text-xs text-muted-foreground">{t('world2.tags')} · {entry.tags.join('、')}</p>}</article>)}
    <WorldTimelineSnapshot timeline={content.timeline} entryTitles={Object.fromEntries(content.entries.map(entry=>[entry.id,entry.title]))}/>
    {!!content.relations.length && <section className="space-y-3 border-t pt-5"><h4 className="font-medium">{t('world2.relations')}</h4>{content.relations.map(r => <p key={r.id} className="whitespace-pre-wrap text-sm leading-7">{content.entries.find(e => e.id === r.sourceId)?.title} → {content.entries.find(e => e.id === r.targetId)?.title} · {r.type}{r.description && `\n${r.description}`}</p>)}</section>}
  </div>;
}

export function WorldPublishDialog({draft,stale,onClose,onPublished,submit}: {
  draft: WorldDetail; stale: boolean; onClose: () => void; onPublished: () => Promise<void>;
  submit?: (request: {expectedRevision: number;releaseNote: string;requestKey: string}) => Promise<unknown>;
}) {
  const {t}=useTranslation();
  const [diff,setDiff]=useState<WorldDiff | null>(null), [error,setError]=useState(''), [busy,setBusy]=useState(false), [note,setNote]=useState('');
  const [key]=useState(()=>crypto.randomUUID());
  const [submitted,setSubmitted]=useState(false);
  const [attempt,setAttempt]=useState(0);
  useEffect(()=>{let current=true;setError('');api.worlds.diff(draft.id,draft.latestPublished?.id,null,draft.revision).then(value=>{if(current)setDiff(value);}).catch(e=>{if(current)setError(localizedErrorMessage(e));});return()=>{current=false;};},[draft.id,draft.revision,draft.latestPublished?.id,attempt]);
  const blanks=(draft.entries || []).filter(e=>!e.content?.trim());
  const emptyBase=['tagline','creativeIntent'] as const;
  const missing=[...emptyBase.filter(k=>!draft[k]?.trim()).map(k=>t(`worldMeta.${k}`)),...(!draft.entries?.length?[t('world2.entries')]:[]),...blanks.map(e=>e.title)];
  return <Dialog open onOpenChange={open=>{if(!open&&!busy)onClose();}}><DialogContent className="max-h-[90dvh] max-w-5xl overflow-y-auto"><DialogHeader><DialogTitle>{t(submit?'world2.publishAndBind':'world2.publishTitle')}</DialogTitle><DialogDescription>{t('world2.publishHelp')}</DialogDescription></DialogHeader>
    {!!missing.length && <p className="rounded border border-amber-300/60 bg-amber-50/40 p-3 text-sm text-amber-800 dark:bg-amber-950/20 dark:text-amber-200">{t('world2.missingHelp',{items:missing.join('、')})}</p>}
    {stale && <p role="alert" className="text-sm text-destructive">{t('worlds.actionStale')}</p>}
    {diff ? <WorldChangeList diff={diff} /> : !error && <p>{t('common.loading')}</p>}
    <label className="space-y-2 text-sm">{t('world2.releaseNote')}<Textarea value={note} disabled={busy||submitted} onChange={e=>setNote(e.target.value)} /></label>
    {error && <div role="alert" className="space-y-2 text-sm text-destructive"><p>{error}</p>{!diff && <Button variant="outline" onClick={()=>setAttempt(n=>n+1)}>{t('common.retry')}</Button>}</div>}
    <DialogFooter><Button variant="outline" disabled={busy} onClick={onClose}>{t('common.cancel')}</Button><Button disabled={busy||stale||!diff} onClick={async()=>{
      setBusy(true);setSubmitted(true);setError('');try{const input={expectedRevision:draft.revision,releaseNote:note,requestKey:key};await (submit?submit(input):api.worlds.publish(draft.id,input.expectedRevision,input.releaseNote,key));await onPublished();onClose();}catch(e){setError(localizedErrorMessage(e));}finally{setBusy(false);}
    }}>{busy?t('common.loading'):t(submit?'world2.publishAndBind':'world2.confirmPublish')}</Button></DialogFooter>
  </DialogContent></Dialog>;
}

export function WorldVersionHistory({worldId,revision}: {worldId:string;revision:number}) {
  const {t}=useTranslation();const [versions,setVersions]=useState<WorldVersion[]>([]),[selected,setSelected]=useState<WorldVersion|null>(null),[error,setError]=useState('');
  const [attempt,setAttempt]=useState(0),[loading,setLoading]=useState(false);
  useEffect(()=>{let current=true;setLoading(true);setSelected(null);setError('');api.worlds.versions(worldId).then(v=>{if(current)setVersions(v);}).catch(e=>{if(current)setError(localizedErrorMessage(e));}).finally(()=>{if(current)setLoading(false);});return()=>{current=false;};},[worldId,revision,attempt]);
  useEffect(()=>{if(!selected||selected.content)return;let current=true;api.worlds.version(worldId,selected.id).then(v=>{if(current)setSelected(v);}).catch(e=>{if(current)setError(localizedErrorMessage(e));});return()=>{current=false;};},[worldId,selected]);
  return <section className="space-y-4"><div><h2 className="font-semibold">{t('world2.history')}</h2><p className="mt-1 text-sm text-muted-foreground">{t('world2.historyHelp')}</p></div>
    {error && <p role="alert" className="text-sm text-destructive">{error} <Button variant="ghost" onClick={()=>setAttempt(n=>n+1)}>{t('common.retry')}</Button></p>}
    {loading && <p>{t('common.loading')}</p>}{!loading&&!versions.length && <p className="py-8 text-sm text-muted-foreground">{t('world2.noVersions')}</p>}
    <div className="divide-y rounded-md border">{versions.map(v=><button key={v.id} className="flex w-full flex-wrap items-center justify-between gap-3 p-4 text-left hover:bg-muted/40" onClick={()=>setSelected(v)}><span><span className="font-medium">{v.origin==='MIGRATION_BASELINE'?t('world2.baseline',{version:v.legacyVersion||''}):t('world2.version',{number:v.sequenceNumber})}</span><span className="mt-1 block text-sm text-muted-foreground">{v.releaseNote || t('world2.noNote')}</span></span><time className="text-xs text-muted-foreground">{new Date(v.createdAt).toLocaleString()}</time></button>)}</div>
    <Dialog open={!!selected} onOpenChange={open=>{if(!open)setSelected(null);}}><DialogContent className="max-h-[90dvh] max-w-4xl overflow-y-auto"><DialogHeader><DialogTitle>{selected?.origin==='MIGRATION_BASELINE'?t('world2.baseline',{version:selected.legacyVersion||''}):t('world2.version',{number:selected?.sequenceNumber})}</DialogTitle><DialogDescription>{t('world2.historyHelp')}</DialogDescription></DialogHeader>{selected?.content?<WorldSnapshot content={selected.content}/>:<p>{t('common.loading')}</p>}</DialogContent></Dialog>
  </section>;
}
