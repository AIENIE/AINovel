#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd -P)"
jenkins_scripts="${1:-/opt/jenkins-scripts}"
builder="$jenkins_scripts/build_component_bundle.sh"
legacy="$jenkins_scripts/build_legacy_project_bundle.sh"
flattener="$jenkins_scripts/flatten_project_bundle.sh"

for helper in "$builder" "$legacy" "$flattener"; do
  [[ -f "$helper" && ! -L "$helper" ]] || {
    printf 'Required Jenkins bundle helper is missing or unsafe: %s\n' "$helper" >&2
    exit 2
  }
done
[[ -f "$repo_root/backend/target/ai-novel-backend-0.1.0.jar" ]] || {
  echo 'Build the backend JAR before the real bundle integration test.' >&2
  exit 2
}
[[ -f "$repo_root/frontend/dist/index.html" ]] || {
  echo 'Build frontend/dist before the real bundle integration test.' >&2
  exit 2
}

probe="$(mktemp -d)"
cleanup() {
  chmod -R u+w -- "$probe" 2>/dev/null || true
  rm -rf -- "$probe"
}
trap cleanup EXIT

AIENIE_CI_PHASE=build \
AIENIE_CI_NETWORK_MODE=offline \
TMPDIR="$probe" \
WORKSPACE="$repo_root" \
COMPONENT_BUNDLE_BUILDER="$builder" \
  bash "$legacy" AINovel "$probe/legacy"
bash "$flattener" AINovel "$probe/legacy" "$probe/output"
bash "$repo_root/scripts/ci/assemble-ainovel-runtime.sh" "$repo_root" "$probe/output"

test -x "$probe/output/backend/start-backend.sh"
test -x "$probe/output/docker/staging-load-env-file.sh"
grep -Fq 'entrypoint: ["/app/bin/start-backend.sh"]' "$probe/output/docker-compose.yml"
grep -Fq 'command: ["/app/env.txt"]' "$probe/output/docker-compose.yml"
grep -Fq '/api/actuator/health/readiness' "$probe/output/docker-compose.yml"
grep -Fq '127.0.0.1:11041:11041' "$probe/output/docker-compose.yml"
grep -Fq '127.0.0.1:11040:10010' "$probe/output/docker-compose.yml"
grep -Fq 'PORT: "11041"' "$probe/output/docker-compose.yml"
if grep -Fq '10011' "$probe/output/docker-compose.yml"; then
  echo 'Real flattened bundle retained the obsolete AINovel backend container port.' >&2
  exit 1
fi
if grep -Eq '(BACKEND|FRONTEND)_PORT' \
  "$probe/output/docker-compose.yml"; then
  echo 'Real flattened bundle retained a wildcard host port publication.' >&2
  exit 1
fi
if grep -Eq 'JAVA_OPTS|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS' \
  "$probe/output/docker-compose.yml"; then
  echo 'Real flattened bundle retained a forbidden JVM option channel.' >&2
  exit 1
fi
python3 - "$probe/output/.project-manifest.json" <<'PY'
import json
import sys

value = json.load(open(sys.argv[1], encoding="utf-8"))
assert value["project_key"] == "AINovel"
for expected in (
    "backend/start-backend.sh",
    "docker/staging-load-env-file.sh",
    "docker-compose.yml",
):
    assert expected in value["files"], expected
PY

echo 'Real Jenkins helper/flattener runtime integration passed.'
