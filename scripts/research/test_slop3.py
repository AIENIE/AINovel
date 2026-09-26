"""Offline safety checks; never invokes the model, database, or business tests."""
import importlib.util, io, json, shutil, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('study',Path(__file__).with_name('slop3-client.py'))
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
class StudyTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory(dir=m.ROOT/'artifacts'); self.root=Path(self.temp.name)
  self.data=self.root/'data'; shutil.copytree(m.DATA,self.data)
  (self.data/'agent-readings.json').unlink(missing_ok=True)
  self.raw=self.root/'raw'; self.raw.mkdir()
  self.p1=patch.object(m,'DATA',self.data); self.p2=patch.object(m,'RAW',self.raw); self.p1.start(); self.p2.start()
  self.budget=dict(used=0,call_limit=40)
 def tearDown(self): self.p1.stop(); self.p2.stop(); self.temp.cleanup()
 def prepare(self,phase='A1',calls=None): return m.prepare('G1',phase,self.budget,calls or [])
 def test_goal_only_and_isolation(self):
  m.validate_freeze(); self.assertEqual(self.prepare()['modelId'],'deepseek-flash')
 def test_changed_frozen_input_blocked(self):
  with (self.data/'tasks.json').open('a',encoding='utf8') as f: f.write(' ')
  with self.assertRaises(AssertionError): self.prepare()
 def test_changed_protocol_blocked(self):
  with (self.data/'protocol.json').open('a',encoding='utf8') as f: f.write(' ')
  with self.assertRaises(AssertionError): self.prepare()
 def test_exhausted_budget(self):
  self.budget['used']=40
  with self.assertRaises(AssertionError): self.prepare()
 def test_idempotency_marker_blocks(self):
  m.save(self.raw/'G1-A1-started.json',{})
  with self.assertRaises(AssertionError): self.prepare()
 def test_wrong_order(self):
  with self.assertRaises(AssertionError): self.prepare('B1')
 def test_read_before_next_pair(self):
  for arm in ['A1','B1']: m.save(self.raw/f'G1-{arm}-started.json',{})
  with self.assertRaises(AssertionError): m.prepare('G3','B1',self.budget,[])
 def test_unresolved_response_blocks(self):
  with self.assertRaises(AssertionError): self.prepare(calls=[dict(status='STARTED',reservation_status='RESERVED')])
 def test_settlement_unresolved_blocks(self):
  with self.assertRaises(AssertionError): self.prepare(calls=[dict(status='COMPLETED',reservation_status='RESERVED')])
 def test_stopped_arm(self):
  records=[dict(pair='x'+str(i),outputs=[dict(arm='A',failureClasses=['new_critical_fact'])]) for i in [1,2]]
  m.save(self.data/'agent-readings.json',records)
  with self.assertRaises(AssertionError): self.prepare()
  self.assertEqual(self.prepare('B1')['modelId'],'deepseek-flash')
 def test_recovery_zero_dispatch_preserves_failure(self):
  key=m.RUN+'-G1-A1'; m.save(self.raw/'G1-A1-started.json',dict(key=key))
  transport=dict(status=502,body=dict(rawBody='<html>error</html>')); m.save(self.raw/'G1-A1-response.json',transport)
  c=dict(request_id=key,status='COMPLETED',reservation_status='COMPLETED',result_json=json.dumps(dict(content='完整尾段。')))
  with patch.object(m,'snapshot',return_value=(self.budget,[c])), patch.object(m.urllib.request,'urlopen') as http:
   m.reconcile('G1','A1'); http.assert_not_called()
  self.assertEqual(m.output('G1-A1'),'完整尾段。'); self.assertEqual(m.read(self.raw/'G1-A1-response.json'),transport)
 def test_ambiguous_recovery_blocked(self):
  m.save(self.raw/'G1-A1-started.json',dict(key=m.RUN+'-G1-A1'))
  with patch.object(m,'snapshot',return_value=(self.budget,[])):
   with self.assertRaises(AssertionError): m.reconcile('G1','A1')
 def test_model_review_requires_prior_reading(self):
  with self.assertRaises(AssertionError): m.prepare('G1','review',self.budget,[])
 def test_review_anonymity_and_stale_reading(self):
  records=[]
  for rep in [1,2]:
   os=[]
   for arm in ['A','B']:
    name=f'G1-{arm}{rep}'; text='原文'+name
    m.save(self.raw/f'{name}-response.json',dict(body=dict(content=text)))
    os.append(dict(id=name,arm=arm,failureClasses=[],fullTextRead=True,outputSha256=m.sha(text)))
   records.append(dict(pair=f'G1-{rep}',outputs=os))
  m.save(self.data/'agent-readings.json',records)
  body=m.prepare('G1','review',self.budget,[])
  self.assertNotIn('sceneGoals',body['messages'][0]['content'])
  self.assertNotIn('expected',body['messages'][0]['content'])
  records[0]['outputs'][0]['outputSha256']='stale'; m.save(self.data/'agent-readings.json',records)
  with self.assertRaises(AssertionError): m.prepare('G1','review',self.budget,[])
 def test_non_json_response_is_preserved_and_never_redispatched(self):
  error=m.urllib.error.HTTPError('https://example.invalid',502,'gateway',{},io.BytesIO(b'<html>upstream failed</html>'))
  with patch.object(m,'snapshot',return_value=(self.budget,[])), patch.object(m.urllib.request,'urlopen',side_effect=error) as http, patch.dict(m.os.environ,{'AINOVEL_RESEARCH_TOKEN':'offline-test-placeholder'}):
   m.call('G1','A1')
   with self.assertRaises(AssertionError): m.call('G1','A1')
   self.assertEqual(http.call_count,1)
  response=m.read(self.raw/'G1-A1-response.json')
  self.assertEqual(response['status'],502)
  self.assertEqual(response['body']['error'],'NON_JSON_HTTP_RESPONSE')
if __name__=='__main__': unittest.main(verbosity=2)
