#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd -P)"

assert_host_loopback_compose() {
  local compose_file="$1"
  grep -Fq '127.0.0.1:11041:11041' "$compose_file" \
    && grep -Fq '127.0.0.1:11040:10010' "$compose_file" \
    && grep -Fq 'PORT: "11041"' "$compose_file" \
    && ! grep -Fq '10011' "$compose_file" \
    && ! grep -Eq '(BACKEND|FRONTEND)_PORT' "$compose_file"
}

work_dir="$(mktemp -d)"
trap 'chmod -R u+w -- "$work_dir" 2>/dev/null || true; rm -rf -- "$work_dir"' EXIT
fake_bin="$work_dir/bin"
mkdir -p "$fake_bin"

real_python=""
if command -v python3 >/dev/null 2>&1 && python3 --version >/dev/null 2>&1; then
  real_python="$(command -v python3)"
elif command -v python >/dev/null 2>&1 && python --version >/dev/null 2>&1; then
  real_python="$(command -v python)"
else
  echo 'A working Python 3 interpreter is required for the CI contract test.' >&2
  exit 1
fi

write_fake() {
  local name="$1"
  shift
  printf '#!/usr/bin/env bash\nset -euo pipefail\n%s\n' "$*" >"$fake_bin/$name"
  chmod +x "$fake_bin/$name"
}

write_fake python3 "exec \"$real_python\" \"\$@\""
write_fake uname 'case "${1:-}" in -s) echo Linux;; -m) echo x86_64;; *) echo Linux;; esac'
write_fake java 'echo "openjdk version 25-test" >&2'
write_fake mvn 'if [[ " $* " == *" --version "* ]]; then echo "Apache Maven 3.9.99-test"; else mkdir -p "$AIENIE_CI_CACHE_DIR/maven"; printf "%s" "$AIENIE_CI_PHASE" >"$AIENIE_CI_CACHE_DIR/maven/$AIENIE_CI_PHASE.bin"; fi'
write_fake node 'if [[ "${1:-}" == "--version" ]]; then echo v22.23.2; else exit 0; fi'
write_fake npm 'if [[ "${1:-}" == "--version" ]]; then echo 10.99.0-test; else mkdir -p "$AIENIE_CI_CACHE_DIR/npm"; printf "%s" "$AIENIE_CI_PHASE" >"$AIENIE_CI_CACHE_DIR/npm/$AIENIE_CI_PHASE.bin"; fi'
write_fake pnpm 'if [[ "${1:-}" == "--version" ]]; then echo 11.22.0; else mkdir -p "$AIENIE_CI_CACHE_DIR/pnpm"; printf "%s" "$AIENIE_CI_PHASE" >"$AIENIE_CI_CACHE_DIR/pnpm/$AIENIE_CI_PHASE.bin"; fi'
write_fake trivy 'exit 0'
write_fake bundle-helper 'mkdir -p "$2/components/backend" "$2/components/frontend/dist"; printf jar >"$2/components/backend/app.jar"; printf html >"$2/components/frontend/dist/index.html"'
write_fake flatten-helper 'rm -rf -- "$3"; mkdir -p "$3/backend" "$3/frontend/dist"; cp -- "$2/components/backend/app.jar" "$3/backend/app.jar"; cp -- "$2/components/frontend/dist/index.html" "$3/frontend/dist/index.html"; printf nginx >"$3/frontend/nginx.conf"; printf "%s\n" "command: java \${JAVA_OPTS:-} -jar /app/app.jar" >"$3/docker-compose.yml"; printf payload >"$3/payload.bin"'

export PATH="$fake_bin:$PATH"
export AIENIE_CI_SOURCE_COMMIT=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
export AIENIE_CI_TARGET_ARCHITECTURE=linux/amd64
export AIENIE_CI_CACHE_DIR="$work_dir/cache"
export AIENIE_CI_OUTPUT_DIR="$work_dir/resolve-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
export AIENIE_CI_PHASE=resolve
unset AIENIE_CI_NETWORK_MODE || true

bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR"
"$fake_bin/python3" - "$AIENIE_DEPENDENCY_MANIFEST" <<'PY'
import json,sys
value=json.load(open(sys.argv[1],encoding='utf-8'))
assert value['schema_version']=='aienie-repository-dependency-manifest-v1'
assert value['source_commit']=='a'*40
assert value['target_architecture']=='linux/amd64'
assert value['inputs'] and value['modules'] and value['toolchains']
assert value['cache']['file_count'] > 1
PY

resolved_manifest="$AIENIE_DEPENDENCY_MANIFEST"
write_fake node 'if [[ "${1:-}" == "--version" ]]; then echo v22.23.1; else exit 0; fi'
export AIENIE_CI_CACHE_DIR="$work_dir/wrong-node-cache"
export AIENIE_CI_OUTPUT_DIR="$work_dir/wrong-node-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
if bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/wrong-node.log" 2>&1; then
  echo 'Resolve unexpectedly accepted a non-authoritative Node.js version.' >&2
  exit 1
fi
grep -q 'Node.js must be exactly 22.23.2' "$work_dir/wrong-node.log"
write_fake node 'if [[ "${1:-}" == "--version" ]]; then echo v22.23.2; else exit 0; fi'

write_fake pnpm 'if [[ "${1:-}" == "--version" ]]; then echo 11.21.0; else exit 0; fi'
export AIENIE_CI_CACHE_DIR="$work_dir/wrong-pnpm-cache"
export AIENIE_CI_OUTPUT_DIR="$work_dir/wrong-pnpm-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
if bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/wrong-pnpm.log" 2>&1; then
  echo 'Resolve unexpectedly accepted a non-authoritative pnpm version.' >&2
  exit 1
fi
grep -q 'pnpm must be exactly 11.22.0' "$work_dir/wrong-pnpm.log"
write_fake pnpm 'if [[ "${1:-}" == "--version" ]]; then echo 11.22.0; else mkdir -p "$AIENIE_CI_CACHE_DIR/pnpm"; printf "%s" "$AIENIE_CI_PHASE" >"$AIENIE_CI_CACHE_DIR/pnpm/$AIENIE_CI_PHASE.bin"; fi'

export AIENIE_CI_CACHE_DIR="$work_dir/cache"
export AIENIE_CI_OUTPUT_DIR="$work_dir/resolve-output"
export AIENIE_DEPENDENCY_MANIFEST="$resolved_manifest"
cache_before="$("$fake_bin/python3" - "$AIENIE_CI_CACHE_DIR" <<'PY'
import hashlib,pathlib,sys
root=pathlib.Path(sys.argv[1]); h=hashlib.sha256()
for path in sorted((p for p in root.rglob('*') if p.is_file()),key=lambda p:p.relative_to(root).as_posix()):
    h.update(path.relative_to(root).as_posix().encode()); h.update(b'\0'); h.update(hashlib.sha256(path.read_bytes()).digest())
print(h.hexdigest())
PY
)"
cp -- "$AIENIE_CI_CACHE_DIR/.aienie-cache-contract" "$work_dir/cache-contract.backup"
chmod -R a-w -- "$AIENIE_CI_CACHE_DIR"
mkdir -p "$work_dir/build-output"
cp -- "$resolved_manifest" "$work_dir/build-output/repository-dependency-manifest.json"
chmod a-w -- "$work_dir/build-output/repository-dependency-manifest.json"
export AIENIE_CI_PHASE=build
export AIENIE_CI_NETWORK_MODE=offline
export AIENIE_CI_OUTPUT_DIR="$work_dir/build-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
export PROJECT_BUNDLE_HELPER="$fake_bin/bundle-helper"
export PROJECT_FLATTEN_HELPER="$fake_bin/flatten-helper"
export APP_BACKEND_PORT=65530 APP_FRONTEND_PORT=65531
export APP_RUNTIME_ROOT=/ambient-poison APP_CERT_HOST=/ambient-poison.crt
if ! bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/positive-build.log" 2>&1; then
  cat "$work_dir/positive-build.log" >&2
  echo 'Positive offline Build failed.' >&2
  exit 1
fi
[[ -f "$AIENIE_CI_OUTPUT_DIR/release/staging-oci-role-contract.json" ]]
cmp -s "$repo_root/scripts/ci/ainovel-runtime-compose.yml" "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"
python3 "$repo_root/scripts/ci/verify-staging-oci-role-contract.py" \
  "$AIENIE_CI_OUTPUT_DIR/release/staging-oci-role-contract.json" \
  "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml" ai-novel
"$fake_bin/python3" - "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml" "$work_dir/short-protected-bind.yml" <<'PY'
import pathlib,re,sys
raw=pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
pattern=re.compile(r'(?m)^(\s*)- type: bind\n\1  source: \./env\.txt\n\1  target: /app/env\.txt\n\1  read_only: true\n\1  bind:\n\1    create_host_path: false$')
poisoned,count=pattern.subn(lambda match: f'{match.group(1)}- ./env.txt:/app/env.txt:ro',raw,count=1)
assert count == 1
pathlib.Path(sys.argv[2]).write_text(poisoned,encoding='utf-8')
PY
if python3 "$repo_root/scripts/ci/verify-staging-oci-role-contract.py" \
  "$AIENIE_CI_OUTPUT_DIR/release/staging-oci-role-contract.json" \
  "$work_dir/short-protected-bind.yml" ai-novel >/dev/null 2>&1; then
  echo 'AINovel staging contract accepted an auto-created protected file bind.' >&2
  exit 1
fi
[[ -f "$AIENIE_CI_OUTPUT_DIR/payload.bin" && -f "$AIENIE_DEPENDENCY_MANIFEST" \
  && -x "$AIENIE_CI_OUTPUT_DIR/backend/start-backend.sh" \
  && -x "$AIENIE_CI_OUTPUT_DIR/docker/staging-load-env-file.sh" ]] || {
  echo 'Positive offline Build did not produce payload plus protected manifest.' >&2
  exit 1
}
grep -Fq 'entrypoint: ["/app/bin/start-backend.sh"]' "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"
grep -Fq 'command: ["/app/env.txt"]' "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"
grep -Fq 'export SERVER_ADDRESS=0.0.0.0 SERVER_PORT=11041 PORT=11041' "$AIENIE_CI_OUTPUT_DIR/backend/start-backend.sh"
grep -Fq -- '-Dapp.external.grpc.trust-cert-collection=/run/aienie/trust/staging-root.pem' "$AIENIE_CI_OUTPUT_DIR/backend/start-backend.sh"
grep -Fq 'AINOVEL_BACKEND_IMAGE' "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"
grep -Fq 'AINOVEL_FRONTEND_IMAGE' "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"
grep -Fq '/api/actuator/health/readiness' "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"
assert_host_loopback_compose "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"
sed 's/127\.0\.0\.1://g' "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml" >"$work_dir/wildcard-compose.yml"
if assert_host_loopback_compose "$work_dir/wildcard-compose.yml"; then
  echo 'Host-loopback release Compose contract accepted a wildcard negative fixture.' >&2
  exit 1
fi
if grep -Eq 'JAVA_OPTS|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS' \
  "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"; then
  echo 'Tracked runtime assembly retained a generated JVM option channel.' >&2
  exit 1
fi
if grep -Eq '(^|[[:space:]])build:|env_file:|extra_hosts|host-gateway|/home/|\$\{[^}]*(PORT|HOST|ROOT)[^}]*\}' \
  "$AIENIE_CI_OUTPUT_DIR/docker-compose.yml"; then
  echo 'Tracked runtime assembly retained local, ambient, or mutable authority.' >&2
  exit 1
fi
cache_after="$("$fake_bin/python3" - "$AIENIE_CI_CACHE_DIR" <<'PY'
import hashlib,pathlib,sys
root=pathlib.Path(sys.argv[1]); h=hashlib.sha256()
for path in sorted((p for p in root.rglob('*') if p.is_file()),key=lambda p:p.relative_to(root).as_posix()):
    h.update(path.relative_to(root).as_posix().encode()); h.update(b'\0'); h.update(hashlib.sha256(path.read_bytes()).digest())
print(h.hexdigest())
PY
)"
[[ "$cache_before" == "$cache_after" ]] || { echo 'Positive Build mutated the read-only input cache.' >&2; exit 1; }
[[ -z "$(find "$AIENIE_CI_OUTPUT_DIR" -name working-cache -print -quit)" ]] || {
  echo 'Working cache leaked into the release payload.' >&2
  exit 1
}

write_fake cache-tamper-helper 'mkdir -p "$2/components/backend" "$2/components/frontend/dist"; printf jar >"$2/components/backend/app.jar"; printf html >"$2/components/frontend/dist/index.html"; chmod u+w -- "$PROTECTED_CACHE_UNDER_TEST/.aienie-cache-contract"; printf tampered >>"$PROTECTED_CACHE_UNDER_TEST/.aienie-cache-contract"'
mkdir -p "$work_dir/cache-tamper-output"
cp -- "$resolved_manifest" "$work_dir/cache-tamper-output/repository-dependency-manifest.json"
chmod a-w -- "$work_dir/cache-tamper-output/repository-dependency-manifest.json"
export AIENIE_CI_OUTPUT_DIR="$work_dir/cache-tamper-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
export PROJECT_BUNDLE_HELPER="$fake_bin/cache-tamper-helper"
export PROTECTED_CACHE_UNDER_TEST="$AIENIE_CI_CACHE_DIR"
if bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/cache-tamper.log" 2>&1; then
  echo 'Build unexpectedly accepted mutation of the protected input cache.' >&2
  exit 1
fi
grep -Eq 'dependency cache (must be mounted or prepared read-only|changed during build)' "$work_dir/cache-tamper.log"
chmod u+w -- "$AIENIE_CI_CACHE_DIR/.aienie-cache-contract"
cp -- "$work_dir/cache-contract.backup" "$AIENIE_CI_CACHE_DIR/.aienie-cache-contract"
chmod a-w -- "$AIENIE_CI_CACHE_DIR/.aienie-cache-contract"
unset PROTECTED_CACHE_UNDER_TEST

write_fake manifest-tamper-flatten 'rm -rf -- "$3"; mkdir -p "$3/backend" "$3/frontend/dist"; cp -- "$2/components/backend/app.jar" "$3/backend/app.jar"; cp -- "$2/components/frontend/dist/index.html" "$3/frontend/dist/index.html"; printf nginx >"$3/frontend/nginx.conf"; printf "{}\\n" >"$3/repository-dependency-manifest.json"'
mkdir -p "$work_dir/manifest-tamper-output"
cp -- "$resolved_manifest" "$work_dir/manifest-tamper-output/repository-dependency-manifest.json"
chmod a-w -- "$work_dir/manifest-tamper-output/repository-dependency-manifest.json"
export AIENIE_CI_OUTPUT_DIR="$work_dir/manifest-tamper-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
export PROJECT_BUNDLE_HELPER="$fake_bin/bundle-helper"
export PROJECT_FLATTEN_HELPER="$fake_bin/manifest-tamper-flatten"
if bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/manifest-replacement.log" 2>&1; then
  echo 'Build unexpectedly accepted replacement of the protected dependency manifest.' >&2
  exit 1
fi
grep -q 'attempted to replace the protected dependency manifest' "$work_dir/manifest-replacement.log"
export PROJECT_FLATTEN_HELPER="$fake_bin/flatten-helper"

mkdir -p "$work_dir/tampered-output"
cp -- "$resolved_manifest" "$work_dir/tampered-output/repository-dependency-manifest.json"
chmod u+w -- "$work_dir/tampered-output/repository-dependency-manifest.json"
"$fake_bin/python3" - "$work_dir/tampered-output/repository-dependency-manifest.json" <<'PY'
import json,sys
value=json.load(open(sys.argv[1],encoding='utf-8'))
value['inputs'][0]['sha256']='0'*64
open(sys.argv[1],'w',encoding='utf-8').write(json.dumps(value,sort_keys=True,separators=(',',':'))+'\n')
PY
chmod a-w -- "$work_dir/tampered-output/repository-dependency-manifest.json"
export AIENIE_CI_OUTPUT_DIR="$work_dir/tampered-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
if bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/tampered-build.log" 2>&1; then
  echo 'Build unexpectedly accepted a tampered dependency manifest.' >&2
  exit 1
fi
grep -q 'dependency lock input mismatch' "$work_dir/tampered-build.log"

mkdir -p "$work_dir/writable-cache-output"
cp -- "$resolved_manifest" "$work_dir/writable-cache-output/repository-dependency-manifest.json"
chmod a-w -- "$work_dir/writable-cache-output/repository-dependency-manifest.json"
chmod u+w -- "$AIENIE_CI_CACHE_DIR/.aienie-cache-contract"
export AIENIE_CI_OUTPUT_DIR="$work_dir/writable-cache-output"
export AIENIE_DEPENDENCY_MANIFEST="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
if bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/writable-cache.log" 2>&1; then
  echo 'Build unexpectedly accepted a writable dependency cache.' >&2
  exit 1
fi
grep -q 'dependency cache must be mounted or prepared read-only' "$work_dir/writable-cache.log"
chmod a-w -- "$AIENIE_CI_CACHE_DIR/.aienie-cache-contract"

export AIENIE_CI_PHASE=resolve
unset AIENIE_CI_NETWORK_MODE || true
export AIENIE_CI_CACHE_DIR="$work_dir/rejected-cache"
export AIENIE_CI_OUTPUT_DIR="$work_dir/rejected-output"
export AIENIE_DEPENDENCY_MANIFEST="$work_dir/arbitrary-manifest.json"
if bash "$repo_root/scripts/ci/build-release.sh" "$AIENIE_CI_OUTPUT_DIR" >"$work_dir/arbitrary-manifest.log" 2>&1; then
  echo 'Resolve unexpectedly accepted an arbitrary dependency manifest path.' >&2
  exit 1
fi
grep -q 'must be the platform output file' "$work_dir/arbitrary-manifest.log"
[[ ! -e "$AIENIE_DEPENDENCY_MANIFEST" ]] || { echo 'Rejected manifest path was written.' >&2; exit 1; }
bash "$repo_root/scripts/ci/test-production-backup-contract.sh"
printf 'Aienie repository CI contract test passed.\n'
