#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "${1:?missing repo}"&&pwd -P)"; out="$(cd "${2:?missing bundle}"&&pwd -P)"; fail(){ echo "AINovel production assembly failed: $*" >&2; exit 2; }; [[ "$out" != / && "$out" != "$repo" && ! -L "$out" ]]||fail unsafe
put(){ local s="$1" t="$2" m="$3"; [[ -f "$s"&&! -L "$s"&&! -L "$t" ]]||fail "$s"; mkdir -p "$(dirname "$t")"; install -m "$m" "$s" "$t"; [[ "$(sha256sum "$s"|awk '{print $1}')" = "$(sha256sum "$t"|awk '{print $1}')" ]]||fail digest; }
put "$repo/backend/start-production-backend.sh" "$out/backend/start-production-backend.sh" 0555
put "$repo/docker/production-load-env-file.sh" "$out/docker/production-load-env-file.sh" 0555
put "$repo/ci/ainovel-production-runtime-compose.yml" "$out/docker-compose.yml" 0444
put "$repo/ci/production-runtime-contract.json" "$out/release/production-runtime-contract.json" 0444
put "$repo/ci/production-persistence-preflight.sh" "$out/release/production-persistence-preflight.sh" 0555
python3 "$repo/ci/write-flyway-migration-ledger.py" "$repo" "$out/release/migrations/flyway-ledger.json"; chmod 0444 "$out/release/migrations/flyway-ledger.json"
python3 "$repo/ci/verify-production-runtime-contract.py" "$out/release/production-runtime-contract.json" "$out/docker-compose.yml" ai-novel "$out/backend/start-production-backend.sh"
python3 - "$out" <<'PY'
import json,pathlib,sys
r=pathlib.Path(sys.argv[1]);m=r/'.project-manifest.json'
for p in r.rglob('*'):
    if p.is_symlink():raise SystemExit(f'symlink: {p}')
v=json.loads(m.read_text(encoding='utf-8')) if m.exists() else {'format':2,'project_key':'AINovel'};v['files']=sorted(p.relative_to(r).as_posix() for p in r.rglob('*') if p.is_file() and p!=m);m.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
PY
chmod 0444 "$out/.project-manifest.json"; echo 'Assembled AINovel production runtime bundle.'
