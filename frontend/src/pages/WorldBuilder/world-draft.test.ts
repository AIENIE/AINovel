import {describe,it,expect} from 'vitest';
import type {WorldDetail,WorldEntry} from '@/types';
import {mergeLocalWorldChanges} from './world-draft';

describe('entry draft conflict merge',()=>{
  it('keeps stable identities, merges separate fields, and removes relations to deleted entries',()=>{
    const entry=(id:string):WorldEntry=>({id,type:'CUSTOM',title:id,summary:'',content:'原文',aliases:[],tags:[],legacyModuleKey:null,legacyFieldKey:null});
    const base={id:'w',revision:1,name:'世界',themes:[],modules:[],entries:[entry('a'),entry('b')],relations:[{id:'r',sourceId:'a',targetId:'b',type:'影响',description:''}]} as unknown as WorldDetail;
    const local={...base,entries:[{...entry('a'),title:'作者改名'}],relations:[]};
    const server={...base,revision:2,entries:[{...entry('a'),content:'远端新正文'},entry('b'),entry('c')]};
    const merged=mergeLocalWorldChanges(base,local,server);
    expect(merged.entries?.map(e=>e.id)).toEqual(['a','c']);expect(merged.entries?.[0]).toMatchObject({id:'a',title:'作者改名',content:'远端新正文'});expect(merged.relations).toEqual([]);expect(merged.revision).toBe(2);
  });
});
