import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api-client';
import { localizedErrorMessage } from '@/lib/error-messages';
import type { StoryWorldEras as EraView, WorldBindingReference, WorldContent } from '@/types';
import { Button } from '@/components/ui/button';
import { WorldTimelineSnapshot } from './WorldTimeline';
import { formatWorldDate } from '../world-timeline';

export function StoryWorldEras({storyId,effective,editable,onRefreshBinding}:{storyId:string;effective:WorldBindingReference;editable:boolean;onRefreshBinding?:()=>void}) {
  const {t}=useTranslation();const [view,setView]=useState<EraView|null>(null),[content,setContent]=useState<WorldContent|null>(null),[ids,setIds]=useState<string[]>([]),[error,setError]=useState(''),[busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[saved,setSaved]=useState(false),[attempt,setAttempt]=useState(0),[all,setAll]=useState(false);
  const currentIds=useRef(ids);currentIds.current=ids;const mounted=useRef(true),lock=useRef(false),generation=useRef(0);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{let current=true;const lifecycle=generation;lifecycle.current++;setBusy(false);lock.current=false;setLoading(true);setView(null);setContent(null);setError('');setSaved(false);
    Promise.all([api.worldBindings.eras(storyId),api.worlds.version(effective.worldId,effective.versionId)]).then(([value,version])=>{if(current){setView(value);setIds(value.eraIds);setContent(version.content);}}).catch(e=>{if(current)setError(localizedErrorMessage(e));}).finally(()=>{if(current)setLoading(false);});return()=>{current=false;lifecycle.current++;};
  },[storyId,effective.worldId,effective.versionId,effective.bindingRevision,attempt]);
  const applicable=view?.worldId===effective.worldId;
  const bindingChanged=editable&&!!view&&(!applicable||view.worldVersionId!==effective.versionId||view.bindingRevision!==effective.bindingRevision);
  const eras=editable?view?.eras||[]:content?.timeline?.eras||[];
  const options=editable?[...eras,...(view?.eraIds.filter(id=>!eras.some(era=>era.id===id))||[]).map(id=>view?.reviewedEras?.find(era=>era.id===id)||{id,name:id,description:'',date:null,sortOrder:0})]:ids.map(id=>eras.find(era=>era.id===id)||view?.reviewedEras?.find(era=>era.id===id)||{id,name:id,description:'',date:null,sortOrder:0});
  const save=async()=>{if(lock.current||!view?.worldId||!view.worldVersionId||bindingChanged)return;lock.current=true;setBusy(true);setError('');setSaved(false);const submitted=[...currentIds.current],scope=generation.current;
    try{const next=await api.worldBindings.saveEras(storyId,{worldId:view.worldId,worldVersionId:view.worldVersionId,expectedBindingRevision:view.bindingRevision,expectedRevision:view.revision,eraIds:submitted});if(mounted.current&&generation.current===scope){setView(next);if(JSON.stringify(currentIds.current)===JSON.stringify(submitted))setIds(next.eraIds);setSaved(true);}}
    catch(e){if(mounted.current&&generation.current===scope)setError(localizedErrorMessage(e));}finally{if(generation.current===scope){lock.current=false;if(mounted.current)setBusy(false);}}
  };
  return <section className="space-y-3 border-t pt-4" aria-label={t('world5.storyEras')}><h4 className="text-sm font-medium">{t('world5.storyEras')}</h4><p className="text-xs leading-6 text-muted-foreground">{t('world5.storyErasHelp')}</p>
    {loading&&<p className="text-sm">{t('common.loading')}</p>}
    {view&&!applicable&&!editable&&<p className="text-sm text-muted-foreground">{t('world5.notApplicable')}</p>}
    {bindingChanged&&<div role="alert" className="space-y-2 text-sm text-destructive"><p>{t('world5.erasConflict')}</p><Button variant="outline" disabled={busy||!onRefreshBinding} onClick={onRefreshBinding}>{t('world5.refreshBinding')}</Button></div>}
    {view&&applicable&&!bindingChanged&&<>
      {!!view.changedEraIds.length&&editable&&<p role="status" className="text-sm text-amber-700">{t('world5.erasChanged')}</p>}
      {!!view.missingEraIds.length&&editable&&<p role="status" className="text-sm text-amber-700">{t('world5.erasMissing')}</p>}
      {!options.length&&<p className="text-sm text-muted-foreground">{t(editable?'world5.noEras':'world5.unselectedEras')}</p>}
      <div className="space-y-2">{options.map(era=><label className="flex items-start gap-2 text-sm" key={era.id}>{editable&&<input type="checkbox" className="mt-1" checked={ids.includes(era.id)} onChange={e=>{setSaved(false);setIds(current=>e.target.checked?[...current,era.id]:current.filter(id=>id!==era.id));}}/>}<span className="break-words">{era.name} · {formatWorldDate(era.date,t)}{!eras.some(item=>item.id===era.id)&&<span className="block text-xs text-amber-700">{t('world5.unavailableEra')}</span>}</span></label>)}</div>
      {editable?<Button variant="outline" size="sm" disabled={busy||loading||ids.some(id=>!eras.some(era=>era.id===id))} onClick={()=>void save()}>{t('world5.saveEras')}</Button>:<p className="text-xs text-muted-foreground">{t('world5.eraReadOnly')}</p>}
      {saved&&<p role="status" className="text-xs">{t('world5.erasSaved')}{JSON.stringify(ids)!==JSON.stringify(view.eraIds)?` · ${t('worlds.unsaved')}`:''}</p>}
      {content?.timeline&&<details><summary className="cursor-pointer text-sm">{t('world5.browseTimeline')}</summary><label className="mt-3 flex items-center gap-2 text-sm"><input type="checkbox" checked={all} onChange={e=>setAll(e.target.checked)}/>{t('world5.allEras')}</label><WorldTimelineSnapshot timeline={content.timeline} eraIds={all||!ids.length?undefined:ids} entryTitles={Object.fromEntries(content.entries.map(entry=>[entry.id,entry.title]))}/></details>}
    </>}
    {error&&<div role="alert" className="space-y-2 text-sm text-destructive"><p>{error}</p><Button variant="outline" size="sm" disabled={busy} onClick={()=>{if(JSON.stringify(ids)===JSON.stringify(view?.eraIds||[])||confirm(t('worlds.unsaved')))setAttempt(value=>value+1);}}>{t('world5.reloadEras')}</Button></div>}
  </section>;
}
