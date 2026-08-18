#!/usr/bin/env bash
set -euo pipefail

# Repository-owned two-phase dependency/build contract used by the Aienie
# release platform.  The caller must declare the module arrays before calling
# aienie_ci_prepare_phase.

aienie_ci_fail() {
  printf 'Aienie repository CI contract failed: %s\n' "$*" >&2
  exit 2
}

aienie_ci_need() {
  command -v "$1" >/dev/null 2>&1 || aienie_ci_fail "missing command: $1"
}

aienie_ci_require_value() {
  local name="$1"
  [[ -n "${!name:-}" ]] || aienie_ci_fail "$name is required"
  [[ "${!name}" != *$'\n'* && "${!name}" != *$'\r'* ]] || aienie_ci_fail "$name contains a newline"
}

aienie_ci_assert_native_architecture() {
  local machine
  [[ "$(uname -s)" == "Linux" ]] || aienie_ci_fail 'CI resolve/build requires a native Linux agent'
  machine="$(uname -m)"
  case "${AIENIE_CI_TARGET_ARCHITECTURE}:${machine}" in
    linux/amd64:x86_64|linux/amd64:amd64|linux/arm64:aarch64|linux/arm64:arm64) ;;
    linux/amd64:*|linux/arm64:*)
      aienie_ci_fail "native agent architecture $machine does not match $AIENIE_CI_TARGET_ARCHITECTURE"
      ;;
    *) aienie_ci_fail "unsupported AIENIE_CI_TARGET_ARCHITECTURE: $AIENIE_CI_TARGET_ARCHITECTURE" ;;
  esac
}

aienie_ci_assert_safe_directory() {
  local path="$1" label="$2" canonical
  canonical="$(realpath -m -- "$path")"
  [[ "$canonical" != "/" && "$canonical" != "$AIENIE_CI_REPO_ROOT" ]] || aienie_ci_fail "unsafe $label path: $canonical"
  case "$canonical/" in
    "$AIENIE_CI_REPO_ROOT/"*) aienie_ci_fail "$label must be outside the source checkout: $canonical" ;;
  esac
  [[ ! -L "$path" ]] || aienie_ci_fail "$label must not be a symbolic link"
  printf '%s\n' "$canonical"
}

aienie_ci_assert_empty_output() {
  local output="$1"
  if [[ -e "$output" ]]; then
    [[ -d "$output" && ! -L "$output" ]] || aienie_ci_fail 'AIENIE_CI_OUTPUT_DIR must be a regular directory'
    [[ -z "$(find "$output" -mindepth 1 -maxdepth 1 -print -quit)" ]] || aienie_ci_fail 'AIENIE_CI_OUTPUT_DIR must be empty'
  else
    mkdir -p -- "$output"
  fi
}

aienie_ci_assert_manifest_path() {
  local expected
  expected="$AIENIE_CI_OUTPUT_DIR/repository-dependency-manifest.json"
  [[ "$AIENIE_DEPENDENCY_MANIFEST" == "$expected" ]] ||
    aienie_ci_fail "AIENIE_DEPENDENCY_MANIFEST must be the platform output file: $expected"
}

aienie_ci_assert_build_output() {
  [[ -d "$AIENIE_CI_OUTPUT_DIR" && ! -L "$AIENIE_CI_OUTPUT_DIR" ]] ||
    aienie_ci_fail 'build output must be a regular directory preloaded by the platform'
  [[ -f "$AIENIE_DEPENDENCY_MANIFEST" && ! -L "$AIENIE_DEPENDENCY_MANIFEST" ]] ||
    aienie_ci_fail 'build dependency manifest is missing or unsafe'
  [[ -z "$(find "$AIENIE_CI_OUTPUT_DIR" -mindepth 1 -maxdepth 1 ! -name repository-dependency-manifest.json -print -quit)" ]] ||
    aienie_ci_fail 'build output must initially contain only repository-dependency-manifest.json'
}

aienie_ci_assert_no_special_entries() {
  local root="$1" label="$2"
  [[ -z "$(find "$root" -type l -print -quit)" ]] || aienie_ci_fail "$label contains a symbolic link"
  [[ -z "$(find "$root" ! -type d ! -type f -print -quit)" ]] || aienie_ci_fail "$label contains a special filesystem entry"
}

aienie_ci_assert_readonly_tree() {
  local root="$1" label="$2"
  [[ -z "$(find "$root" -perm /222 -print -quit)" ]] ||
    aienie_ci_fail "$label must be mounted or prepared read-only (no write mode bits)"
}

aienie_ci_sha256_file() {
  sha256sum -- "$1" | awk '{print $1}'
}

aienie_ci_write_specs() {
  local output="$1" kind module
  : >"$output"
  for module in "${AIENIE_CI_MAVEN_MODULES[@]}"; do printf 'maven\t%s\n' "$module" >>"$output"; done
  for module in "${AIENIE_CI_NPM_MODULES[@]}"; do printf 'npm\t%s\n' "$module" >>"$output"; done
  for module in "${AIENIE_CI_PNPM_MODULES[@]}"; do printf 'pnpm\t%s\n' "$module" >>"$output"; done
  for module in "${AIENIE_CI_STATIC_NODE_MODULES[@]}"; do printf 'static-node\t%s\n' "$module" >>"$output"; done
  [[ -s "$output" ]] || aienie_ci_fail 'no CI modules were declared'
  while IFS=$'\t' read -r kind module; do
    [[ "$module" != /* && "$module" != *'..'* ]] || aienie_ci_fail "unsafe $kind module path: $module"
    [[ -d "$AIENIE_CI_REPO_ROOT/$module" ]] || aienie_ci_fail "missing $kind module directory: $module"
    case "$kind" in
      maven) [[ -f "$AIENIE_CI_REPO_ROOT/$module/pom.xml" ]] || aienie_ci_fail "missing $module/pom.xml" ;;
      npm) [[ -f "$AIENIE_CI_REPO_ROOT/$module/package.json" && -f "$AIENIE_CI_REPO_ROOT/$module/package-lock.json" ]] || aienie_ci_fail "missing npm lock inputs under $module" ;;
      pnpm) [[ -f "$AIENIE_CI_REPO_ROOT/$module/package.json" && -f "$AIENIE_CI_REPO_ROOT/$module/pnpm-lock.yaml" ]] || aienie_ci_fail "missing pnpm lock inputs under $module" ;;
      static-node) [[ -f "$AIENIE_CI_REPO_ROOT/$module/package.json" ]] || aienie_ci_fail "missing $module/package.json" ;;
    esac
  done <"$output"
}

aienie_ci_toolchains() {
  local output="$1"
  : >"$output"
  if (( ${#AIENIE_CI_MAVEN_MODULES[@]} > 0 )); then
    printf 'maven\t%s\n' "$(MAVEN_OPTS='-Djansi.force=false' mvn --version | head -n 1 | tr -d '\r')" >>"$output"
    printf 'java\t%s\n' "$(java -version 2>&1 | head -n 1 | tr -d '\r')" >>"$output"
  fi
  if (( ${#AIENIE_CI_NPM_MODULES[@]} > 0 || ${#AIENIE_CI_STATIC_NODE_MODULES[@]} > 0 )); then
    printf 'node\t%s\n' "$(node --version)" >>"$output"
    printf 'npm\t%s\n' "$(npm --version)" >>"$output"
  fi
  if (( ${#AIENIE_CI_PNPM_MODULES[@]} > 0 )); then
    printf 'node\t%s\n' "$(node --version)" >>"$output"
    printf 'pnpm\t%s\n' "$(pnpm --version)" >>"$output"
  fi
  sort -u -o "$output" "$output"
}

aienie_ci_cache_inventory() {
  python3 - "$1" <<'PY'
import hashlib, pathlib, sys
root = pathlib.Path(sys.argv[1])
entries = []
for path in sorted((p for p in root.rglob('*') if p.is_file()), key=lambda p: p.relative_to(root).as_posix()):
    relative = path.relative_to(root).as_posix()
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    entries.append((relative, digest))
h = hashlib.sha256()
for relative, digest in entries:
    h.update(relative.encode('utf-8')); h.update(b'\0'); h.update(digest.encode('ascii')); h.update(b'\n')
print(len(entries), h.hexdigest())
PY
}

aienie_ci_restore_manifest() {
  local staged="${AIENIE_CI_STAGED_MANIFEST:-}" destination="${AIENIE_CI_MANIFEST_PATH:-}"
  [[ -n "$staged" && -f "$staged" && -n "$destination" ]] || return 0
  mkdir -p -- "$(dirname "$destination")"
  if [[ -e "$destination" || -L "$destination" ]]; then
    [[ -f "$destination" || -L "$destination" ]] || return 1
    rm -f -- "$destination"
  fi
  mv -- "$staged" "$destination"
}

aienie_ci_cleanup() {
  local scratch="${AIENIE_CI_SCRATCH_DIR:-}"
  aienie_ci_restore_manifest || true
  if [[ -n "$scratch" && -d "$scratch" && "$scratch" != "/" && "$scratch" != "$AIENIE_CI_REPO_ROOT" ]]; then
    rm -rf -- "$scratch"
  fi
  AIENIE_CI_SCRATCH_DIR=''
}

aienie_ci_detach_manifest() {
  AIENIE_CI_MANIFEST_PATH="$AIENIE_DEPENDENCY_MANIFEST"
  AIENIE_CI_STAGED_MANIFEST="$AIENIE_CI_SCRATCH_DIR/repository-dependency-manifest.json"
  [[ ! -e "$AIENIE_CI_STAGED_MANIFEST" ]] || aienie_ci_fail 'internal staged manifest path already exists'
  mv -- "$AIENIE_CI_MANIFEST_PATH" "$AIENIE_CI_STAGED_MANIFEST"
}

aienie_ci_prepare_working_cache() {
  local working="$AIENIE_CI_SCRATCH_DIR/working-cache"
  mkdir -p -- "$working"
  cp -a -- "$AIENIE_CI_INPUT_CACHE_DIR/." "$working/"
  chmod -R u+rwX -- "$working"
  AIENIE_CI_CACHE_DIR="$working"
  export AIENIE_CI_CACHE_DIR
}

aienie_ci_finalize_build_inputs() {
  [[ "${AIENIE_CI_PHASE:-}" == "build" ]] || return 0
  local replacement=false cache_count cache_sha manifest_sha
  if [[ -e "$AIENIE_CI_MANIFEST_PATH" || -L "$AIENIE_CI_MANIFEST_PATH" ]]; then
    replacement=true
  fi
  aienie_ci_restore_manifest || aienie_ci_fail 'could not restore the protected dependency manifest'
  AIENIE_CI_STAGED_MANIFEST=''
  [[ "$replacement" == false ]] || aienie_ci_fail 'release assembly attempted to replace the protected dependency manifest'
  [[ -f "$AIENIE_CI_MANIFEST_PATH" && ! -L "$AIENIE_CI_MANIFEST_PATH" ]] ||
    aienie_ci_fail 'protected dependency manifest is missing after build'
  aienie_ci_assert_readonly_tree "$AIENIE_CI_MANIFEST_PATH" 'dependency manifest'
  manifest_sha="$(aienie_ci_sha256_file "$AIENIE_CI_MANIFEST_PATH")"
  [[ "$manifest_sha" == "$AIENIE_CI_INPUT_MANIFEST_SHA" ]] ||
    aienie_ci_fail 'dependency manifest changed during build'
  aienie_ci_validate_manifest "$AIENIE_CI_SPECS_PATH" "$AIENIE_CI_TOOLCHAINS_PATH" \
    "$AIENIE_CI_INPUT_CACHE_COUNT" "$AIENIE_CI_INPUT_CACHE_SHA"
  aienie_ci_assert_no_special_entries "$AIENIE_CI_INPUT_CACHE_DIR" 'dependency cache'
  aienie_ci_assert_readonly_tree "$AIENIE_CI_INPUT_CACHE_DIR" 'dependency cache'
  read -r cache_count cache_sha < <(aienie_ci_cache_inventory "$AIENIE_CI_INPUT_CACHE_DIR")
  [[ "$cache_count" == "$AIENIE_CI_INPUT_CACHE_COUNT" && "$cache_sha" == "$AIENIE_CI_INPUT_CACHE_SHA" ]] ||
    aienie_ci_fail 'dependency cache changed during build'
}

aienie_ci_resolve_maven() {
  local module
  mkdir -p -- "$AIENIE_CI_CACHE_DIR/maven"
  for module in "${AIENIE_CI_MAVEN_MODULES[@]}"; do
    (
      cd "$AIENIE_CI_REPO_ROOT/$module"
      mvn -B -ntp -Dmaven.repo.local="$AIENIE_CI_CACHE_DIR/maven" \
        dependency:go-offline dependency:resolve dependency:resolve-plugins
    )
  done
  local artifact
  for artifact in "${AIENIE_CI_MAVEN_EXTRA_ARTIFACTS[@]}"; do
    mvn -B -ntp -Dmaven.repo.local="$AIENIE_CI_CACHE_DIR/maven" \
      dependency:get -Dtransitive=true -Dartifact="$artifact"
  done
}

aienie_ci_resolve_npm() {
  local module temp
  mkdir -p -- "$AIENIE_CI_CACHE_DIR/npm"
  for module in "${AIENIE_CI_NPM_MODULES[@]}"; do
    temp="$(mktemp -d)"
    (
      trap 'rm -rf -- "$temp"' EXIT
      cp "$AIENIE_CI_REPO_ROOT/$module/package.json" "$AIENIE_CI_REPO_ROOT/$module/package-lock.json" "$temp/"
      npm --prefix "$temp" ci --ignore-scripts --legacy-peer-deps \
        --cache "$AIENIE_CI_CACHE_DIR/npm" --no-audit --no-fund
    )
    if [[ "${AIENIE_CI_NPM_AUDIT:-false}" == "true" ]]; then
      npm --prefix "$AIENIE_CI_REPO_ROOT/$module" audit --audit-level=high \
        --cache "$AIENIE_CI_CACHE_DIR/npm" --no-fund
      npm --prefix "$AIENIE_CI_REPO_ROOT/$module" audit --omit=dev --audit-level=high \
        --cache "$AIENIE_CI_CACHE_DIR/npm" --no-fund
    fi
  done
}

aienie_ci_resolve_pnpm() {
  local module
  mkdir -p -- "$AIENIE_CI_CACHE_DIR/pnpm"
  for module in "${AIENIE_CI_PNPM_MODULES[@]}"; do
    pnpm --dir "$AIENIE_CI_REPO_ROOT/$module" fetch --frozen-lockfile \
      --store-dir "$AIENIE_CI_CACHE_DIR/pnpm"
  done
}

aienie_ci_write_manifest() {
  local specs="$1" toolchains="$2" cache_count="$3" cache_sha="$4"
  mkdir -p -- "$(dirname "$AIENIE_DEPENDENCY_MANIFEST")"
  python3 - "$AIENIE_CI_REPO_ROOT" "$specs" "$toolchains" "$AIENIE_DEPENDENCY_MANIFEST" \
    "$AIENIE_CI_SOURCE_COMMIT" "$AIENIE_CI_TARGET_ARCHITECTURE" "$cache_count" "$cache_sha" \
    "${AIENIE_CI_NPM_AUDIT:-false}" <<'PY'
import hashlib, json, pathlib, re, sys
root, specs_path, tools_path, out_path = map(pathlib.Path, sys.argv[1:5])
source_commit, target_arch, cache_count, cache_sha, npm_audit = sys.argv[5:10]
modules=[]; input_paths=set()
for line in specs_path.read_text(encoding='utf-8').splitlines():
    kind, relative = line.split('\t', 1)
    modules.append({'kind':kind, 'path':relative})
    input_paths.add(f'{relative}/package.json' if kind != 'maven' else f'{relative}/pom.xml')
    if kind == 'npm': input_paths.add(f'{relative}/package-lock.json')
    if kind == 'pnpm': input_paths.add(f'{relative}/pnpm-lock.yaml')
inputs=[]
for relative in sorted(input_paths):
    path=root/relative
    inputs.append({'path':relative,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
toolchains={}
for line in tools_path.read_text(encoding='utf-8').splitlines():
    key,value=line.split('\t',1); toolchains[key]=re.sub(r'[\x00-\x1f\x7f]+',' ',value).strip()
value={
  'schema_version':'aienie-repository-dependency-manifest-v1',
  'source_commit':source_commit.lower(),
  'target_architecture':target_arch,
  'inputs':inputs,
  'modules':modules,
  'toolchains':toolchains,
  'cache':{'file_count':int(cache_count),'inventory_sha256':cache_sha},
  'resolve_checks':{'npm_audit':'passed' if npm_audit == 'true' else 'not-applicable'}
}
out_path.write_text(json.dumps(value,sort_keys=True,separators=(',',':'))+'\n',encoding='utf-8')
PY
}

aienie_ci_validate_manifest() {
  local specs="$1" toolchains="$2" cache_count="$3" cache_sha="$4"
  [[ -f "$AIENIE_DEPENDENCY_MANIFEST" && ! -L "$AIENIE_DEPENDENCY_MANIFEST" ]] || aienie_ci_fail 'dependency manifest is missing or unsafe'
  python3 - "$AIENIE_CI_REPO_ROOT" "$specs" "$toolchains" "$AIENIE_DEPENDENCY_MANIFEST" \
    "$AIENIE_CI_SOURCE_COMMIT" "$AIENIE_CI_TARGET_ARCHITECTURE" "$cache_count" "$cache_sha" <<'PY'
import hashlib,json,pathlib,re,sys
root,specs_path,tools_path,manifest_path=map(pathlib.Path,sys.argv[1:5])
source_commit,target_arch,cache_count,cache_sha=sys.argv[5:9]
value=json.loads(manifest_path.read_text(encoding='utf-8'))
if value.get('schema_version')!='aienie-repository-dependency-manifest-v1': raise SystemExit('dependency manifest schema rejected')
if value.get('source_commit')!=source_commit.lower(): raise SystemExit('dependency manifest source commit mismatch')
if value.get('target_architecture')!=target_arch: raise SystemExit('dependency manifest target architecture mismatch')
expected_modules=[]; input_paths=set()
for line in specs_path.read_text(encoding='utf-8').splitlines():
    kind,relative=line.split('\t',1); expected_modules.append({'kind':kind,'path':relative})
    input_paths.add(f'{relative}/package.json' if kind!='maven' else f'{relative}/pom.xml')
    if kind=='npm': input_paths.add(f'{relative}/package-lock.json')
    if kind=='pnpm': input_paths.add(f'{relative}/pnpm-lock.yaml')
expected_inputs=[{'path':p,'sha256':hashlib.sha256((root/p).read_bytes()).hexdigest()} for p in sorted(input_paths)]
expected_tools={}
for line in tools_path.read_text(encoding='utf-8').splitlines():
    key,tool_value=line.split('\t',1); expected_tools[key]=re.sub(r'[\x00-\x1f\x7f]+',' ',tool_value).strip()
if value.get('modules')!=expected_modules: raise SystemExit('dependency manifest module set mismatch')
if value.get('inputs')!=expected_inputs: raise SystemExit('dependency lock input mismatch')
if value.get('toolchains')!=expected_tools: raise SystemExit('dependency toolchain mismatch')
if value.get('cache')!={'file_count':int(cache_count),'inventory_sha256':cache_sha}: raise SystemExit('dependency cache inventory mismatch')
PY
}

aienie_ci_has_script() {
  node -e 'const p=require(process.argv[1]); process.exit(p.scripts && p.scripts[process.argv[2]] ? 0 : 1)' "$1/package.json" "$2"
}

aienie_ci_build_maven() {
  local module
  for module in "${AIENIE_CI_MAVEN_MODULES[@]}"; do
    (cd "$AIENIE_CI_REPO_ROOT/$module" && mvn -B -ntp -o \
      -Dmaven.repo.local="$AIENIE_CI_CACHE_DIR/maven" clean package)
  done
}

aienie_ci_build_npm() {
  local module module_dir
  for module in "${AIENIE_CI_NPM_MODULES[@]}"; do
    module_dir="$AIENIE_CI_REPO_ROOT/$module"
    npm --prefix "$module_dir" ci --offline --legacy-peer-deps \
      --cache "$AIENIE_CI_CACHE_DIR/npm" --no-audit --no-fund
    if aienie_ci_has_script "$module_dir" lint; then npm --prefix "$module_dir" run lint; fi
    if aienie_ci_has_script "$module_dir" typecheck; then npm --prefix "$module_dir" run typecheck; fi
    if aienie_ci_has_script "$module_dir" test:unit; then
      npm --prefix "$module_dir" run test:unit
    elif aienie_ci_has_script "$module_dir" test; then
      npm --prefix "$module_dir" test
    fi
    if aienie_ci_has_script "$module_dir" build; then npm --prefix "$module_dir" run build; fi
  done
}

aienie_ci_build_pnpm() {
  local module module_dir
  for module in "${AIENIE_CI_PNPM_MODULES[@]}"; do
    module_dir="$AIENIE_CI_REPO_ROOT/$module"
    pnpm --dir "$module_dir" install --offline --frozen-lockfile --store-dir "$AIENIE_CI_CACHE_DIR/pnpm"
    if aienie_ci_has_script "$module_dir" lint; then pnpm --dir "$module_dir" run lint; fi
    if aienie_ci_has_script "$module_dir" typecheck; then pnpm --dir "$module_dir" run typecheck; fi
    if aienie_ci_has_script "$module_dir" test:unit; then
      pnpm --dir "$module_dir" run test:unit
    elif aienie_ci_has_script "$module_dir" test; then
      pnpm --dir "$module_dir" test
    fi
    if aienie_ci_has_script "$module_dir" build; then pnpm --dir "$module_dir" run build; fi
  done
}

aienie_ci_build_static_node() {
  local module module_dir
  for module in "${AIENIE_CI_STATIC_NODE_MODULES[@]}"; do
    module_dir="$AIENIE_CI_REPO_ROOT/$module"
    if aienie_ci_has_script "$module_dir" test:unit; then npm --prefix "$module_dir" run test:unit; fi
  done
}

aienie_ci_prepare_phase() {
  AIENIE_CI_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[1]}")/.." && pwd -P)"
  export AIENIE_CI_REPO_ROOT
  aienie_ci_require_value AIENIE_CI_PHASE
  aienie_ci_require_value AIENIE_CI_CACHE_DIR
  aienie_ci_require_value AIENIE_CI_OUTPUT_DIR
  aienie_ci_require_value AIENIE_DEPENDENCY_MANIFEST
  aienie_ci_require_value AIENIE_CI_SOURCE_COMMIT
  aienie_ci_require_value AIENIE_CI_TARGET_ARCHITECTURE
  [[ "$AIENIE_CI_SOURCE_COMMIT" =~ ^[0-9a-fA-F]{40}$|^[0-9a-fA-F]{64}$ ]] || aienie_ci_fail 'AIENIE_CI_SOURCE_COMMIT must be an immutable hexadecimal commit'
  (( $# == 1 )) || aienie_ci_fail 'the phase output must be the only positional argument'
  local positional_output cache_dir output_dir specs toolchains cache_count cache_sha
  positional_output="$(realpath -m -- "$1")"
  output_dir="$(aienie_ci_assert_safe_directory "$AIENIE_CI_OUTPUT_DIR" 'output')"
  cache_dir="$(aienie_ci_assert_safe_directory "$AIENIE_CI_CACHE_DIR" 'cache')"
  [[ "$positional_output" == "$output_dir" ]] || aienie_ci_fail 'positional output does not match AIENIE_CI_OUTPUT_DIR'
  [[ "$cache_dir" != "$output_dir" ]] || aienie_ci_fail 'cache and phase output must be separate directories'
  case "$cache_dir/" in "$output_dir/"*) aienie_ci_fail 'cache must not be nested under phase output' ;; esac
  case "$output_dir/" in "$cache_dir/"*) aienie_ci_fail 'phase output must not be nested under cache' ;; esac
  AIENIE_CI_CACHE_DIR="$cache_dir"; AIENIE_CI_OUTPUT_DIR="$output_dir"
  AIENIE_DEPENDENCY_MANIFEST="$(realpath -m -- "$AIENIE_DEPENDENCY_MANIFEST")"
  export AIENIE_CI_CACHE_DIR AIENIE_CI_OUTPUT_DIR AIENIE_DEPENDENCY_MANIFEST
  aienie_ci_assert_manifest_path
  aienie_ci_need python3; aienie_ci_need realpath; aienie_ci_need sha256sum; aienie_ci_need find; aienie_ci_need uname
  aienie_ci_need awk; aienie_ci_need cp; aienie_ci_need chmod
  (( ${#AIENIE_CI_MAVEN_MODULES[@]} == 0 )) || { aienie_ci_need mvn; aienie_ci_need java; }
  (( ${#AIENIE_CI_NPM_MODULES[@]} == 0 && ${#AIENIE_CI_STATIC_NODE_MODULES[@]} == 0 )) || { aienie_ci_need node; aienie_ci_need npm; }
  (( ${#AIENIE_CI_PNPM_MODULES[@]} == 0 )) || { aienie_ci_need node; aienie_ci_need pnpm; }
  aienie_ci_assert_native_architecture
  AIENIE_CI_SCRATCH_DIR="$(mktemp -d)"
  specs="$AIENIE_CI_SCRATCH_DIR/modules.tsv"; toolchains="$AIENIE_CI_SCRATCH_DIR/toolchains.tsv"
  AIENIE_CI_SPECS_PATH="$specs"; AIENIE_CI_TOOLCHAINS_PATH="$toolchains"
  trap aienie_ci_cleanup EXIT
  aienie_ci_write_specs "$specs"; aienie_ci_toolchains "$toolchains"
  case "$AIENIE_CI_PHASE" in
    resolve)
      [[ "${AIENIE_CI_NETWORK_MODE:-online}" != "offline" ]] || aienie_ci_fail 'resolve phase requires approved repository network access'
      if [[ -e "$AIENIE_CI_CACHE_DIR" ]]; then
        [[ -d "$AIENIE_CI_CACHE_DIR" && ! -L "$AIENIE_CI_CACHE_DIR" && -z "$(find "$AIENIE_CI_CACHE_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]] || aienie_ci_fail 'resolve cache must be an empty regular directory'
      else
        mkdir -p -- "$AIENIE_CI_CACHE_DIR"
      fi
      aienie_ci_assert_empty_output "$AIENIE_CI_OUTPUT_DIR"
      [[ ! -e "$AIENIE_DEPENDENCY_MANIFEST" ]] || aienie_ci_fail 'resolve dependency manifest output already exists'
      aienie_ci_resolve_maven; aienie_ci_resolve_npm; aienie_ci_resolve_pnpm
      printf 'aienie-repository-cache-v1\n%s\n%s\n' "$AIENIE_CI_SOURCE_COMMIT" "$AIENIE_CI_TARGET_ARCHITECTURE" >"$AIENIE_CI_CACHE_DIR/.aienie-cache-contract"
      aienie_ci_assert_no_special_entries "$AIENIE_CI_CACHE_DIR" 'resolved dependency cache'
      read -r cache_count cache_sha < <(aienie_ci_cache_inventory "$AIENIE_CI_CACHE_DIR")
      (( cache_count > 1 )) || aienie_ci_fail 'resolve produced an empty dependency cache'
      aienie_ci_write_manifest "$specs" "$toolchains" "$cache_count" "$cache_sha"
      printf 'Resolved repository dependencies: manifest=%s cacheFiles=%s\n' "$AIENIE_DEPENDENCY_MANIFEST" "$cache_count"
      return 0
      ;;
    build)
      [[ "${AIENIE_CI_NETWORK_MODE:-}" == "offline" ]] || aienie_ci_fail 'build requires AIENIE_CI_NETWORK_MODE=offline'
      [[ -d "$AIENIE_CI_CACHE_DIR" && ! -L "$AIENIE_CI_CACHE_DIR" ]] || aienie_ci_fail 'build dependency cache is missing or unsafe'
      aienie_ci_assert_no_special_entries "$AIENIE_CI_CACHE_DIR" 'dependency cache'
      aienie_ci_assert_readonly_tree "$AIENIE_CI_CACHE_DIR" 'dependency cache'
      aienie_ci_assert_build_output
      aienie_ci_assert_readonly_tree "$AIENIE_DEPENDENCY_MANIFEST" 'dependency manifest'
      read -r cache_count cache_sha < <(aienie_ci_cache_inventory "$AIENIE_CI_CACHE_DIR")
      (( cache_count > 1 )) || aienie_ci_fail 'build dependency cache is empty'
      aienie_ci_validate_manifest "$specs" "$toolchains" "$cache_count" "$cache_sha"
      AIENIE_CI_INPUT_CACHE_DIR="$AIENIE_CI_CACHE_DIR"
      AIENIE_CI_INPUT_CACHE_COUNT="$cache_count"
      AIENIE_CI_INPUT_CACHE_SHA="$cache_sha"
      AIENIE_CI_INPUT_MANIFEST_SHA="$(aienie_ci_sha256_file "$AIENIE_DEPENDENCY_MANIFEST")"
      aienie_ci_prepare_working_cache
      aienie_ci_build_maven; aienie_ci_build_npm; aienie_ci_build_pnpm; aienie_ci_build_static_node
      export npm_config_offline=true npm_config_audit=false npm_config_fund=false
      export npm_config_cache="$AIENIE_CI_CACHE_DIR/npm" npm_config_store_dir="$AIENIE_CI_CACHE_DIR/pnpm"
      export MAVEN_ARGS="-o -Dmaven.repo.local=$AIENIE_CI_CACHE_DIR/maven ${MAVEN_ARGS:-}"
      export MAVEN_OPTS="-Dmaven.repo.local=$AIENIE_CI_CACHE_DIR/maven ${MAVEN_OPTS:-}"
      aienie_ci_detach_manifest
      ;;
    *) aienie_ci_fail 'AIENIE_CI_PHASE must be resolve or build' ;;
  esac
}
