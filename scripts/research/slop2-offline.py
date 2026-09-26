"""Frozen structural observations, not production rules. No model calls."""
import collections, hashlib, json, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; D=ROOT/'doc/research/slop2-20260926'
def read(n): return json.loads((D/n).read_text(encoding='utf-8-sig'))
def save(n,v): (D/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
def observe(text):
 signals=[]
 patterns={
  'negative_chain':r'不是|并非|与其说|没有什么',
  'emotion_tail':r'语气里带着|声音中充满|这句话透露|话音透着|字字都是|显然',
  'generic_imagery':r'像刀子|仿佛星辰|宛若利刃|如同灯塔|是一片海|空气仿佛凝固',
  'abstract_ending':r'所有的.{1,8}都有了|命运的齿轮|这是.{1,8}(意义|真谛|安排)|从此.{1,8}(旅程|篇章|蜕变)|这意味着|每一次.{1,15}(开始|成长|馈赠)'}
 for base in range(0,len(text),250):
  window=text[base:base+500]
  for kind,pattern in patterns.items():
   hits=list(re.finditer(pattern,window))
   if len(hits)>=3: signals.append(dict(kind=kind,start=base+hits[0].start(),count=len(hits),evidence=[h[0] for h in hits]))
  sentences=[s.strip() for s in re.split(r'[。！？；，]',window) if s.strip()]
  skeletons=[]
  for s in sentences:
   s=re.sub(r'^(他|她|老人|孩子|守卫|弟弟|母亲|陈平|赵禾|孙叔|门边的人|窗边的人|桌边的人|走廊里的人)','<人>',s)
   s=re.sub(r'把.{1,4}?(?=拿起|放回去)','把<物>',s)
   s=re.sub(r'端起.{1,3}?(?=又搁下)','端起<物>',s)
   skeletons.append(s)
  counts=collections.Counter(skeletons)
  evidence=[s for s,n in counts.items() if n>=3 and len(s)>=5]
  if evidence: signals.append(dict(kind='repeated_skeleton',start=base,count=max(counts[x] for x in evidence),evidence=evidence))
 # No semantic intervention claim: every observation is provisional and may abstain.
 unique={json.dumps(s,ensure_ascii=False,sort_keys=True):s for s in signals}
 return list(unique.values())
def metric(rows,pred):
 c=dict(tp=0,fp=0,tn=0,fn=0)
 for r in rows:
  y=r['expectedIntervention']; p=pred[r['id']]
  c[('tp' if y else 'fp') if p else ('fn' if y else 'tn')]+=1
 tp,fp,tn,fn=(c[k] for k in ['tp','fp','tn','fn'])
 return dict(c,precision=tp/(tp+fp) if tp+fp else None,recall=tp/(tp+fn) if tp+fn else None,
  accuracy=(tp+tn)/len(rows),abstentionRate=(tn+fn)/len(rows))
def main():
 rows=read('samples.json'); baseline={x['id']:x['result']['requiresAiReview'] for x in read('baseline-results.json')}
 results=[dict(id=r['id'],signals=observe(r['text'])) for r in rows]
 proto={x['id']:bool(x['signals']) for x in results}
 metrics={}
 for label,pred in [('shipping_trigger',baseline),('structural_candidate',proto)]:
  groups={'all':rows}
  for field in ['split','family','provenance']:
   for value in sorted({r[field] for r in rows}): groups[field+':'+value]=[r for r in rows if r[field]==value]
  metrics[label]={g:metric(rs,pred) for g,rs in groups.items()}
 save('structural-results.json',results);save('offline-metrics.json',metrics)
 save('prototype-freeze.json',dict(codeSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
  productionMutation=False,trained=False,tunedOnHoldout=False,definition='candidate flag, not editorial truth; no flag = abstain/no issue',
  limitations='handwritten regex normalization, not a dependency parser or semantic model; family-specific phrases remain; correlated synthetic pairs'))
 print(json.dumps({k:v['all'] for k,v in metrics.items()},ensure_ascii=False))
if __name__=='__main__':main()
