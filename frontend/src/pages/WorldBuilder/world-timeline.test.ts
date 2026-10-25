import {describe,it,expect} from 'vitest';
import type {WorldDetail,WorldEvent} from '@/types';
import {emptyTimeline,definitelyAfter,formatWorldDate,timelineOrder} from './world-timeline';
import {mergeLocalWorldChanges,worldContent} from './world-draft';
import i18n from '@/i18n';

const event=(id:string,sortOrder=0):WorldEvent=>({id,title:id,summary:'',content:'原文',aliases:[],tags:[],date:null,entryIds:[],eraIds:[],sortOrder});
const world=(events:WorldEvent[]):WorldDetail=>({id:'w',revision:1,name:'世界',tagline:'',themes:[],creativeIntent:'',notes:'',status:'draft',version:'1',updatedAt:'',modules:[],entries:[],relations:[],timeline:{...emptyTimeline(),events}});
describe('world chronology and conflict merge',()=>{
  it('keeps partial and unknown dates without inferring a Gregorian date',()=>{
    expect(definitelyAfter({year:9},{year:9,month:3})).toBe(false);
    expect(definitelyAfter({year:10},{year:9,month:99,day:999})).toBe(true);
    expect(definitelyAfter({year:9,month:3,day:2},{year:9,month:3})).toBe(false);
    expect(formatWorldDate({start:{year:0},end:{year:0}},i18n.t.bind(i18n))).toBe('0 年');
    expect(formatWorldDate({start:null,end:{year:-10}},i18n.t.bind(i18n))).toBe('日期未知 — -10 年');
  });
  it('sorts known starts with precision then author order and stable identity, unknown starts last',()=>{
    const dated=(id:string,year:number,month?:number)=>({...event(id),date:{start:{year,month},end:{year,month}}});
    const rows=[event('z',1),dated('b',4,2),dated('a',4),event('c',0),dated('d',-1),dated('e',4,2)];
    expect(timelineOrder(rows,'date').map(row=>row.id)).toEqual(['d','a','b','e','c','z']);
    expect(timelineOrder(rows,'author').map(row=>row.id)).toEqual(['a','b','c','d','e','z']);
  });
  it('does not mark a legacy empty timeline dirty, but includes explicit order changes',()=>{
    const old={...world([]),timeline:undefined};
    expect(worldContent(old)).toBe(worldContent(world([])));
    expect(worldContent(world([event('a',1)]))).not.toBe(worldContent(world([event('a',2)])));
    expect(worldContent(world([event('a'),event('b')]))).toBe(worldContent(world([event('b'),event('a')])));
  });
  it('rebases only changed event fields and preserves independently added remote events',()=>{
    const base=world([event('a'),event('b')]);
    const local=world([{...event('a'),title:'作者标题'}]);
    const remote={...world([{...event('a'),content:'服务器正文'},event('b'),event('c')]),revision:2};
    const merged=mergeLocalWorldChanges(base,local,remote);
    expect(merged.timeline?.events).toEqual([{...event('a'),title:'作者标题',content:'服务器正文'},event('c')]);
    expect(merged.revision).toBe(2);
  });
});
