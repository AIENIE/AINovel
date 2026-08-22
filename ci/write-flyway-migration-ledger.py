#!/usr/bin/env python3
import hashlib,json,pathlib,re,sys
repo=pathlib.Path(sys.argv[1]).resolve(); target=pathlib.Path(sys.argv[2]); root=repo/'backend/src/main/resources/db/migration'; entries=[]; seen=set()
for path in root.glob('V*__*.sql'):
    if path.is_symlink() or not path.is_file(): raise SystemExit(f'unsafe migration: {path}')
    m=re.fullmatch(r'V([0-9]+)__(.+)\.sql',path.name)
    if not m or int(m.group(1)) in seen: raise SystemExit(f'invalid migration: {path.name}')
    seen.add(int(m.group(1))); entries.append({'version':m.group(1),'path':f'classpath:db/migration/{path.name}','sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
entries.sort(key=lambda x:int(x['version']))
if [int(x['version']) for x in entries] != list(range(1,15)): raise SystemExit('AINovel Flyway chain must remain contiguous V1..V14')
target.parent.mkdir(parents=True,exist_ok=True); target.write_text(json.dumps({'schema_version':'aienie-flyway-ledger-v1','canonical_component_id':'ai-novel','migrations':entries},indent=2)+'\n',encoding='utf-8',newline='\n')
