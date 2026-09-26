"""Export auditable research evidence and separate metrics; never dispatches."""
import difflib, importlib.util, json, re, statistics
from pathlib import Path
spec=importlib.util.spec_from_file_location('study',Path(__file__).with_name('slop3-client.py'))
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
m.validate_freeze()
ledger=m.read(m.RAW/'ledger.json'); records=m.readings(); p=m.read(m.DATA/'protocol.json')
assert not any(c['status']=='STARTED' for c in ledger['calls']), 'Unresolved call'
def clean(value):
 if isinstance(value,dict): return {k:clean(v) for k,v in value.items() if k not in ['remainingCredits','accessToken','refreshToken','Authorization','password']}
 if isinstance(value,list): return [clean(v) for v in value]
 return value
experiments=[]; outputs=[]; texts=['# 原始输出全文\n\n仅研究文本，未编辑；逐例判断见 `agent-readings.json`。\n']
for call in ledger['calls']:
 name=call['request_id'].removeprefix(m.RUN+'-'); req=m.read(m.RAW/f'{name}-request.json')
 response=m.read(m.RAW/f'{name}-response.json')
 recovery=m.read(m.RAW/f'{name}-recovery.json') if (m.RAW/f'{name}-recovery.json').exists() else None
 item=dict(name=name,request=req,requestSha256=m.sha(json.dumps(req,ensure_ascii=False)),started=m.read(m.RAW/f'{name}-started.json'),response=clean(response),recovery=clean(recovery))
 item['ledger']={k:json.loads(v) if k in ['request_json','result_json'] and isinstance(v,str) else v for k,v in call.items()}
 item['ledger']=clean(item['ledger']); experiments.append(item)
 if not name.endswith('-review') and call['status']=='COMPLETED':
  text=m.output(name)
  sentences=[len(re.findall(r'[\u3400-\u4dbf\u4e00-\u9fff]',s)) for s in re.split('[。！？]',text)]
  sentences=[n for n in sentences if n]
  outputs.append(dict(id=name,hanCount=len(re.findall(r'[\u3400-\u4dbf\u4e00-\u9fff]',text)),paragraphs=len(text.split('\n\n')),sentenceHanMedian=statistics.median(sentences),shortSentenceCount=sum(n<=10 for n in sentences),sentenceCount=len(sentences),formCounts={s:text.count(s) for s in ['没有','不是','像','仿佛']},textSha256=m.sha(text)))
  texts.append('\n## '+name+'\n\n'+text+'\n')
  matches=[o for r in records for o in r['outputs'] if o['id']==name]
  assert len(matches)==1 and matches[0]['outputSha256']==m.sha(text) and matches[0]['fullTextRead'], 'Missing full reading'
  for claim in matches[0]['claims']: assert claim['quote'] in text, 'Unmatched evidence quote'
m.save(m.DATA/'experiments.json',experiments)
m.save(m.DATA/'output-statistics.json',outputs)
(m.DATA/'outputs.md').write_text('\n'.join(texts),encoding='utf8')
requests=m.read(m.DATA/'requests.json'); diffs=['# A/B 提示差异\n\n同一场景两次重复使用同一请求体，幂等键不同。这里只列每场首份 system 差异；user、model 与其他字段由工具逐项核验。\n']
for t in m.read(m.DATA/'tasks.json'):
 sid=t['id']; a=requests[sid+'-A1']['messages'][0]['content']; b=requests[sid+'-B1']['messages'][0]['content']
 diffs.append('\n## '+sid+'\n\n```diff\n'+''.join(difflib.unified_diff(a.splitlines(True),b.splitlines(True),fromfile=sid+'-A',tofile=sid+'-B',n=1))+'```\n')
(m.DATA/'prompt-diffs.md').write_text('\n'.join(diffs),encoding='utf8')
stops=m.stopped(records)
planned=[n for pair in p['order'] for n in pair['calls']]
done={e['name'] for e in experiments}
skips=[dict(name=n,reason='arm_stopped_after_repeated_hard_failure' if n.split('-')[1][0] in stops else 'not_executed') for n in planned if n not in done]
for sid in p['reviewScenes']:
 if sid+'-review' not in done: skips.append(dict(name=sid+'-review',reason='four_outputs_incomplete_after_stop' if not all(sid+'-'+x in done for x in ['A1','B1','A2','B2']) else 'not_executed'))
armstats={}
for arm in ['A','B']:
 os=[o for r in records for o in r['outputs'] if o['arm']==arm]
 armstats[arm]=dict(outputs=len(os),hardPass=sum(o['hard']=='pass' for o in os),hardFail=sum(o['hard']=='fail' for o in os),hardUncertain=sum(o['hard']=='uncertain' for o in os),lengthAdherent=sum(550<=o['hanCount']<=750 for o in os))
pairwins={x:sum(r['winner']==x for r in records) for x in ['A','B','tie','uncertain','missing']}
successful_scenes=[sid for sid in [t['id'] for t in m.read(m.DATA/'tasks.json')] if len(rs:=[r for r in records if r['pair'].split('-')[0]==sid])==2 and all(r['winner'] in ['B','tie'] for r in rs) and any(r['winner']=='B' for r in rs)]
summary=dict(runId=m.RUN,budget=ledger['budget'],calls=len(experiments),costProjectCredits=sum(float(e['ledger']['settled_amount'] or 0) for e in experiments),generationCalls=sum(not e['name'].endswith('-review') for e in experiments),reviewCalls=sum(e['name'].endswith('-review') for e in experiments),retries=0,armStatistics=armstats,pairOutcomes=pairwins,scenesBothNonWorseAndOneBetter=successful_scenes,stoppedArms=stops,skipped=skips,
 decision='保留现状；继续验证候选假设；不替换生产提示',scope='Agent exploratory assessment, not G2 human or product L4',
 disclosure='Account balances and credentials excluded. Full model messages, content, transport failures and server accounting retained. No sampling seed exposed.')
m.save(m.DATA/'summary.json',summary)
print(json.dumps({k:v for k,v in summary.items() if k!='skipped'},ensure_ascii=False,indent=2))
