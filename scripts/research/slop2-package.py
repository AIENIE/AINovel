"""Export a credential-free, complete research bundle and integrity summary. No calls."""
import collections, difflib, hashlib, json, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
D=ROOT/'doc/research/slop2-20260926'; R=ROOT/'artifacts/slop2-20260926'
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def save(n,x): (D/n).write_text(json.dumps(x,ensure_ascii=False,indent=2,default=str)+'\n',encoding='utf8',newline='\n')
def sha(b): return hashlib.sha256(b).hexdigest()
def main():
 ledger=read(R/'ledger.json'); calls=[]
 for p in sorted(R.glob('*-response.json')):
  stem=p.name.removesuffix('-response.json'); marker=read(R/(stem+'-started.json'))
  request=read(R/(stem+'-request.json')); response=read(p)
  response['body']={k:v for k,v in response['body'].items() if k!='remainingCredits'}
  rows=[r for r in ledger['calls'] if r['request_id']==marker['key']]
  assert len(rows)==1, stem
  row=rows[0]
  assert row['model']=='deepseek-flash'
  # Explicit fields: no authentication header, account, runtime configuration, or balance.
  accounting={k:row[k] for k in ['attempt','request_id','model','status','reservation_status','settled_amount','prompt_tokens','completion_tokens','cache_tokens']}
  calls.append(dict(id=stem,request=request,response=response,dispatch=marker,accounting=accounting))
 calls.sort(key=lambda c:c['accounting']['attempt'])
 assert len(calls)==ledger['budget']['used']<=40
 save('experiment-calls.json',calls)
 candidates=[read(p) for p in sorted(R.glob('*-candidate.json'))]
 save('experiment-candidates.json',candidates)
 rows=read(D/'samples.json'); assert len(rows)==72
 old={s['id']:s['text'] for s in read(D.parent/'slop-20260926/scenes.json')}
 for r in rows:
  assert sha(r['text'].encode())==r['originalSha256']
  if r['provenance']=='real_output_excerpt': assert r['text'] in old[r['source']]
 assert collections.Counter(r['split'] for r in rows)=={'development':48,'holdout':24}
 assert all(n==12 for n in collections.Counter(r['family'] for r in rows).values())
 for group in {r['sourceGroup'] for r in rows}:
  assert len({r['split'] for r in rows if r['sourceGroup']==group})==1
 for field in ['split','family']:
  for v in {r[field] for r in rows}:
   subset=[r for r in rows if r[field]==v]
   assert sum(r['expectedIntervention'] for r in subset)*2==len(subset)
 for name,h in read(D/'freeze.json')['files'].items(): assert sha((D/name).read_bytes())==h,name
 assert sha((ROOT/'scripts/research/slop2-offline.py').read_bytes())==read(D/'prototype-freeze.json')['codeSha256']
 # protocol.prepare hashed decoded text (universal-newline normalization),
 # whereas freeze.py hashed raw bytes. Preserve both original hash contracts.
 for name,h in read(D/'protocol.json')['hashes'].items(): assert sha((D/name).read_text(encoding='utf8').encode())==h,name
 reviews=read(D/'agent-reviews.json'); reviews={r['id']:r for r in reviews}
 for c in calls:
  if not c['id'].endswith('-review'):
   frozen=read(D/('generation-requests.json' if c['id'].startswith('G') else 'editing-requests.json'))
   assert c['request']==frozen[c['id']], 'Dispatched prompt drift'
  assert sha(json.dumps(c['request'],ensure_ascii=False).encode())==c['dispatch']['requestHash']
  if c['id'].endswith('-review'):
   sid=c['id'].split('-')[0]
   assert reviews[sid]['reviewedAtEpoch']<c['dispatch']['time'], 'Agent review must precede model review'
   prefix=read(D/'protocol.json')['review']; prompt=c['request']['messages'][0]['content']
   assert prompt.startswith(prefix)
   payload=json.loads(prompt[len(prefix):]); assert set(payload['calibration'])=={'original','candidate'}
 for c in candidates:
  assert c['original']==next(t['text'] for t in read(D/'editing-tasks.json') if t['id']==c['id'])
  assert sha(c['original'].encode())==c['sourceHash']
  assert sha(c['revised'].encode())==c['candidateHash']
  if c['valid']:
   raw=c['original'].encode('utf-16-le'); ranges=[]
   for p in c['patches']:
    a,b=p['startUtf16'],p['endUtf16']; assert raw[a*2:b*2].decode('utf-16-le')==p['quote']
    ranges.append((a,b,p['replacement']))
   ranges.sort(); assert all(a[1]<=b[0] for a,b in zip(ranges,ranges[1:]))
   for a,b,replacement in reversed(ranges): raw=raw[:a*2]+replacement.encode('utf-16-le')+raw[b*2:]
   assert raw.decode('utf-16-le')==c['revised']
  else: assert c['original']==c['revised']
 features=[]
 for c in calls:
  if not c['id'].startswith('G'): continue
  text=c['response']['body']['content']; han=len(re.findall(r'[\u3400-\u4dbf\u4e00-\u9fff]',text))
  sentences=[s for s in re.split('[。！？]',text) if s.strip()]
  grams=[text[i:i+4] for i in range(max(0,len(text)-3))]
  features.append(dict(id=c['id'],han=han,lengthWithin550to750=550<=han<=750,
   sentenceLengths=[len(s) for s in sentences],distinct4=len(set(grams))/len(grams) if grams else None,
   caveat='Descriptive only; length-sensitive and not a quality score.'))
 save('generation-features.json',features)
 feature_map={r['id']:r for r in features}
 table=['# 逐例代理阅读结果','', '先读记录见 agent-reviews.json；模型结果另列，不覆盖代理原判。G 的 same/uncertain 表示相对目标的定性观察，缺失对照时不代表胜负。E 均与同一原稿比较。','',
 '| 单元/臂 | 硬约束 | 机械/声音/流畅/多样性 | 阅读判断 |','|---|---|---|---|']
 for r in reviews.values():
  if 'arms' not in r: continue
  for arm,v in r['arms'].items():
   ident=r['id']+'-'+arm; f=feature_map.get(ident)
   reason=v['reason']+(f" 汉字数 {f['han']}，长度达标：{f['lengthWithin550to750']}。" if f else '')
   table.append('| '+ident+' | '+v['hard']+' | '+' / '.join(v[k] for k in ['mechanical','voice','fluency','diversity'])+' | '+reason.replace('|','/').replace('\n',' ')+' |')
 table+=['','未执行：G3–G6 的 base/profile，因 G1/G2 同类关键内容破坏触发预先停止规则。缺失不填为失败样本，也不填为成功样本。',
  '', '每处编辑的原句、替换、理由与UTF-16位置在 experiment-candidates.json；每份全文和完整diff保留。']
 (D/'case-results.md').write_text('\n'.join(table)+'\n',encoding='utf8',newline='\n')
 shipping={x['id']:x['result']['requiresAiReview'] for x in read(D/'baseline-results.json')}
 structural={x['id']:bool(x['signals']) for x in read(D/'structural-results.json')}
 lines=['# 72片段逐例结果','', '标签为代理判断；完整原文/上下文与理由见 samples.json。命中是复核触发或结构观察，不是确定的文学问题。','',
 '| ID | 集合 / 来源 | 应干预 | 当前触发 / 结构观察 | 当前 / 结构结果 |','|---|---|---|---|---|']
 def outcome(y,p): return ('TP' if y else 'FP') if p else ('FN' if y else 'TN')
 for r in rows:
  k=r['id']; y=r['expectedIntervention']; a,b=shipping[k],structural[k]
  lines.append(f"| {k} | {r['split']} / {r['provenance']} | {y} | {a} / {b} | {outcome(y,a)} / {outcome(y,b)} |")
 (D/'sample-results.md').write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
 save('integrity.json',dict(runId=ledger['budget']['id'],calls=len(calls),limit=40,
  projectCredits=sum(float(c['accounting']['settled_amount'] or 0) for c in calls),
  transportFailures=sum(c['response']['status']!=200 for c in calls),samples=72,
  provenance=dict(collections.Counter(r['provenance'] for r in rows)),
  checks=['frozen inputs/prompts unchanged','source groups split disjoint','balanced labels',
  'every response reconciled to persistent budget and credit reservation','UTF-16 patch reconstruction and source hashes'],
  limitation='Agent labels; no independent human blind evaluation.'))
 print(json.dumps(read(D/'integrity.json'),ensure_ascii=False))
if __name__=='__main__': main()
