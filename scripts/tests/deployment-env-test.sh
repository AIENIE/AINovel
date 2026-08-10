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
      'EXTERNAL_USER_INTERNAL_GRPC_TOKEN=fixture-user-token' \
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
