import { StoryWorldEras } from './StoryWorldEras';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { World, WorldBindingScope, WorldBindingView, WorldDiff, WorldVersion } from '@/types';
import { api, ApiError } from '@/lib/api-client';
import { localizedErrorMessage } from '@/lib/error-messages';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { WorldChangeList } from './WorldVersionViews';

const selectClass='h-10 min-w-0 rounded-md border border-input bg-background px-3 text-sm';
export function WorldBindingPanel({scope,resourceId,storyId,onChanged}: {scope:WorldBindingScope;resourceId:string;storyId?:string;onChanged?:()=>void}) {
  const {t}=useTranslation();
  const [view,setView]=useState<WorldBindingView|null>(null),[worlds,setWorlds]=useState<World[]>([]),[versions,setVersions]=useState<WorldVersion[]>([]);
  const [worldId,setWorldId]=useState(''),[target,setTarget]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[attempt,setAttempt]=useState(0),[stale,setStale]=useState(false);
  const [review,setReview]=useState<{diff:WorldDiff;view:WorldBindingView;target:string}|null>(null);
  const [override,setOverride]=useState<{resourceType:WorldBindingScope;resourceId:string;title:string}|null>(null);
  useEffect(()=>{let current=true;setView(null);setReview(null);setError('');setBusy(false);setStale(false);
    Promise.all([api.worldBindings.get(scope,resourceId),scope==='STORY'?api.worlds.list():Promise.resolve([])]).then(([binding,items])=>{
      if(!current)return;setView(binding);setWorlds(items);setWorldId(binding.effective?.worldId||'');
    }).catch(e=>{if(current)setError(localizedErrorMessage(e));});return()=>{current=false;};
  },[scope,resourceId,attempt]);
  useEffect(()=>{let current=true;setVersions([]);setTarget('');setReview(null);if(!worldId)return;
    api.worlds.versions(worldId).then(items=>{if(!current)return;const published=items.filter(v=>v.origin==='PUBLISHED');setVersions(published);
      setTarget(published.find(v=>v.id!==view?.effective?.versionId && (worldId!==view?.effective?.worldId || v.sequenceNumber>view.effective.sequenceNumber))?.id||'');
    }).catch(e=>{if(current)setError(localizedErrorMessage(e));});return()=>{current=false;};
  },[worldId,view]);
  const currentName=view?.effective ? `${view.effective.name} · ${view.effective.origin==='MIGRATION_BASELINE'?t('world2.migrationBaseline'):t('world2.version',{number:view.effective.sequenceNumber})}` : t('world2.unbound');
  const reload=()=>setAttempt(n=>n+1);
  return <section className="space-y-4 rounded-lg border p-4 md:p-5" aria-label={t('world2.binding')}>
    <div><h3 className="text-sm font-semibold">{t('world2.binding')}</h3><p className="mt-2 text-sm">{currentName}</p>
      {view?.effective && <p className="mt-1 text-xs text-muted-foreground">{t('world2.sourceLevel',{source:t(`world2.scope.${view.effective.sourceType}`)})}{!view.explicit && ` · ${t('world2.inherited')}`}</p>}
      {view?.effective?.unavailableReason && <p role="alert" className="mt-2 text-sm text-destructive">{t('world2.bindingUnavailable')}</p>}
    </div>
    {!view&&!error && <p className="text-sm text-muted-foreground">{t('common.loading')}</p>}
    {view && (scope==='STORY'||view.explicit) && <div className="space-y-3 border-t pt-4">
      <p className="text-xs leading-6 text-muted-foreground">{t('world2.bindingHelp')}</p>
      <div className="flex flex-wrap gap-2">
        {scope==='STORY' && <select className={`${selectClass} flex-1`} aria-label={t('world2.chooseWorld')} value={worldId} disabled={busy} onChange={e=>{setWorldId(e.target.value);setError('');}}><option value="">{t('world2.chooseWorld')}</option>{worlds.map(w=><option value={w.id} key={w.id}>{w.name}</option>)}</select>}
        <select className={`${selectClass} flex-1`} aria-label={t('world2.chooseVersion')} value={target} disabled={busy||!worldId} onChange={e=>{setTarget(e.target.value);setReview(null);}}><option value="">{t('world2.chooseVersion')}</option>{versions.map(v=><option value={v.id} key={v.id} disabled={v.id===view.effective?.versionId}>{t('world2.version',{number:v.sequenceNumber})}{v.id===view.effective?.versionId?` · ${t('world2.currentVersion')}`:''}{v.releaseNote?` · ${v.releaseNote}`:''}</option>)}</select>
        <Button variant="outline" disabled={busy||stale||!target} onClick={async()=>{setBusy(true);setError('');try{const diff=await api.worldBindings.diff(scope,resourceId,target);if(diff.fromVersionId!==(view.effective?.versionId??null)){setStale(true);throw new ApiError(409,"WORLD_BINDING_CONFLICT");}setReview({diff,view,target});}catch(e){setError(localizedErrorMessage(e));}finally{setBusy(false);}}}>{t('world2.reviewUpgrade')}</Button>
      </div>
      {worldId&&!versions.length && <p className="text-sm text-muted-foreground">{t('world2.noPublishedVersions')} <Link className="underline" to={`/world-editor?id=${worldId}`}>{t('world2.openWorld')}</Link></p>}
    </div>}
    {view && !view.explicit && scope!=='STORY' && <p className="text-sm text-muted-foreground">{t('world2.inheritHelp')}</p>}
    {view?.explicit && <Button variant="ghost" size="sm" disabled={busy} onClick={async()=>{
      if(!window.confirm(t(scope==='STORY'?'world2.unbindConfirm':'world2.inheritConfirm')))return;
      setBusy(true);setError('');try{await api.worldBindings.change(scope,resourceId,{fromVersionId:view.explicitVersionId,toVersionId:null,expectedBindingRevision:view.bindingRevision});reload();onChanged?.();}catch(e){setError(localizedErrorMessage(e));}finally{setBusy(false);}
    }}>{t(scope==='STORY'?'world2.unbind':'world2.useInheritance')}</Button>}
    {!!view?.overrides.length && <div className="space-y-2 border-t pt-4"><h4 className="text-xs font-medium text-muted-foreground">{t('world2.overrides')}</h4>{view.overrides.map(item=><div className="flex flex-wrap items-center justify-between gap-2" key={`${item.resourceType}:${item.resourceId}`}><div><p className="text-sm">{item.title} · {t(`world2.scope.${item.resourceType}`)}</p><p className="text-xs text-muted-foreground">{item.effective?.name} · {item.effective?.origin==='MIGRATION_BASELINE'?t('world2.migrationBaseline'):t('world2.version',{number:item.effective?.sequenceNumber})}</p></div><Button variant="outline" size="sm" onClick={()=>setOverride(item)}>{t('world2.manageOverride')}</Button></div>)}</div>}
    {view?.effective&&(scope==="STORY"||storyId)&&<StoryWorldEras key={`${scope=== "STORY"?resourceId:storyId}:${view.effective.worldId}:${view.effective.versionId}:${attempt}`} storyId={scope==="STORY"?resourceId:storyId!} effective={view.effective} editable={scope==="STORY"} onRefreshBinding={reload}/>}
    {error && <div role="alert" className="space-y-2 text-sm text-destructive"><p>{error}</p><Button variant="outline" onClick={reload}>{t('world2.refreshBinding')}</Button></div>}
    <Dialog open={!!review} onOpenChange={open=>{if(!open&&!busy)setReview(null);}}><DialogContent className="max-h-[90dvh] max-w-5xl overflow-y-auto"><DialogHeader><DialogTitle>{t('world2.reviewUpgrade')}</DialogTitle><DialogDescription>{t('world2.upgradeHelp')}</DialogDescription></DialogHeader>
      {review && <><p className="text-sm">{review.view.effective?.name||t('world2.unbound')} → {worlds.find(w=>w.id===worldId)?.name||view?.effective?.name} · {t('world2.version',{number:versions.find(v=>v.id===review.target)?.sequenceNumber})}</p><WorldChangeList diff={review.diff}/></>}
      <DialogFooter><Button variant="outline" disabled={busy} onClick={()=>setReview(null)}>{t('common.cancel')}</Button><Button disabled={busy||!review} onClick={async()=>{
        if(!review)return;setBusy(true);setError('');try{await api.worldBindings.change(scope,resourceId,{fromVersionId:review.view.explicitVersionId,toVersionId:review.target,expectedBindingRevision:review.view.bindingRevision});setReview(null);reload();onChanged?.();}catch(e){setReview(null);if(e instanceof ApiError&&e.status===409)setStale(true);setError(localizedErrorMessage(e));}finally{setBusy(false);}
      }}>{t('world2.confirmUpgrade')}</Button></DialogFooter>
    </DialogContent></Dialog>
    <Dialog open={!!override} onOpenChange={open=>{if(!open)setOverride(null);}}><DialogContent className="max-h-[90dvh] max-w-3xl overflow-y-auto"><DialogHeader><DialogTitle>{override?.title}</DialogTitle><DialogDescription>{t('world2.overrideHelp')}</DialogDescription></DialogHeader>{override && <WorldBindingPanel key={override.resourceId} scope={override.resourceType} resourceId={override.resourceId} storyId={scope==="STORY"?resourceId:storyId} onChanged={reload}/>}</DialogContent></Dialog>
  </section>;
}
