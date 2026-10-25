import type { WorldDatePoint, WorldDateRange, WorldTimeline } from '@/types';

export const emptyTimeline = (): WorldTimeline => ({schemaVersion:1,calendarName:'',eras:[],events:[]});
export function canonicalTimeline(value?: WorldTimeline | null) {
  if(!value || (!value.calendarName && !value.eras.length && !value.events.length))return null;
  const byId=<T extends {id:string}>(rows:T[])=>[...rows].sort((a,b)=>a.id.localeCompare(b.id));
  return {...value,eras:byId(value.eras),events:byId(value.events)};
}
export function compareDates(a?: WorldDatePoint | null,b?: WorldDatePoint | null) {
  if(a?.year==null||b?.year==null)return a?.year!=null?-1:b?.year!=null?1:0;
  return a.year-b.year || (a.month??0)-(b.month??0) || (a.day??0)-(b.day??0);
}
/** Only compare shared precision; year-only dates are never invented as January 1. */
export function definitelyAfter(a?: WorldDatePoint | null,b?: WorldDatePoint | null) {
  if(a?.year==null||b?.year==null)return false;
  if(a.year!==b.year)return a.year>b.year;
  if(a.month==null||b.month==null)return false;
  if(a.month!==b.month)return a.month>b.month;
  return a.day!=null&&b.day!=null&&a.day>b.day;
}
export function timelineOrder<T extends {id:string;date:WorldDateRange|null;sortOrder:number}>(rows:T[],order:'date'|'author') {
  return [...rows].sort((a,b)=>(order==='date'?compareDates(a.date?.start,b.date?.start):0)||a.sortOrder-b.sortOrder||a.id.localeCompare(b.id));
}
export function formatWorldDate(range:WorldDateRange|null|undefined,t:(key:string,options?:Record<string,unknown>)=>string):string {
  const point=(p:WorldDatePoint|null|undefined)=>p?.year==null?t('world5.unknownDate'):`${t('world5.yearValue',{value:p.year})}${p.month==null?'':` ${t('world5.monthValue',{value:p.month})}`}${p.day==null?'':` ${t('world5.dayValue',{value:p.day})}`}`;
  if(!range?.start&&!range?.end)return t('world5.unknownDate');
  if(JSON.stringify(range?.start)===JSON.stringify(range?.end))return point(range?.start);
  return `${point(range?.start)} — ${point(range?.end)}`;
}
