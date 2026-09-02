#!/usr/bin/env bash
set -euo pipefail

AIENIE_CI_MAVEN_MODULES=(backend)
AIENIE_CI_NPM_MODULES=()
AIENIE_CI_PNPM_MODULES=(frontend)
AIENIE_CI_STATIC_NODE_MODULES=()
AIENIE_CI_MAVEN_EXTRA_ARTIFACTS=()
source "$(cd "$(dirname "$0")" && pwd -P)/aienie-ci-phase.sh"
aienie_ci_prepare_phase "$@"
[[ "$AIENIE_CI_PHASE" == "resolve" ]] && exit 0

output_dir="$AIENIE_CI_OUTPUT_DIR"
repo_root="$AIENIE_CI_REPO_ROOT"
helper="${PROJECT_BUNDLE_HELPER:-/opt/jenkins-scripts/build_legacy_project_bundle.sh}"
flatten="${PROJECT_FLATTEN_HELPER:-/opt/jenkins-scripts/flatten_project_bundle.sh}"
legacy_dir="$(mktemp -d)"
cleanup() {
  [[ -z "${legacy_dir:-}" || ! -d "$legacy_dir" ]] || rm -rf -- "$legacy_dir"
  aienie_ci_cleanup
}
trap cleanup EXIT
WORKSPACE="$repo_root" bash "$helper" "AINovel" "$legacy_dir"
bash "$flatten" "AINovel" "$legacy_dir" "$output_dir"
case "${AIENIE_RELEASE_ENVIRONMENT:-staging}" in
  staging) bash "$repo_root/scripts/ci/assemble-ainovel-runtime.sh" "$repo_root" "$output_dir" ;;
  production) bash "$repo_root/scripts/ci/assemble-ainovel-production-runtime.sh" "$repo_root" "$output_dir" ;;
  *) echo 'AIENIE_RELEASE_ENVIRONMENT must be staging or production' >&2; exit 2 ;;
esac
aienie_ci_finalize_build_inputs
