#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "${1:?missing repository root}" && pwd -P)"
output_dir="$(cd "${2:?missing flattened bundle}" && pwd -P)"

fail() {
  printf 'AINovel runtime bundle assembly failed: %s\n' "$*" >&2
  exit 2
}

[[ "$output_dir" != / && "$output_dir" != "$repo_root" && ! -L "$output_dir" ]] \
  || fail 'unsafe flattened bundle directory'
[[ -f "$output_dir/backend/app.jar" && ! -L "$output_dir/backend/app.jar" ]] \
  || fail 'flattened bundle is missing the regular backend JAR'
[[ -d "$output_dir/frontend/dist" && ! -L "$output_dir/frontend/dist" ]] \
  || fail 'flattened bundle is missing the regular frontend dist tree'

install_exact() {
  local source="$1" target="$2" mode="$3" source_hash target_hash
  [[ -f "$source" && ! -L "$source" ]] || fail "tracked runtime file is missing or unsafe: $source"
  [[ ! -L "$target" ]] || fail "runtime target must not be a link: $target"
  mkdir -p -- "$(dirname "$target")"
  source_hash="$(sha256sum -- "$source" | awk '{print $1}')"
  install -m "$mode" -- "$source" "$target"
  target_hash="$(sha256sum -- "$target" | awk '{print $1}')"
  [[ "$source_hash" == "$target_hash" ]] || fail "runtime file digest changed during assembly: $target"
}

install_exact "$repo_root/backend/start-backend.sh" \
  "$output_dir/backend/start-backend.sh" 0555
install_exact "$repo_root/scripts/docker/staging-load-env-file.sh" \
  "$output_dir/docker/staging-load-env-file.sh" 0555
install_exact "$repo_root/scripts/ci/ainovel-runtime-compose.yml" \
  "$output_dir/docker-compose.yml" 0444
install_exact "$repo_root/scripts/ci/staging-oci-role-contract.json" \
  "$output_dir/release/staging-oci-role-contract.json" 0444

python3 "$repo_root/scripts/ci/verify-staging-oci-role-contract.py" \
  "$output_dir/release/staging-oci-role-contract.json" "$output_dir/docker-compose.yml" ai-novel

python3 - "$output_dir" <<'PY'
import json
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
compose = (root / "docker-compose.yml").read_text(encoding="utf-8")
for forbidden in ("JAVA_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
    if forbidden in compose:
        raise SystemExit(f"release Compose contains forbidden JVM option channel: {forbidden}")
required = (
    "AINOVEL_BACKEND_IMAGE",
    "AINOVEL_FRONTEND_IMAGE",
    'entrypoint: ["/app/bin/start-backend.sh"]',
    'command: ["/app/env.txt"]',
    "source: ./backend/start-backend.sh",
    "target: /app/bin/start-backend.sh",
    "source: ./docker/staging-load-env-file.sh",
    "target: /app/bin/staging-load-env-file.sh",
    "/api/actuator/health/readiness",
    "/run/aienie/trust/staging-root.pem",
    "condition: service_healthy",
)
for marker in required:
    if marker not in compose:
        raise SystemExit(f"release Compose is missing runtime contract marker: {marker}")
for forbidden in ("build:", "extra_hosts", "host-gateway", "/home/"):
    if forbidden in compose:
        raise SystemExit(f"release Compose contains local or mutable authority: {forbidden}")
if "env_file:" in compose or re.search(r"\$\{[^}]*(?:PORT|HOST|ROOT)[^}]*\}", compose):
    raise SystemExit("release Compose contains ambient environment authority")

for path in root.rglob("*"):
    if path.is_symlink():
        raise SystemExit(f"release bundle contains a symbolic link: {path.relative_to(root)}")

manifest_path = root / ".project-manifest.json"
files = sorted(
    path.relative_to(root).as_posix()
    for path in root.rglob("*")
    if path.is_file() and path != manifest_path
)
manifest = {"format": 2, "project_key": "AINovel", "files": files}
manifest_path.write_text(
    json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
    newline="\n",
)
PY
chmod 0444 -- "$output_dir/.project-manifest.json"

printf 'Assembled tracked AINovel runtime launcher contract.\n'
