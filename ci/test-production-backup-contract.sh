#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd -P)"
work_dir="$(mktemp -d)"
trap 'chmod -R u+w -- "$work_dir" 2>/dev/null || true; rm -rf -- "$work_dir"' EXIT
mkdir -p "$work_dir/bundle/components"
printf 'compiled-component-placeholder\n' >"$work_dir/bundle/components/backend.bin"
bash "$repo_root/ci/assemble-ainovel-production-runtime.sh" "$repo_root" "$work_dir/bundle"
cmp -s "$repo_root/ci/images/ainovel-nginx.conf" "$work_dir/bundle/frontend/nginx.conf"
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
PY
printf 'AINovel production backup contract test passed.\n'
