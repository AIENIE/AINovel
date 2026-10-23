import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api-client';
import type { WorldBindingView, WorldDetail } from '@/types';
import { localizedErrorMessage } from '@/lib/error-messages';
import { Button } from '@/components/ui/button';
import { WorldPublishDialog } from '@/pages/WorldBuilder/components/WorldVersionViews';

export function GuidedWorldPublication({worldId,storyId,publish}: {
  worldId:string;storyId:string;publish:(input:{expectedRevision:number;fromVersionId:string|null;expectedBindingRevision:number;releaseNote:string;requestKey:string})=>Promise<unknown>;
}) {
  const {t}=useTranslation();const [draft,setDraft]=useState<WorldDetail|null>(null),[binding,setBinding]=useState<WorldBindingView|null>(null);
  const [error,setError]=useState(''),[attempt,setAttempt]=useState(0),[open,setOpen]=useState(false);
  useEffect(()=>{let current=true;setDraft(null);setBinding(null);setError('');Promise.all([api.worlds.getDetail(worldId),api.worldBindings.get('STORY',storyId)]).then(([d,b])=>{if(current){setDraft(d);setBinding(b);}}).catch(e=>{if(current)setError(localizedErrorMessage(e));});return()=>{current=false;};},[worldId,storyId,attempt]);
  return <section className="space-y-5 rounded-lg border border-emerald-200 bg-white p-6"><h3 className="text-lg font-semibold">{t('world2.awaitingPublication')}</h3><p className="text-sm leading-7 text-muted-foreground">{t('world2.guidedPublishHelp')}</p>
    {draft && <p className="text-sm font-medium">{draft.name} · {t('world2.entryCount',{count:draft.entries?.length||0})}</p>}
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    <div className="flex flex-wrap gap-2"><Button disabled={!draft||!binding} onClick={()=>setOpen(true)}>{t('world2.publishAndBind')}</Button><Button asChild variant="outline"><Link to={`/world-editor?id=${worldId}`}>{t('world2.editDraft')}</Link></Button><Button variant="ghost" onClick={()=>setAttempt(n=>n+1)}>{t('world2.refreshDraft')}</Button></div>
    {open&&draft&&binding && <WorldPublishDialog draft={draft} stale={false} onClose={()=>setOpen(false)} onPublished={async()=>{}} submit={input=>publish({...input,fromVersionId:binding.explicitVersionId,expectedBindingRevision:binding.bindingRevision})}/>}
  </section>;
}
