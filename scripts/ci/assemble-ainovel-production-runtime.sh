#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "${1:?missing repo}"&&pwd -P)"; out="$(cd "${2:?missing bundle}"&&pwd -P)"; fail(){ echo "AINovel production assembly failed: $*" >&2; exit 2; }; [[ "$out" != / && "$out" != "$repo" && ! -L "$out" ]]||fail unsafe
put(){ local s="$1" t="$2" m="$3"; [[ -f "$s"&&! -L "$s"&&! -L "$t" ]]||fail "$s"; mkdir -p "$(dirname "$t")"; install -m "$m" "$s" "$t"; [[ "$(sha256sum "$s"|awk '{print $1}')" = "$(sha256sum "$t"|awk '{print $1}')" ]]||fail digest; }
[[ -s "$out/backend/app.jar" && -f "$out/backend/app.jar" && ! -L "$out/backend/app.jar" ]]||fail 'real backend JAR is required'
[[ -s "$out/frontend/dist/index.html" && -f "$out/frontend/dist/index.html" && ! -L "$out/frontend/dist/index.html" ]]||fail 'real frontend dist is required'
put "$repo/backend/start-production-backend.sh" "$out/backend/start-production-backend.sh" 0555
put "$repo/backend/production-migration-entrypoint.sh" "$out/backend/production-migration-entrypoint.sh" 0555
put "$repo/scripts/docker/production-load-env-file.sh" "$out/docker/production-load-env-file.sh" 0555
put "$repo/scripts/ci/ainovel-production-runtime-compose.yml" "$out/docker-compose.yml" 0444
put "$repo/scripts/ci/production-runtime-contract.json" "$out/release/production-runtime-contract.json" 0444
put "$repo/scripts/ci/production-persistence-preflight.sh" "$out/release/production-persistence-preflight.sh" 0555
put "$repo/scripts/ci/images/ainovel-nginx.conf" "$out/frontend/nginx.conf" 0444
put "$repo/scripts/ci/production-migration-executor" "$out/release/production-migration-executor" 0555
grep -Eq '^[[:space:]]*listen[[:space:]]+10010;' "$out/frontend/nginx.conf"||fail 'frontend nginx must listen on 10010'
grep -Fq 'location = /healthz' "$out/frontend/nginx.conf"||fail 'frontend nginx must expose /healthz'
if grep -Eq '^[[:space:]]*listen[[:space:]]+(80|443)([[:space:];]|$)|^[[:space:]]*user[[:space:]]+root' "$out/frontend/nginx.conf";then fail 'frontend nginx requires privileged execution';fi
for migration in "$repo"/backend/src/main/resources/db/migration/V*__*.sql;do
  put "$migration" "$out/release/migrations/sql/$(basename "$migration")" 0444
done
python3 "$repo/scripts/ci/write-flyway-migration-ledger.py" "$repo" "$out/release/migrations/flyway-ledger.json"; chmod 0444 "$out/release/migrations/flyway-ledger.json"
python3 "$repo/scripts/ci/write-production-migration-artifacts.py" "$out" "$out/release/production-migration-artifacts.json"; chmod 0444 "$out/release/production-migration-artifacts.json"
python3 "$repo/scripts/ci/verify-production-runtime-contract.py" "$out/release/production-runtime-contract.json" "$out/docker-compose.yml" ai-novel "$out/backend/start-production-backend.sh" "$out/backend/production-migration-entrypoint.sh" "$out/release/production-migration-executor"
python3 - "$out" <<'PY'
import json,pathlib,sys
r=pathlib.Path(sys.argv[1]);m=r/'.project-manifest.json'
for p in r.rglob('*'):
    if p.is_symlink():raise SystemExit(f'symlink: {p}')
v=json.loads(m.read_text(encoding='utf-8')) if m.exists() else {'format':2,'project_key':'AINovel'};v['files']=sorted(p.relative_to(r).as_posix() for p in r.rglob('*') if p.is_file() and p!=m);m.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
PY
chmod 0444 "$out/.project-manifest.json"; echo 'Assembled AINovel production runtime bundle.'
