#!/usr/bin/env bash
set -euo pipefail
umask 077

repo_root="$(cd -- "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/lib/deployment-env.sh
source "$repo_root/scripts/lib/deployment-env.sh"

fixture_dir="$(mktemp -d)"
trap 'rm -rf -- "$fixture_dir"' EXIT
missing_file="$fixture_dir/missing.env"
complete_file="$fixture_dir/complete.env"
policy_file="$fixture_dir/policy.env"
template_file="$fixture_dir/template.env"
placeholder_probe_file="$fixture_dir/placeholder-probe.env"

write_fixture() {
  local target="$1"
  {
    printf '%s\n' \
      'ENV=local' \
      'AUTH_MODE=totp' \
      'ADMIN_USERNAME=fixture-admin' \
      'ADMIN_PASSWORD_HASH='\''$2b$12$literal-dollar-placeholder'\''' \
      'ADMIN_TOTP_ENCRYPTION_KEYS=v1:fixture-keyring' \
      'ADMIN_TOTP_ACTIVE_KEY_VERSION=v1' \
      'ADMIN_TRUSTED_ORIGINS=https://localainovel.testhut.top' \
      'ADMIN_SESSION_COOKIE_SECURE=true' \
      'JWT_SECRET=fixture-jwt-secret' \
      'JWT_ISSUER=ainovel' \
      'JWT_AUDIENCE=ainovel-web' \
      'MYSQL_PASSWORD=fixture-database-password' \
      'SPRING_JPA_HIBERNATE_DDL_AUTO=none' \
      'EXTERNAL_AI_HMAC_CALLER=fixture-caller' \
      'EXTERNAL_AI_HMAC_SECRET=fixture-hmac-secret' \
      'EXTERNAL_USER_SERVICE_JWT_CALLER_ID=ainovel' \
      'EXTERNAL_USER_SERVICE_JWT_ISSUER=ainovel' \
      'EXTERNAL_USER_SERVICE_JWT_SECRET=fixture-user-service-jwt-secret-32-bytes' \
      'EXTERNAL_USER_SERVICE_JWT_AUDIENCE=aienie-userservice-grpc' \
      'EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS=300' \
      'EXTERNAL_USER_SERVICE_JWT_SCOPES=user.auth.session.read' \
      'EXTERNAL_PAY_SERVICE_JWT=fixture.pay.jwt'
  } > "$target"
  chmod 600 "$target"
}

write_fixture "$missing_file"
sed '/^ADMIN_USERNAME=/d' "$missing_file" > "$complete_file"
chmod 600 "$complete_file"

(
  export ADMIN_USERNAME=host-admin ENV=production AUTH_MODE=password
  ainovel_read_env_file "$complete_file"
  if ainovel_require_deployment_env_sources 2>/dev/null; then
    echo "host environment incorrectly satisfied env.txt contract" >&2
    exit 1
  fi
)

write_fixture "$complete_file"
chmod 644 "$complete_file"
if ainovel_validate_env_file "$complete_file" 2>/dev/null; then
  echo "env.txt mode check accepted a non-owner-only file" >&2
  exit 1
fi
chmod 600 "$complete_file"

ainovel_read_env_file "$complete_file"
ainovel_require_deployment_env_sources
[[ "${AINOVEL_ENV_FILE_VALUES[ADMIN_PASSWORD_HASH]}" == '$2b$12$literal-dollar-placeholder' ]]

write_fixture "$policy_file"
printf '%s\n' 'EXTERNAL_USER_INTERNAL_GRPC_TOKEN=legacy-static-token' >> "$policy_file"
ainovel_read_env_file "$policy_file"
if ainovel_require_deployment_env_sources 2>/dev/null; then
  echo "legacy UserService static token passed deployment preflight" >&2
  exit 1
fi

for invalid_user_jwt in \
  'EXTERNAL_USER_SERVICE_JWT_AUDIENCE=wrong-audience' \
  'EXTERNAL_USER_SERVICE_JWT_SCOPES=user.auth.session.read,user.directory.read' \
  'EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS=901' \
  'EXTERNAL_USER_SERVICE_JWT_SECRET=short'
do
  write_fixture "$policy_file"
  printf '%s\n' "$invalid_user_jwt" >> "$policy_file"
  ainovel_read_env_file "$policy_file"
  if ainovel_require_deployment_env_sources 2>/dev/null; then
    echo "invalid UserService caller JWT policy passed deployment preflight" >&2
    exit 1
  fi
done

while read -r invalid_env invalid_mode; do
  write_fixture "$policy_file"
  printf 'ENV=%s\nAUTH_MODE=%s\n' "$invalid_env" "$invalid_mode" >> "$policy_file"
  ainovel_read_env_file "$policy_file"
  if ainovel_require_deployment_env_sources 2>/dev/null; then
    echo "invalid ENV/AUTH_MODE policy passed deployment preflight" >&2
    exit 1
  fi
done <<'POLICIES'
LOCAL totp
development totp
local TOTP
local disabled
test password
production password
POLICIES

for raw_policy in \
  "ENV='local'" \
  'ENV= local' \
  'ENV=local ' \
  "AUTH_MODE='totp'" \
  'AUTH_MODE= totp' \
  'AUTH_MODE=totp '
do
  write_fixture "$policy_file"
  printf '%s\n' "$raw_policy" >> "$policy_file"
  ainovel_read_env_file "$policy_file"
  if ainovel_require_deployment_env_sources 2>/dev/null; then
    echo "quoted or whitespace-padded ENV/AUTH_MODE passed deployment preflight" >&2
    exit 1
  fi
done

cp "$repo_root/env.example" "$template_file"
chmod 600 "$template_file"
ainovel_read_env_file "$template_file"
ainovel_load_template_placeholder_manifest
declare -A template_placeholder_values=()
for name in "${!AINOVEL_ENV_FILE_KEYS[@]}"; do
  value="${AINOVEL_ENV_FILE_VALUES[$name]}"
  case "${value,,}" in
    *replace-*|*replace_*|*change-me*|*change_me*) template_placeholder_values["$name"]="$value" ;;
  esac
done
if (( ${#template_placeholder_values[@]} != ${#AINOVEL_TEMPLATE_PLACEHOLDERS[@]} )); then
  echo "env.example and runtime placeholder manifest differ" >&2
  exit 1
fi
for name in "${!AINOVEL_TEMPLATE_PLACEHOLDERS[@]}"; do
  if [[ "${template_placeholder_values[$name]:-}" != "${AINOVEL_TEMPLATE_PLACEHOLDERS[$name]}" ]]; then
    echo "env.example and runtime placeholder manifest differ for key $name" >&2
    exit 1
  fi
  write_fixture "$placeholder_probe_file"
  printf '%s=%s\n' "$name" "${AINOVEL_TEMPLATE_PLACEHOLDERS[$name]}" >> "$placeholder_probe_file"
  ainovel_read_env_file "$placeholder_probe_file"
  if ainovel_require_deployment_env_sources 2>/dev/null; then
    echo "env.example placeholder passed deployment preflight for key $name" >&2
    exit 1
  fi
done

write_fixture "$placeholder_probe_file"
printf '%s\n' \
  'JWT_ISSUER=replace-service' \
  'JWT_SECRET=replace-with-a-different-runtime-value' >> "$placeholder_probe_file"
ainovel_read_env_file "$placeholder_probe_file"
ainovel_require_deployment_env_sources

export ENV=production AUTH_MODE=password ADMIN_PASSWORD_HASH=host-value
ainovel_run_without_host_runtime_env bash -c '
  set -euo pipefail
  [[ ! -v ENV && ! -v AUTH_MODE && ! -v ADMIN_PASSWORD_HASH ]]
'

env -i PATH="$PATH" sh -c '
  set -eu
  loader="$1"
  env_file="$2"
  set -- "$env_file"
  . "$loader"
  test "$ENV" = local
  test "$AUTH_MODE" = totp
  test "$ADMIN_PASSWORD_HASH" = '\''$2b$12$literal-dollar-placeholder'\''
' _ "$repo_root/docker/load-env-file.sh" "$complete_file"

echo "deployment env contract tests: PASS"
