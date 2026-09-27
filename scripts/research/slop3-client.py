"""One explicit call at a time; frozen inputs, per-pair reading, durable 40-call cap.

No automatic retry. Uncertain transport is reconciled from the persisted server
ledger. Credentials and runtime configuration never enter research documents.
"""
import hashlib, json, os, ssl, time, urllib.request, urllib.error
from collections import Counter
from pathlib import Path
import pymysql
ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'doc/research/slop3-20260926'; RAW=ROOT/'artifacts/slop3-20260926'
RUN='slop3-20260926-v1'; RAW.mkdir(parents=True,exist_ok=True)
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def save(p,x): p.write_text(json.dumps(x,ensure_ascii=False,indent=2,default=str)+'\n',encoding='utf8',newline='\n')
def sha(t): return hashlib.sha256(t.encode()).hexdigest()
def db():
 env={}
 for line in (Path(r'D:\project\aienie\aienie-runtime\private\app-secrets')/'ainovel-slop3-20260926.env').read_text(encoding='utf-8-sig').splitlines():
  if '=' in line and not line.lstrip().startswith('#'):
   k,v=line.split('=',1); env[k.strip()]=v.strip().strip('"').strip("'")
 assert env['AI_VALIDATION_RUN_ID']==RUN and env['AI_VALIDATION_MAXIMUM_CALLS']=='40'
 return pymysql.connect(host='localbase.testhut.top',port=23306,user=env['MYSQL_USER'],password=env['MYSQL_PASSWORD'],database='ainovel',charset='utf8mb4',connect_timeout=8,cursorclass=pymysql.cursors.DictCursor)
def snapshot():
 conn=db()
 with conn.cursor() as q:
  q.execute('SELECT id,used,call_limit FROM ai_validation_budgets WHERE id=%s',(RUN,)); budget=q.fetchone()
  q.execute('SELECT v.attempt,v.request_id,v.model,v.status,v.request_json,v.result_json,r.status reservation_status,r.settled_amount,r.prompt_tokens,r.completion_tokens,r.cache_tokens FROM ai_validation_calls v LEFT JOIN ai_credit_reservations r ON r.idempotency_key=v.request_id WHERE v.run_id=%s ORDER BY v.attempt',(RUN,)); calls=q.fetchall()
 conn.close(); save(RAW/'ledger.json',dict(budget=budget,calls=calls)); return budget,calls
def validate_freeze():
 p=read(DATA/'protocol.json'); assert p['runId']==RUN and p['maximumCalls']==40
 assert p['hashes'], 'Not sealed'
 for name,digest in p['hashes'].items(): assert sha((DATA/name).read_text(encoding='utf8'))==digest, 'Frozen input changed: '+name
 seal=read(DATA/'seal.json'); assert sha((DATA/'protocol.json').read_text(encoding='utf8'))==seal['protocolSha256'], 'Protocol changed'
 requests=read(DATA/'requests.json'); tasks=read(DATA/'tasks.json')
 header='本节叙事质量目标（请重点体现以下要素）：'
 for t in tasks:
  sid=t['id']; a=requests[sid+'-A1']; b=requests[sid+'-B1']
  assert a==requests[sid+'-A2'] and b==requests[sid+'-B2']
  assert a['modelId']==b['modelId']=='deepseek-flash' and a['messages'][1:]==b['messages'][1:]
  assert a['messages'][0]['content'].split(header)[0]==b['messages'][0]['content'].split(header)[0]
  assert t['context'] in a['messages'][1]['content'] and sha(t['context'])==t['contextSha256']
  for other in tasks:
   if other['id']!=sid: assert other['context'] not in a['messages'][1]['content'], 'Cross-scene context'
 return p
def readings(): return read(DATA/'agent-readings.json') if (DATA/'agent-readings.json').exists() else []
def stopped(records):
 counts={a:Counter(c for r in records for o in r['outputs'] if o['arm']==a for c in set(o['failureClasses'])) for a in ['A','B']}
 return {a:dict(c) for a,c in counts.items() if any(n>=2 for n in c.values())}
def output(name):
 recovery=RAW/f'{name}-recovery.json'; path=recovery if recovery.exists() else RAW/f'{name}-response.json'
 body=read(path)['body']; assert isinstance(body.get('content'),str) and body['content'].strip(), 'No complete output'
 return body['content']
def prepare(sid,phase,budget,calls):
 p=validate_freeze(); name=sid+'-'+phase
 assert not (RAW/f'{name}-started.json').exists() and not (RAW/f'{name}-response.json').exists(), 'Attempt exists; reconcile, never redispatch'
 assert budget and budget['call_limit']==40 and budget['used']<40, 'Budget exhausted'
 assert not any(c['status']=='STARTED' or c['reservation_status'] not in ['COMPLETED','REFUNDED','CANCELED','FAILED'] for c in calls), 'Unresolved call or settlement'
 records=readings(); stops=stopped(records)
 if phase=='review':
  assert sid in p['reviewScenes']
  assert all(any(r['pair']==sid+'-'+str(rep) for r in records) for rep in [1,2]), 'Read all outputs before model review'
  # Deterministic anonymous ordering frozen by scene parity, not quality.
  phases=['B2','A1','B1','A2'] if int(sid[1:])%2 else ['A2','B1','A1','B2']
  for a in phases:
   checks=[o for r in records for o in r['outputs'] if o['id']==sid+'-'+a]
   assert len(checks)==1 and checks[0]['fullTextRead'] and checks[0]['outputSha256']==sha(output(sid+'-'+a)), 'Incomplete or stale agent reading'
  candidates=[dict(label=label,text=output(sid+'-'+a)) for label,a in zip(['甲','乙','丙','丁'],phases)]
  source=next(t for t in read(DATA/'tasks.json') if t['id']==sid)
  task={k:source[k] for k in ['id','genre','title','context','intent','ordinaryDetails','minHan','maxHan']}
  controls=[{k:v for k,v in c.items() if k!='expected'} for c in read(DATA/'calibration.json')]
  body=dict(modelId='deepseek-flash',messages=[dict(role='user',content=p['reviewPrompt']+'\n'+json.dumps(dict(task=task,candidates=candidates,calibration=controls),ensure_ascii=False))])
 else:
  assert phase in ['A1','B1','A2','B2'] and phase[0] not in stops, 'Arm stopped'
  next_name=None
  for pair in p['order']:
   if any(r['pair']==pair['pair'] for r in records): continue
   for n in pair['calls']:
    if n.split('-')[1][0] in stops: continue
    if not (RAW/f'{n}-started.json').exists(): next_name=n; break
   # No further pair before this pair's reading, even when one arm was stopped.
   if next_name is None and all(n.split('-')[1][0] in stops for n in pair['calls']): continue
   break
  assert name==next_name, 'Wrong order or previous pair not read'
  # A previous response must be complete or ledger-confirmed failed.
  for c in calls:
   logical=c['request_id'].removeprefix(RUN+'-')
   if c['status']=='COMPLETED': output(logical)
  body=read(DATA/'requests.json')[name]
 return body
def call(sid,phase):
 budget,calls=snapshot(); body=prepare(sid,phase,budget,calls); name=sid+'-'+phase; key=RUN+'-'+name
 save(RAW/f'{name}-request.json',body)
 marker=RAW/f'{name}-started.json'
 # Exclusive create prevents duplicate dispatch even with two local processes.
 with marker.open('x',encoding='utf8') as f: json.dump(dict(key=key,beforeUsed=budget['used'],time=time.time(),requestHash=sha(json.dumps(body,ensure_ascii=False))),f)
 request=urllib.request.Request('https://localainovel.testhut.top/api/v1/ai/chat',data=json.dumps(body,ensure_ascii=False).encode(),method='POST',headers={'Content-Type':'application/json','Authorization':'Bearer '+os.environ['AINOVEL_RESEARCH_TOKEN'],'Idempotency-Key':key})
 start=time.monotonic()
 try:
  try:
   with urllib.request.urlopen(request,context=ssl.create_default_context(),timeout=240) as r: status,raw=r.status,r.read().decode()
  except urllib.error.HTTPError as e:
   with e: status,raw=e.code,e.read().decode()
  try: result_body=json.loads(raw)
  except json.JSONDecodeError: result_body=dict(error='NON_JSON_HTTP_RESPONSE',rawBody=raw)
  result=dict(status=status,elapsed=time.monotonic()-start,body=result_body)
 except Exception as e: result=dict(status=None,elapsed=time.monotonic()-start,body=dict(error=type(e).__name__))
 save(RAW/f'{name}-response.json',result); budget,_=snapshot()
 print(json.dumps(dict(name=name,status=result['status'],used=budget['used'],elapsed=round(result['elapsed'],2))),flush=True)
def reconcile(sid,phase):
 name=sid+'-'+phase; marker=read(RAW/f'{name}-started.json'); _,calls=snapshot()
 matches=[c for c in calls if c['request_id']==marker['key']]; assert len(matches)==1, 'Missing/ambiguous ledger; no retry'
 c=matches[0]; assert c['status']=='COMPLETED' and c['reservation_status']=='COMPLETED', 'Still unresolved or confirmed failure; no retry'
 body=json.loads(c['result_json']) if isinstance(c['result_json'],str) else c['result_json']
 assert isinstance(body.get('content'),str)
 save(RAW/f'{name}-recovery.json',dict(status=None,body=body,recovery=dict(source='ai_validation_calls.result_json',originalTransportRetained=True,requestId=marker['key'])))
 print('Recovered persisted output; zero dispatches.',flush=True)
if __name__=='__main__':
 import sys
 if sys.argv[1]=='verify': validate_freeze(); print('Freeze and prompt-isolation checks passed')
 elif sys.argv[1]=='seed':
  validate_freeze(); conn=db()
  with conn.cursor() as q: q.execute('INSERT INTO ai_validation_budgets (id,used,call_limit) VALUES (%s,0,40)',(RUN,))
  conn.commit(); conn.close(); print(snapshot()[0])
 elif sys.argv[1]=='snapshot': print(snapshot()[0])
 elif sys.argv[1]=='reconcile': reconcile(sys.argv[2],sys.argv[3])
