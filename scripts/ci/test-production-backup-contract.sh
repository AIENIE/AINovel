#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd -P)"
work_dir="$(mktemp -d)"
trap 'chmod -R u+w -- "$work_dir" 2>/dev/null || true; rm -rf -- "$work_dir"' EXIT
mkdir -p "$work_dir/bundle/backend" "$work_dir/bundle/frontend/dist"
printf 'compiled-backend-placeholder\n' >"$work_dir/bundle/backend/app.jar"
printf '<!doctype html>\n' >"$work_dir/bundle/frontend/dist/index.html"
bash "$repo_root/scripts/ci/assemble-ainovel-production-runtime.sh" "$repo_root" "$work_dir/bundle"
cmp -s "$repo_root/scripts/ci/images/ainovel-nginx.conf" "$work_dir/bundle/frontend/nginx.conf"
grep -Eq '^[[:space:]]*listen[[:space:]]+10010;' "$work_dir/bundle/frontend/nginx.conf"
grep -Fq 'location = /healthz' "$work_dir/bundle/frontend/nginx.conf"
python3 - "$work_dir/bundle/release/production-runtime-contract.json" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["backup"] == {
    "schema_version": "aienie-production-backup-contract-v1",
    "nightly": {
        "include": ["/srv/aienie-products/ai-novel/records"],
        "exclude": [
            {"source": "/srv/aienie-products/ai-novel/logs", "classification": "operational-log"}
        ],
    },
}
paths = contract["backup"]["nightly"]["include"]
assert all("env" not in path and "admin" not in path and "cap" not in path for path in paths)
manifest = json.loads((pathlib.Path(sys.argv[1]).parents[1] / ".project-manifest.json").read_text(encoding="utf-8"))
assert "frontend/nginx.conf" in manifest["files"]
for required in (
    "backend/app.jar",
    "backend/production-migration-entrypoint.sh",
    "release/migrations/flyway-ledger.json",
    "release/production-migration-artifacts.json",
    "release/production-migration-executor",
):
    assert required in manifest["files"], required
assert contract["protected_config_overlay_contract"]["allowed_files"] == [{
    "target_path": "env.txt",
    "file_mode": "0600",
    "owner": "runtime_identity",
    "consumers": ["backend-runtime", "production-migration-executor"],
}]
PY
! grep -Fq 'container_name:' "$work_dir/bundle/docker-compose.yml"
cp "$work_dir/bundle/release/production-runtime-contract.json" "$work_dir/overlay-tamper.json"
chmod u+w "$work_dir/overlay-tamper.json"
python3 - "$work_dir/overlay-tamper.json" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1]);v=json.loads(p.read_text(encoding='utf-8'))
v['protected_config_overlay_contract']['allowed_files'][0]['target_path']='docker-compose.yml'
p.write_text(json.dumps(v),encoding='utf-8')
PY
if python3 "$repo_root/scripts/ci/verify-production-runtime-contract.py" \
  "$work_dir/overlay-tamper.json" "$work_dir/bundle/docker-compose.yml" ai-novel \
  "$work_dir/bundle/backend/start-production-backend.sh" \
  "$work_dir/bundle/backend/production-migration-entrypoint.sh" \
  "$work_dir/bundle/release/production-migration-executor" >/dev/null 2>&1;then
  echo 'AINovel production verifier accepted artifact overwrite authority.' >&2
  exit 1
fi
printf 'AINovel production backup contract test passed.\n'
