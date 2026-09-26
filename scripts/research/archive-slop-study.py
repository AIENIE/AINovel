"""Archive research evidence without credentials. No model calls or DB writes."""
import difflib
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'doc/research/slop-20260926'
RAW = ROOT / 'artifacts/slop-20260926'

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def sha(text):
    return hashlib.sha256(text.encode()).hexdigest()

def corpus():
    rows = []
    for mode in ('fast', 'crafted'):
        source = f'artifacts/closure-repair-20260922/draft-{mode}.md'
        text = (ROOT / source).read_text(encoding='utf-8-sig')
        for i, m in enumerate(re.finditer(r'^### ([^\n]+)\n(.*?)(?=^### |\Z)', text, re.M | re.S), 1):
            body = re.split(r'\n## ', m[2])[0].strip()
            if body == '（尚未生成）':
                continue
            rows.append(dict(id=f'{mode}:{i}', title=m[1], text=body,
                             source=source, originalSha256=sha(body), genre='悬疑', tone=''))
    rows += [s for s in read(DATA / 'scenes.json') if s['id'] in ('S5', 'S6')]
    target = DATA / 'reading-corpus.json'
    if target.exists():
        assert read(target) == rows, 'Corpus changed'
    else:
        save(target, rows)

def archive():
    ledger = read(RAW / 'ledger.json')
    rows = []
    for entry in ledger['calls']:
        key = entry['request_id']
        suffix = key.removeprefix('slop-20260926-v1-')
        request = read(RAW / f'{suffix}-request.json')
        response = read(RAW / f'{suffix}-response.json')
        assert json.loads(entry['request_json']) == request['messages']
        result = json.loads(entry['result_json'])
        assert result['content'] == response['body']['content']
        # Only explicit public accounting fields, never cookies/tokens/credit balances.
        rows.append(dict(attempt=entry['attempt'], requestId=key, model=entry['model'],
            status=entry['status'], reservationStatus=entry['reservation_status'],
            settledProjectCredits=entry['settled_amount'], promptTokens=entry['prompt_tokens'],
            completionTokens=entry['completion_tokens'], cacheTokens=entry['cache_tokens'],
            request=request, response=dict(status=response['status'], elapsedSeconds=response['elapsed'],
                content=response['body']['content'], usage=response['body']['usage'])))
    save(DATA / 'experiment-calls.json', dict(budget=ledger['budget'], calls=rows))
    candidates = []
    for path in sorted(RAW.glob('S*-candidate.json')):
        candidate = read(path)
        candidate['sceneId'] = path.name.split('-')[0]
        for patch in candidate['patches']:
            start = candidate['original'].index(patch['quote'])
            patch['startUtf16'] = len(candidate['original'][:start].encode('utf-16-le')) // 2
            patch['endUtf16'] = patch['startUtf16'] + len(patch['quote'].encode('utf-16-le')) // 2
        candidate['unifiedDiff'] = ''.join(difflib.unified_diff(
            candidate['original'].splitlines(True), candidate['revised'].splitlines(True),
            fromfile='original', tofile='candidate'))
        candidates.append(candidate)
    save(DATA / 'experiment-candidates.json', candidates)
    print(json.dumps(dict(calls=len(rows), budget=ledger['budget'], candidates=len(candidates))))

if __name__ == '__main__':
    import sys
    {'corpus': corpus, 'archive': archive}[sys.argv[1]]()
