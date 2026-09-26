import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import type { ContextEntry } from '@/types/narrative-context';

export function StableAttributeEditor({characters,sceneId,onAdd}:{characters:Array<{id:string;name:string}>;sceneId:string;onAdd:(entry:ContextEntry)=>void}) {
  const [person,setPerson]=useState('');
  const [name,setName]=useState('');
  const [pronoun,setPronoun]=useState('');
  const [appearance,setAppearance]=useState('');
  const selected=characters.find(c=>c.id===person);
  return <details className="space-y-2 rounded border p-2">
    <summary className="cursor-pointer text-xs">整理人物稳定属性</summary>
    <p className="text-xs">仅录入本次选择的属性，默认从当前场景起对该人物可用。加入后可在背景条目中调整知情人物与适用起点。缺少的性别、身份或外观不由模型补全。</p>
    <label className="block text-xs">属性所属人物<select className="w-full rounded border bg-background p-2 text-sm" value={person} onChange={e=>setPerson(e.target.value)}><option value="">选择人物</option>{characters.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
    <label className="block text-xs">称谓<Input maxLength={100} value={name} onChange={e=>setName(e.target.value)}/></label>
    <label className="block text-xs">代词<Input maxLength={30} placeholder="如：她；或始终使用姓名" value={pronoun} onChange={e=>setPronoun(e.target.value)}/></label>
    <label className="block text-xs">必要外观<Input maxLength={500} value={appearance} onChange={e=>setAppearance(e.target.value)}/></label>
    <Button type="button" size="sm" variant="outline" disabled={!selected || ![name,pronoun,appearance].some(v=>v.trim())} onClick={()=>{
      if(!selected)return;
      const attributes=[name.trim() && `称谓：${name.trim()}`,pronoun.trim() && `代词：${pronoun.trim()}`,appearance.trim() && `必要外观：${appearance.trim()}`].filter(Boolean);
      onAdd({id:crypto.randomUUID(),kind:'BACKGROUND',text:`人物稳定属性（${selected.name}）：${attributes.join('；')}。仅使用明确给出的属性，不据此外推身份或经历。`,fromSceneId:sceneId,characterIds:[person],narratorVisible:false,dependencyRecordIds:[],stale:false});
      setName('');setPronoun('');setAppearance('');
    }}>将属性加入配置草稿</Button>
  </details>;
}
