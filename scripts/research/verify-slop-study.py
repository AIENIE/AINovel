"""Validate frozen data, literal evidence, patch scope and paid-call accounting offline."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / 'doc/research/slop-20260926'
def read(name):
    return json.loads((D / name).read_text(encoding='utf-8-sig'))
def sha(text):
    return hashlib.sha256(text.encode()).hexdigest()
def content(text):
    if text.strip().startswith('```'):
        text = text.strip().split('\n', 1)[1].rsplit('```', 1)[0]
    return json.loads(text)

freeze = read('freeze.json')
for name, key in [('samples.json','samplesSha256'),('scenes.json','scenesSha256')]:
    # Freeze producer hashes UTF-8 JSON text with universal-newline normalization.
    assert sha((D / name).read_text(encoding='utf-8')) == freeze[key]
samples, scenes = read('samples.json'), read('scenes.json')
corpus = {x['id']: x for x in read('reading-corpus.json')}
scene_map = {x['id']: x for x in scenes}
assert len(samples) == 24 and len(scenes) == 6 and len(corpus) == 9
assert sum(s['split'] == 'development' for s in samples) == 16
assert sum(s['split'] == 'holdout' for s in samples) == 8
assert len({s['family'] for s in samples}) == 6
for s in samples + scenes + list(corpus.values()):
    assert sha(s['text']) == s['originalSha256'], s['id']
for s in samples:
    if s['provenance'] == 'real_model_excerpt':
        source = (scene_map | corpus)[s['source']]['text']
        assert s['text'] in source, s['id']

results = {x['id']: x for x in read('local-sample-results.json')}
metrics = {}
table = ['# 24 个冻结片段的逐例结果', '',
    '标签为代理编辑判断；原句、理由及来源见 samples.json，全文上下文见 scenes.json / reading-corpus.json。触发定义为 requiresAiReview=true，低风险提示不计阳性。', '',
    '| ID | 类别 | 集合 | 来源 | 期望介入 | 风险 | 触发 | 结果 |',
    '|---|---|---|---|---|---:|---|---|']
for split in ('all','development','holdout'):
    counts = dict(tp=0,fp=0,tn=0,fn=0)
    for s in samples:
        if split != 'all' and s['split'] != split: continue
        expected = s['expectedIntervention']; predicted = results[s['id']]['result']['requiresAiReview']
        label = ('tp' if expected else 'fp') if predicted else ('fn' if expected else 'tn')
        counts[label] += 1
        if split == 'all':
            r = results[s['id']]['result']
            table.append(f"| {s['id']} | {s['family']} | {s['split']} | {s['provenance']} | {expected} | {r['overallRiskScore']} | {predicted} | {label.upper()} |")
    tp, fp, tn, fn = (counts[k] for k in ('tp','fp','tn','fn'))
    metrics[split] = dict(counts, precision=tp/(tp+fp) if tp+fp else None,
        recall=tp/(tp+fn), accuracy=(tp+tn)/(tp+fp+tn+fn))
assert metrics == read('metrics.json')
(D / 'sample-results.md').write_text('\n'.join(table)+'\n', encoding='utf-8')

experiment = read('experiment-calls.json')
calls = experiment['calls']
assert experiment['budget']['used'] == len(calls) <= 20
assert experiment['budget']['id'] == freeze['paidRunId']
assert experiment['budget']['call_limit'] == 20
assert len({c['requestId'] for c in calls}) == len(calls)
assert sorted(c['attempt'] for c in calls) == list(range(1,len(calls)+1))
diagnosed = set()
phase_counts = {}
for c in calls:
    assert c['model'] == freeze['model']
    assert c['status'] == 'COMPLETED' and c['reservationStatus'] == 'COMPLETED'
    assert c['response']['status'] == 200
    assert c['promptTokens'] == c['response']['usage']['inputTokens']
    assert c['completionTokens'] == c['response']['usage']['outputTokens']
    sid, phase = c['requestId'].removeprefix(freeze['paidRunId']+'-').split('-')
    phase_counts[phase] = phase_counts.get(phase,0)+1
    prompt = c['request']['messages'][0]['content']
    assert scene_map[sid]['text'] in prompt, (sid, phase, 'source omitted/truncated')
    if phase == 'diagnosis':
        diagnosed.add(sid)
        for issue in content(c['response']['content'])['issues']:
            assert scene_map[sid]['text'].count(issue['quote']) == 1, (sid, issue['id'])
assert diagnosed == set(scene_map)
assert phase_counts == dict(diagnosis=6,rewrite=4,review=2)

for candidate in read('experiment-candidates.json'):
    source = scene_map[candidate['sceneId']]['text']
    assert candidate['original'] == source
    edits = []
    for p in candidate['patches']:
        assert source.count(p['quote']) == 1
        a = source.index(p['quote']); b = a+len(p['quote'])
        assert len(source[:a].encode('utf-16-le'))//2 == p['startUtf16']
        assert len(source[:b].encode('utf-16-le'))//2 == p['endUtf16']
        edits.append((a,b,p['replacement']))
    edits.sort()
    assert len(edits) <= 4 and all(a[1] <= b[0] for a,b in zip(edits,edits[1:]))
    revised = source
    for a,b,replacement in reversed(edits): revised = revised[:a]+replacement+revised[b:]
    assert revised == candidate['revised']
    assert sha(source) == candidate['originalSha256']
    assert sha(revised) == candidate['revisedSha256']
    reviews = [c for c in calls if c['requestId'].endswith(candidate['sceneId']+'-review')]
    if candidate['patches']:
        assert len(reviews) == 1 and revised in reviews[0]['request']['messages'][0]['content']

print(json.dumps(dict(status='PASS', samples=24, development=16, holdout=8,
    corpus=9, scenes=6, calls=len(calls), settledProjectCredits=sum(c['settledProjectCredits'] for c in calls)),ensure_ascii=False))
