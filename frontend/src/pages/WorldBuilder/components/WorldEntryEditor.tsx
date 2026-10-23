import { WorldListInput as ListInput } from './WorldListInput';
import { WORLD_ENTRY_TYPES } from '../world-draft';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Search, Trash2, Sparkles, Link2, FileText } from 'lucide-react';
import type { WorldDetail, WorldEntry, WorldEntryType } from '@/types';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog';

const emptyEntries: WorldEntry[] = [];
const selectClass = 'h-10 w-full rounded-md border border-input bg-background px-3 text-sm';


export function WorldEntryEditor({draft, edit, busy, onGenerate, onRefine}: {
  draft: WorldDetail; edit: (update: (draft: WorldDetail) => WorldDetail) => void; busy: boolean;
  onGenerate: (entry: WorldEntry) => void; onRefine: (entry: WorldEntry) => void;
}) {
  const {t} = useTranslation();
  const [selected, setSelected] = useState('');
  const [query, setQuery] = useState('');
  const [type, setType] = useState('');
  const [tag, setTag] = useState('');
  const [pendingDelete, setPendingDelete] = useState<WorldEntry | null>(null);
  const entries = draft.entries || emptyEntries, relations = draft.relations || [];
  useEffect(()=>{if(!selected&&entries[0])setSelected(entries[0].id);},[selected,entries]);
  const tags = useMemo(() => [...new Set(entries.flatMap(e => e.tags))].sort(), [entries]);
  const filtered = entries.filter(e => (!type || e.type === type) && (!tag || e.tags.includes(tag)) &&
    (!query || [e.title,e.summary,e.content,...e.aliases,...e.tags].join(' ').toLocaleLowerCase().includes(query.toLocaleLowerCase())));
  const active = filtered.find(e => e.id === selected) || filtered[0];
  const update = (change: Partial<WorldEntry>) => edit(current => ({...current,entries:(current.entries || []).map(e => e.id === active?.id ? {...e,...change} : e)}));
  const related = relations.filter(r => r.sourceId === active?.id || r.targetId === active?.id);
  return <section className="grid min-h-[60vh] overflow-hidden rounded-lg border bg-background lg:grid-cols-[280px_minmax(0,1fr)]" aria-label={t('world2.entries')}>
    <aside className="flex flex-col border-b bg-muted/20 lg:border-b-0 lg:border-r">
      <div className="space-y-3 p-4">
        <div className="flex items-center justify-between"><h2 className="font-semibold">{t('world2.entries')} <span className="ml-1 text-xs font-normal text-muted-foreground">{entries.length}</span></h2>
          <Button size="sm" variant="outline" onClick={() => {
            const id=crypto.randomUUID();edit(current => ({...current,entries:[...(current.entries || []),{id,type:'CUSTOM',title:t('world2.newEntry'),summary:'',content:'',aliases:[],tags:[],legacyModuleKey:null,legacyFieldKey:null}]}));
            setSelected(id);setQuery('');setType('');setTag('');
          }}><Plus className="mr-1 h-3.5 w-3.5" />{t('world2.add')}</Button>
        </div>
        <div className="relative"><Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input className="pl-9" aria-label={t('world2.search')} placeholder={t('world2.search')} value={query} onChange={e => setQuery(e.target.value)} /></div>
        <div className="grid grid-cols-2 gap-2">
          <select className={selectClass} aria-label={t('world2.filterType')} value={type} onChange={e => setType(e.target.value)}><option value="">{t('world2.allTypes')}</option>{WORLD_ENTRY_TYPES.map(key => <option key={key} value={key}>{t(`world2.types.${key}`)}</option>)}</select>
          <select className={selectClass} aria-label={t('world2.filterTag')} value={tag} onChange={e => setTag(e.target.value)}><option value="">{t('world2.allTags')}</option>{tags.map(value => <option key={value}>{value}</option>)}</select>
        </div>
      </div>
      <nav className="max-h-[65vh] overflow-y-auto px-2 pb-3" aria-label={t('world2.directory')}>
        {filtered.map(entry => <button key={entry.id} className={`mb-1 block w-full rounded-md border-l-2 px-3 py-3 text-left transition-colors ${entry.id === active?.id ? 'border-primary bg-primary/5' : 'border-transparent hover:bg-muted'}`} onClick={() => setSelected(entry.id)} aria-current={entry.id === active?.id ? 'true' : undefined}>
          <span className="block truncate text-sm font-medium">{entry.title || t('world2.newEntry')}</span><span className="mt-1 block text-xs text-muted-foreground">{t(`world2.types.${entry.type}`)}{!entry.content?.trim() && ` · ${t('world2.blank')}`}</span>
        </button>)}
        {!filtered.length && <p className="p-4 text-sm leading-6 text-muted-foreground">{entries.length ? t('world2.noMatch') : t('world2.emptyHelp')}</p>}
      </nav>
    </aside>
    {active ? <div className="min-w-0 space-y-6 p-5 md:p-7">
      <div className="flex flex-wrap items-center justify-between gap-3"><span className="text-xs uppercase tracking-widest text-muted-foreground">{t(`world2.types.${active.type}`)}</span>
        <div className="flex items-center gap-2"><Button variant="outline" size="sm" disabled={busy} onClick={() => active.content?.trim() ? onRefine(active) : onGenerate(active)}><Sparkles className="mr-2 h-3.5 w-3.5" />{t(active.content?.trim() ? 'world2.refine' : 'world2.fill')}</Button>
          <Button variant="ghost" size="icon" aria-label={t('world2.deleteEntry')} onClick={() => setPendingDelete(active)}><Trash2 className="h-4 w-4 text-muted-foreground" /></Button></div>
      </div>
      <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_180px]">
        <label className="space-y-2 text-sm">{t('world2.title')}<Input className="text-lg font-medium" value={active.title} maxLength={255} onChange={e => update({title:e.target.value})} /></label>
        <label className="space-y-2 text-sm">{t('world2.type')}<select className={selectClass} value={active.type} onChange={e => update({type:e.target.value as WorldEntryType})}>{WORLD_ENTRY_TYPES.map(key => <option key={key} value={key}>{t(`world2.types.${key}`)}</option>)}</select></label>
      </div>
      <label className="block space-y-2 text-sm">{t('world2.summary')}<Textarea value={active.summary || ''} rows={2} onChange={e => update({summary:e.target.value})} /></label>
      <label className="block space-y-2 text-sm">{t('world2.content')}<Textarea className="min-h-[320px] leading-7" value={active.content || ''} onChange={e => update({content:e.target.value})} placeholder={t('world2.contentHelp')} /></label>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-2 text-sm">{t('world2.aliases')}<ListInput key={`${active.id}:aliases`} values={active.aliases} placeholder={t('world2.commaHelp')} onChange={aliases => update({aliases})} /></label>
        <label className="space-y-2 text-sm">{t('world2.tags')}<ListInput key={`${active.id}:tags`} values={active.tags} placeholder={t('world2.commaHelp')} onChange={tags => update({tags})} /></label>
      </div>
      <section className="space-y-3 border-t pt-5" aria-label={t('world2.relations')}>
        <div className="flex items-center justify-between"><h3 className="flex items-center gap-2 text-sm font-medium"><Link2 className="h-4 w-4" />{t('world2.relations')}</h3>
          <Button size="sm" variant="ghost" disabled={entries.length < 2} onClick={() => edit(current => ({...current,relations:[...(current.relations || []),{id:crypto.randomUUID(),sourceId:active.id,targetId:entries.find(e => e.id !== active.id)!.id,type:t('world2.related'),description:''}]}))}><Plus className="mr-1 h-3.5 w-3.5" />{t('world2.addRelation')}</Button></div>
        {related.map(relation => {
          const updateRelation = (change: Partial<typeof relation>) => edit(current => ({...current,relations:current.relations?.map(r => r.id === relation.id ? {...r,...change} : r)}));
          return <div key={relation.id} className="space-y-2 rounded-md border p-3"><div className="grid grid-cols-[1fr_auto_1fr_auto] items-center gap-2">
            <select className={selectClass} aria-label={t('world2.source')} value={relation.sourceId} onChange={e => updateRelation({sourceId:e.target.value})}>{entries.map(e => <option key={e.id} value={e.id}>{e.title}</option>)}</select><span aria-hidden>→</span>
            <select className={selectClass} aria-label={t('world2.target')} value={relation.targetId} onChange={e => updateRelation({targetId:e.target.value})}>{entries.map(e => <option key={e.id} value={e.id}>{e.title}</option>)}</select>
            <Button variant="ghost" size="icon" aria-label={t('world2.deleteRelation')} onClick={() => edit(current => ({...current,relations:current.relations?.filter(r => r.id !== relation.id)}))}><Trash2 className="h-4 w-4" /></Button>
          </div><Input aria-label={t('world2.relationType')} placeholder={t('world2.relationType')} value={relation.type} maxLength={255} onChange={e => updateRelation({type:e.target.value})} /><Textarea aria-label={t('world2.relationNote')} placeholder={t('world2.relationNote')} value={relation.description || ''} rows={2} onChange={e => updateRelation({description:e.target.value})} /></div>;
        })}
        {!related.length && <p className="text-sm text-muted-foreground">{t('world2.noRelations')}</p>}
      </section>
      {active.legacyModuleKey && <p className="text-xs text-muted-foreground">{t('world2.migratedEntry',{source:`${active.legacyModuleKey}.${active.legacyFieldKey}`})}</p>}
    </div> : <div className="flex min-h-72 flex-col items-center justify-center gap-4 p-8 text-center text-muted-foreground"><FileText className="h-8 w-8 opacity-40" /><p className="max-w-sm text-sm leading-7">{t(entries.length ? 'world2.noMatch' : 'world2.emptyHelp')}</p></div>}
    <AlertDialog open={!!pendingDelete} onOpenChange={open => { if (!open) setPendingDelete(null); }}>
      <AlertDialogContent>
        <AlertDialogHeader><AlertDialogTitle>{t('world2.deleteEntry')}</AlertDialogTitle>
          <AlertDialogDescription>{pendingDelete && t('world2.deleteConfirm',{title:pendingDelete.title,count:relations.filter(r => r.sourceId === pendingDelete.id || r.targetId === pendingDelete.id).length})}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter><AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel><AlertDialogAction onClick={() => {
          if (!pendingDelete) return;
          edit(current => ({...current,entries:current.entries?.filter(e => e.id !== pendingDelete.id),relations:current.relations?.filter(r => r.sourceId !== pendingDelete.id && r.targetId !== pendingDelete.id)}));
        }}>{t('world2.deleteEntry')}</AlertDialogAction></AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </section>;
}
