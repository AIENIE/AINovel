import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, isApiError } from '@/lib/api-client';
import type { WorldDetail, WorldVersion } from '@/types';
import { localizedErrorMessage } from '@/lib/error-messages';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import type { WorldEditorState } from '../useWorldDiscussion';

export function WorldManageDialog({worldId,editor,onClose,onCopied}:{worldId:string;editor?:WorldEditorState;onClose:()=>void;onCopied?:()=>void}) {
  const {t}=useTranslation();const [draft,setDraft]=useState<WorldDetail|null>(null),[versions,setVersions]=useState<WorldVersion[]>([]),[source,setSource]=useState('DRAFT'),[name,setName]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[copied,setCopied]=useState<WorldDetail|null>(null),[attempt,setAttempt]=useState(0),[downloaded,setDownloaded]=useState(false);
  const [sourceName,setSourceName]=useState<{id:string;name:string}|null>(null),[needsRefresh,setNeedsRefresh]=useState(false);
  const pending=useRef<Parameters<typeof api.worlds.copy>[1]|null>(null),lock=useRef(false),mounted=useRef(true);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{let current=true;setError('');Promise.all([api.worlds.getDetail(worldId),api.worlds.versions(worldId)]).then(([value,history])=>{if(!current)return;setDraft(value);setVersions(history.filter(version=>version.origin==='PUBLISHED'));setName(value.name+t('world5.copySuffix'));}).catch(e=>{if(current)setError(localizedErrorMessage(e));});return()=>{current=false;};},[worldId,attempt,t]);
  useEffect(()=>{let current=true;if(source==='DRAFT'){setName((draft?.name||'')+t('world5.copySuffix'));return;}
    setSourceName(null);api.worlds.version(worldId,source).then(version=>{if(!current)return;if(!version.content)throw new Error('WORLD_SOURCE_REFERENCE_INVALID');setSourceName({id:source,name:version.content.name});setName(version.content.name+t('world5.copySuffix'));}).catch(e=>{if(current)setError(localizedErrorMessage(e));});return()=>{current=false;};
  },[source,worldId,draft?.name,t]);
  const sourceReady=source==='DRAFT'||sourceName?.id===source;
  const unsaved=!!editor&&!editor.canAct();
  const stale=!!editor&&!!draft&&editor.draft?.revision!==draft.revision;
  const perform=async(action:'copy'|'MARKDOWN'|'JSON')=>{
    if(lock.current||!draft||unsaved||stale||!sourceReady||needsRefresh)return;lock.current=true;setBusy(true);setError('');
    const origin=source==='DRAFT'?{source:'DRAFT' as const,expectedRevision:draft.revision}:{source:'VERSION' as const,versionId:source};
    try{if(action==='copy'){
      pending.current??={...origin,name,requestKey:crypto.randomUUID()};const value=await api.worlds.copy(worldId,pending.current);if(mounted.current){setCopied(value);onCopied?.();}
    }else{
      const value=await api.worlds.export(worldId,{...origin,format:action});if(!mounted.current)return;
      const url=URL.createObjectURL(value.blob),link=document.createElement('a');link.href=url;link.download=value.fileName||`${draft.name}.${action==='JSON'?'json':'md'}`;document.body.appendChild(link);link.click();link.remove();window.setTimeout(()=>URL.revokeObjectURL(url),1000);setDownloaded(true);
    }}catch(e){if(mounted.current){setError(localizedErrorMessage(e));if(isApiError(e)&&e.status>=400&&e.status<500&&e.status!==408){pending.current=null;setNeedsRefresh(true);}}}finally{lock.current=false;if(mounted.current)setBusy(false);}
  };
  return <Dialog open onOpenChange={open=>{if(!open&&!busy)onClose();}}><DialogContent className="max-h-[90dvh] overflow-y-auto"><DialogHeader><DialogTitle>{t('world5.manage')}</DialogTitle><DialogDescription>{t('world5.copyHelp')}</DialogDescription></DialogHeader>
    {!draft&&!error&&<p>{t('common.loading')}</p>}
    {draft&&<div className="space-y-5"><label className="block space-y-2 text-sm">{t('world5.source')}<select className="w-full rounded border bg-background p-2" disabled={busy||!!pending.current} value={source} onChange={e=>setSource(e.target.value)}><option value="DRAFT">{t('world5.savedDraft')} · r{draft.revision}</option>{versions.map(version=><option key={version.id} value={version.id}>{version.origin==='MIGRATION_BASELINE'?t('world2.migrationBaseline'):t('world2.version',{number:version.sequenceNumber})} · {version.releaseNote}</option>)}</select></label>
      {unsaved&&<div role="status" className="space-y-2 rounded border p-3 text-sm"><p>{t('world5.saveRequired')}</p><Button variant="outline" disabled={busy||editor?.saving||!!editor?.remote||editor?.conflict} onClick={async()=>{if(await editor?.save())setAttempt(value=>value+1);}}>{t('world5.saveContinue')}</Button></div>}
      {stale&&!unsaved&&<p role="alert" className="text-sm text-destructive">{t('world5.sourceStale')}</p>}
      {(needsRefresh||stale)&&<Button variant="outline" disabled={busy||unsaved} onClick={()=>{pending.current=null;setNeedsRefresh(false);setDraft(null);setCopied(null);setSource('DRAFT');setAttempt(value=>value+1);}}>{t('world5.refreshSource')}</Button>}
      <label className="block space-y-2 text-sm">{t('world5.copyName')}<Input value={name} disabled={busy||!!pending.current||!sourceReady} onChange={e=>setName(e.target.value)}/></label><Button disabled={busy||unsaved||stale||!sourceReady||needsRefresh||!name.trim()||!!copied} onClick={()=>void perform('copy')}>{t('world5.copy')}</Button>
      {copied&&<p role="status" className="text-sm">{t('world5.copied')} · {copied.name} <Link className="underline" to={`/world-editor?id=${copied.id}`} onClick={onClose}>{t('world5.openCopy')}</Link></p>}
      <div className="space-y-3 border-t pt-4"><p className="text-sm text-muted-foreground">{t('world5.exportHelp')}</p><div className="flex flex-wrap gap-2"><Button variant="outline" disabled={busy||unsaved||stale||!sourceReady||needsRefresh} onClick={()=>void perform('MARKDOWN')}>{t('world5.exportMarkdown')}</Button><Button variant="outline" disabled={busy||unsaved||stale||!sourceReady||needsRefresh} onClick={()=>void perform('JSON')}>{t('world5.exportJson')}</Button></div>{downloaded&&<p role="status" className="text-sm">{t('world5.downloaded')}</p>}</div>
    </div>}
    {error&&<p role="alert" className="text-sm text-destructive">{error}{!draft&&<Button variant="outline" onClick={()=>setAttempt(value=>value+1)}>{t('common.retry')}</Button>}</p>}
    <DialogFooter><Button variant="outline" disabled={busy} onClick={onClose}>{t('common.cancel')}</Button></DialogFooter>
  </DialogContent></Dialog>;
}
