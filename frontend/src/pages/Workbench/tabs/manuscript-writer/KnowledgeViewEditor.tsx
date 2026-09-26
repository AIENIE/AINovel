import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import type { KnowledgeView, NarrativeKind } from '@/types/narrative';

export function KnowledgeViewEditor({value,kind,onChange}:{value?:KnowledgeView|null;kind:NarrativeKind;onChange:(value:KnowledgeView|null)=>void}) {
  if(!value)return <div className="space-y-1"><p className="text-xs text-amber-700">待补充人物可用表述；这条旧知情不会进入严格人物输入。</p><Button type="button" size="sm" variant="outline" onClick={()=>onChange({content:'',kind,certainty:kind==='BELIEF'?'BELIEVED':kind==='UTTERANCE'||kind==='RUMOR'?'REPORTED':kind==='INFERENCE'?'INFERRED':'UNKNOWN'})}>补充人物可用表述</Button></div>;
  return <div className="space-y-2 rounded border p-2">
    <label className="block text-xs">人物可用表述<Textarea maxLength={2000} value={value.content} onChange={e=>onChange({...value,kind,content:e.target.value})}/></label>
    <p className="text-xs">只写人物能够知道或相信的内容，不包含作者纠错、隐藏真相或其他人的内心。类型沿用本条：{kind}。</p>
    <label className="block text-xs">人物获知性质<select className="w-full rounded border bg-background p-2 text-sm" value={value.certainty} onChange={e=>onChange({...value,kind,certainty:e.target.value as KnowledgeView['certainty']})}>
      <option value="OBSERVED">亲历或观察</option><option value="REPORTED">听闻，未经核实</option><option value="BELIEVED">人物信念</option><option value="INFERRED">人物推断</option><option value="UNKNOWN">性质尚不确定</option>
    </select></label>
    <details className="space-y-2 text-xs">
      <summary className="cursor-pointer">关键归因与获知依据（可选）</summary>
      <p>普通动作、环境和对白可由模型补充。影响情节的实施者、获知来源和往事须有作者依据；留空表示尚未提供，不代表人物一定不知道。以下内容会进入人物输入，只写人物可用的信息，不填作者纠错或隐藏真相。</p>
      <label className="block">事件实施者<Textarea maxLength={500} value={value.eventActor??''} placeholder="未明确则留空；相信的人不一定是实施者" onChange={e=>onChange({...value,kind,eventActor:e.target.value||null})}/></label>
      <label className="block">获知依据<Textarea maxLength={1000} value={value.acquisitionBasis??''} placeholder="如当面听到某人陈述，尚未核实；梦境需注明仅为梦境" onChange={e=>onChange({...value,kind,acquisitionBasis:e.target.value||null})}/></label>
      <p>这两项沿用本条的信息类型与可信度，补充来源不会把信念或传闻变成事实。建议内容仍须随审阅或配置保存由作者确认。</p>
    </details>
    <Button type="button" size="sm" variant="ghost" onClick={()=>onChange(null)}>移除人物可用表述</Button>
  </div>;
}
