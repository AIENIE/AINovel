import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import type { WorldDatePoint, WorldDateRange, WorldDetail, WorldEra, WorldEvent, WorldTimeline as Timeline } from '@/types';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { WorldListInput } from './WorldListInput';
import { definitelyAfter, emptyTimeline, formatWorldDate, timelineOrder } from '../world-timeline';

const selectClass='max-w-full rounded-md border bg-background p-2 text-sm';
const infer=(date:WorldDateRange|null):'unknown'|'single'|'range'=>!date?.start&&!date?.end?'unknown':JSON.stringify(date.start)===JSON.stringify(date.end)?'single':'range';
function UnknownDateDivider({date,previous,first,visible=true}:{date:WorldDateRange|null;previous?:WorldDateRange|null;first:boolean;visible?:boolean}) {
  const {t}=useTranslation();
  return visible&&date?.start?.year==null&&(first||previous?.start?.year!=null)?<h4 className="border-t pt-3 text-xs font-medium text-muted-foreground">{t('world5.unknownStart')}</h4>:null;
}
function DatePointEditor({label,value,onChange,disabled}:{label:string;value:WorldDatePoint|null;onChange:(value:WorldDatePoint|null)=>void;disabled:boolean}) {
  const {t}=useTranslation();
  return <fieldset disabled={disabled} className="min-w-0 space-y-2"><legend className="text-xs text-muted-foreground">{label}</legend>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!!value} onChange={e=>onChange(e.target.checked?{year:null}:null)}/>{t('world5.known')}</label>
    {value&&<div className="grid grid-cols-3 gap-2">{(['year','month','day'] as const).map(key=><label key={key} className="space-y-1 text-xs">{t(`world5.${key}`)}<Input aria-label={`${label} ${t(`world5.${key}`)}`} type="number" step="1" min={key==='year'?undefined:1} value={value[key]??''} disabled={key==='month'?value.year==null:key==='day'?value.month==null:false} onChange={e=>{
      const number=e.target.value===''?null:Number(e.target.value);onChange({...value,[key]:number,...(key==='year'&&number===null?{month:null,day:null}:key==='month'&&number===null?{day:null}:{})});
    }}/></label>)}</div>}
  </fieldset>;
}
export function WorldDateEditor({value,onChange,disabled=false,label}:{value:WorldDateRange|null;onChange:(value:WorldDateRange|null)=>void;disabled?:boolean;label?:string}) {
  const {t}=useTranslation();
  const [mode,setMode]=useState(()=>infer(value));
  const authored=useRef<WorldDateRange|null|undefined>(undefined),previous=useRef(value);
  useEffect(()=>{if(value!==previous.current){previous.current=value;if(value!==authored.current)setMode(infer(value));authored.current=undefined;}},[value]);
  const change=(next:WorldDateRange|null)=>{authored.current=next;onChange(next);};
  const invalidDate=[value?.start,value?.end].some(point=>point&&(point.year==null||!Number.isInteger(point.year)||(point.month!=null&&(!Number.isInteger(point.month)||point.month<=0))||(point.day!=null&&(point.month==null||!Number.isInteger(point.day)||point.day<=0))));
  return <div className="space-y-3"><label className="flex flex-wrap items-center gap-3 text-sm">{label||t('world5.date')}<select aria-label={`${label||t('world5.date')} ${t('world5.dateMode')}`} className={selectClass} disabled={disabled} value={mode} onChange={e=>{const next=e.target.value as typeof mode;setMode(next);change(next==='unknown'?null:next==='single'?{start:value?.start||{year:null},end:value?.start||{year:null}}:{start:value?.start||null,end:value?.end||null});}}>
    <option value="unknown">{t('world5.unknownDate')}</option><option value="single">{t('world5.singleDate')}</option><option value="range">{t('world5.rangeDate')}</option></select></label>
    {mode!=='unknown'&&<div className="grid gap-4 sm:grid-cols-2"><DatePointEditor label={`${label||t('world5.date')} ${t('world5.start')}`} value={value?.start||null} disabled={disabled} onChange={start=>change({start,end:mode==='single'?start:value?.end||null})}/>{mode==='range'&&<DatePointEditor label={`${label||t('world5.date')} ${t('world5.end')}`} value={value?.end||null} disabled={disabled} onChange={end=>change({start:value?.start||null,end})}/>}</div>}
    {definitelyAfter(value?.start,value?.end)&&<p role="alert" className="text-sm text-destructive">{t('world5.reversedDate')}</p>}
    {invalidDate&&<p role="alert" className="text-sm text-destructive">{t('world5.dateInvalid')}</p>}
  </div>;
}
export function WorldReferenceChoices({label,values,options,onChange,disabled=false}:{label:string;values:string[];options:{id:string;label:string}[];onChange:(ids:string[])=>void;disabled?:boolean}) {
  const {t}=useTranslation();const missing=values.filter(id=>!options.some(option=>option.id===id));
  return <fieldset disabled={disabled} className="space-y-2"><legend className="mb-2 text-sm">{label}</legend><div className="flex max-h-44 flex-wrap gap-x-5 gap-y-2 overflow-y-auto">{[...options,...missing.map(id=>({id,label:`${t('world5.pendingReference')} · ${id}`}))].map(option=><label className="flex max-w-full items-start gap-2 text-sm" key={option.id}><input type="checkbox" className="mt-1" checked={values.includes(option.id)} onChange={e=>onChange(e.target.checked?[...values,option.id]:values.filter(id=>id!==option.id))}/><span className="break-words">{option.label}</span></label>)}</div></fieldset>;
}
export function WorldTimeline({draft,edit}:{draft:WorldDetail;edit:(update:(draft:WorldDetail)=>WorldDetail)=>void}) {
  const {t}=useTranslation();const timeline=draft.timeline||emptyTimeline();
  const [order,setOrder]=useState<'date'|'author'>('date'),[eraFilter,setEraFilter]=useState(''),[selected,setSelected]=useState('');
  const change=(update:(value:Timeline)=>Timeline)=>edit(current=>({...current,timeline:update(current.timeline||emptyTimeline())}));
  const events=timelineOrder(timeline.events.filter(event=>!eraFilter||event.eraIds.includes(eraFilter)),order);
  const orderedEras=timelineOrder(timeline.eras,order);
  const event=events.find(item=>item.id===selected)||events[0];
  // Pin the implicit first selection before date/order edits can reorder the directory.
  useEffect(()=>{if(event&&event.id!==selected)setSelected(event.id);},[event,selected]);
  const updateEvent=(patch:Partial<WorldEvent>)=>change(value=>({...value,events:value.events.map(row=>row.id===event?.id?{...row,...patch}:row)}));
  const updateEra=(id:string,patch:Partial<WorldEra>)=>change(value=>({...value,eras:value.eras.map(row=>row.id===id?{...row,...patch}:row)}));
  const move=(kind:'eras'|'events',id:string,offset:number)=>change(value=>{const rows=timelineOrder(value[kind] as (WorldEra|WorldEvent)[],'author'),index=rows.findIndex(row=>row.id===id),target=index+offset;if(target<0||target>=rows.length)return value;[rows[index],rows[target]]=[rows[target],rows[index]];return {...value,[kind]:rows.map((row,index)=>({...row,sortOrder:index}))};});
  const controls=(kind:'eras'|'events',id:string)=><div className="flex gap-1"><Button size="icon" variant="ghost" aria-label={t('world5.up')} onClick={()=>move(kind,id,-1)}><ArrowUp className="h-4 w-4"/></Button><Button size="icon" variant="ghost" aria-label={t('world5.down')} onClick={()=>move(kind,id,1)}><ArrowDown className="h-4 w-4"/></Button></div>;
  const outside=event?.eraIds.some(id=>{const era=timeline.eras.find(item=>item.id===id);return definitelyAfter(era?.date?.start,event.date?.start)||definitelyAfter(event.date?.end,era?.date?.end);});
  return <section className="space-y-6" aria-label={t('world5.timeline')}>
    <div className="space-y-2"><label className="block max-w-xl space-y-2 text-sm">{t('world5.calendarName')}<Input value={timeline.calendarName} onChange={e=>change(value=>({...value,calendarName:e.target.value}))}/></label><p className="text-sm text-muted-foreground">{t('world5.calendarHelp')}</p></div>
    <div className="flex flex-wrap items-center gap-3"><label className="flex items-center gap-2 text-sm">{t('world5.order')}<select className={selectClass} value={order} onChange={e=>setOrder(e.target.value as typeof order)}><option value="date">{t('world5.dateOrder')}</option><option value="author">{t('world5.authorOrder')}</option></select></label><Button variant="outline" onClick={()=>change(value=>({...value,eras:[...value.eras,{id:crypto.randomUUID(),name:t('world5.newEra'),description:'',date:null,sortOrder:Math.max(-1,...value.eras.map(row=>row.sortOrder))+1}]}))}><Plus className="mr-2 h-4 w-4"/>{t('world5.addEra')}</Button><Button onClick={()=>{const id=crypto.randomUUID();change(value=>({...value,events:[...value.events,{id,title:t('world5.newEvent'),summary:'',content:'',aliases:[],tags:[],date:null,entryIds:[],eraIds:eraFilter?[eraFilter]:[],sortOrder:Math.max(-1,...value.events.map(row=>row.sortOrder))+1}]}));setSelected(id);}}><Plus className="mr-2 h-4 w-4"/>{t('world5.addEvent')}</Button></div>
    <section className="space-y-3" aria-label={t('world5.eras')}><h3 className="font-semibold">{t('world5.eras')}</h3>{!timeline.eras.length&&<p className="text-sm text-muted-foreground">{t('world5.noEras')}</p>}{orderedEras.map((era,index)=><div key={era.id} className="space-y-3"><UnknownDateDivider date={era.date} previous={orderedEras[index-1]?.date} first={index===0} visible={order==="date"}/><details className="rounded-md border p-4"><summary className="cursor-pointer break-words text-sm font-medium">{era.name} · {formatWorldDate(era.date,t)}</summary><div className="mt-4 space-y-4"><div className="flex flex-wrap items-center justify-between gap-2">{controls('eras',era.id)}<Button size="sm" variant="ghost" onClick={()=>{const affected=timeline.events.filter(row=>row.eraIds.includes(era.id));if(confirm(t('world5.deleteEraConfirm',{name:era.name,events:affected.map(row=>row.title).join('、')||t('world5.noReferences')}))){change(value=>({...value,eras:value.eras.filter(row=>row.id!==era.id),events:value.events.map(row=>({...row,eraIds:row.eraIds.filter(id=>id!==era.id)}))}));if(eraFilter===era.id)setEraFilter('');}}}><Trash2 className="mr-1 h-4 w-4"/>{t('world5.deleteEra')}</Button></div><label className="block space-y-2 text-sm">{t('world5.name')}<Input value={era.name} onChange={e=>updateEra(era.id,{name:e.target.value})}/></label><label className="block space-y-2 text-sm">{t('world5.description')}<Textarea value={era.description} onChange={e=>updateEra(era.id,{description:e.target.value})}/></label><WorldDateEditor value={era.date} onChange={date=>updateEra(era.id,{date})}/></div></details></div>)}</section>
    <section className="grid overflow-hidden rounded-md border lg:grid-cols-[260px_minmax(0,1fr)]" aria-label={t('world5.events')}><aside className="space-y-3 border-b bg-muted/20 p-4 lg:border-b-0 lg:border-r"><h3 className="font-semibold">{t('world5.events')}</h3><select className={`${selectClass} w-full`} aria-label={t('world5.filterEra')} value={eraFilter} onChange={e=>setEraFilter(e.target.value)}><option value="">{t('world5.allEras')}</option>{timeline.eras.map(era=><option key={era.id} value={era.id}>{era.name}</option>)}</select><nav className="max-h-[60vh] space-y-1 overflow-y-auto">{events.map((row,index)=><div key={row.id}><UnknownDateDivider date={row.date} previous={events[index-1]?.date} first={index===0} visible={order==="date"}/><button className={`block w-full rounded p-3 text-left ${row.id===event?.id?'bg-primary/10':'hover:bg-muted'}`} aria-current={row.id===event?.id?'true':undefined} onClick={()=>setSelected(row.id)}><span className="block break-words text-sm">{row.title}</span><span className="mt-1 block text-xs text-muted-foreground">{formatWorldDate(row.date,t)}</span></button></div>)}</nav></aside>
      {event?<div key={event.id} className="min-w-0 space-y-5 p-4 md:p-6"><div className="flex flex-wrap items-center justify-between gap-2">{controls('events',event.id)}<Button variant="ghost" size="sm" onClick={()=>{if(confirm(t('world5.deleteEventConfirm',{name:event.title})))change(value=>({...value,events:value.events.filter(row=>row.id!==event.id)}));}}><Trash2 className="mr-1 h-4 w-4"/>{t('world5.deleteEvent')}</Button></div>{(['title','summary','content'] as const).map(key=><label key={key} className="block space-y-2 text-sm">{t(`world2.${key}`)}{key==='title'?<Input value={event[key]} onChange={e=>updateEvent({[key]:e.target.value})}/>:<Textarea value={event[key]} rows={key==='content'?8:3} onChange={e=>updateEvent({[key]:e.target.value})}/>}</label>)}<WorldDateEditor value={event.date} onChange={date=>updateEvent({date})}/>{outside&&<p role="status" className="text-sm text-amber-700 dark:text-amber-300">{t('world5.outsideEra')}</p>}<WorldReferenceChoices label={t('world5.eraIds')} values={event.eraIds} options={timeline.eras.map(era=>({id:era.id,label:era.name}))} onChange={eraIds=>updateEvent({eraIds})}/><WorldReferenceChoices label={t('world5.entries')} values={event.entryIds} options={(draft.entries||[]).map(entry=>({id:entry.id,label:entry.title}))} onChange={entryIds=>updateEvent({entryIds})}/>{(['aliases','tags'] as const).map(key=><WorldListInput key={key} label={t(`world2.${key}`)} values={event[key]} onChange={value=>updateEvent({[key]:value})}/>)}</div>:<p className="p-6 text-sm text-muted-foreground">{t('world5.noEvents')}</p>}
    </section>
  </section>;
}
export function WorldTimelineSnapshot({timeline,entryTitles={},eraIds}:{timeline?:Timeline|null;entryTitles?:Record<string,string>;eraIds?:string[]}) {
  const {t}=useTranslation();if(!timeline||(!timeline.calendarName&&!timeline.eras.length&&!timeline.events.length))return null;
  const eras=timelineOrder(timeline.eras.filter(era=>!eraIds||eraIds.includes(era.id)),'date');
  const events=timelineOrder(timeline.events.filter(event=>!eraIds||event.eraIds.some(id=>eraIds.includes(id))),'date');
  return <section className="space-y-4 border-t pt-5">
    <h3 className="font-semibold">{t('world5.timeline')} {timeline.calendarName&&`· ${timeline.calendarName}`}</h3>
    <section aria-label={t('world5.eras')} className="space-y-4">{eras.map((era,index)=><article key={era.id} className="space-y-2">
      <UnknownDateDivider date={era.date} previous={eras[index-1]?.date} first={index===0}/>
      <h4 className="break-words font-medium">{era.name} · {formatWorldDate(era.date,t)}</h4><p className="whitespace-pre-wrap break-words text-sm">{era.description}</p>
    </article>)}</section>
    <section aria-label={t('world5.events')} className="space-y-4">{events.map((event,index)=><article key={event.id} className="space-y-2 border-t pt-4">
      <UnknownDateDivider date={event.date} previous={events[index-1]?.date} first={index===0}/>
      <h4 className="break-words font-medium">{event.title} · {formatWorldDate(event.date,t)}</h4><p className="whitespace-pre-wrap break-words text-sm text-muted-foreground">{event.summary}</p><p className="whitespace-pre-wrap break-words text-sm leading-7">{event.content}</p>
      {!!event.aliases.length&&<p className="break-words text-xs">{t('world2.aliases')} · {event.aliases.join('、')}</p>}{!!event.tags.length&&<p className="break-words text-xs">{t('world2.tags')} · {event.tags.join('、')}</p>}
      {!!event.eraIds.length&&<p className="break-words text-xs">{t('world5.eras')} · {event.eraIds.map(id=>timeline.eras.find(era=>era.id===id)?.name||id).join('、')}</p>}{!!event.entryIds.length&&<p className="break-words text-xs">{t('world5.entries')} · {event.entryIds.map(id=>entryTitles[id]||id).join('、')}</p>}
    </article>)}</section>
  </section>;
}
