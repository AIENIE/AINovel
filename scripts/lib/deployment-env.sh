#!/usr/bin/env bash

AINOVEL_COMPOSE_ENV_NAMES=(
  BACKEND_PORT FRONTEND_PORT JAVA_OPTS
  DOCKER_LOG_MAX_SIZE DOCKER_LOG_MAX_FILE
  APP_LOG_DIR_HOST APP_RECORD_DIR_HOST
)

declare -Ag AINOVEL_ENV_FILE_KEYS=()
declare -Ag AINOVEL_ENV_FILE_VALUES=()

ainovel_validate_env_file() {
  local env_file="$1"
  if [[ ! -f "$env_file" || -L "$env_file" ]]; then
    echo "Missing regular env.txt; copy env.example to env.txt before deployment" >&2
    return 1
  fi
  local mode
  mode="$(stat -c '%a' "$env_file")"
  if [[ "$mode" != "600" ]]; then
    echo "env.txt must have mode 0600 (current mode: $mode)" >&2
    return 1
  fi
}

ainovel_read_env_file() {
  local env_file="$1"
  ainovel_validate_env_file "$env_file" || return 1
  AINOVEL_ENV_FILE_KEYS=()
  AINOVEL_ENV_FILE_VALUES=()

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line="$raw_line"
    line="${line%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" == export[[:space:]]* ]]; then
      line="${line#export }"
      line="${line#"${line%%[![:space:]]*}"}"
    fi
    [[ "$line" == *=* ]] || continue

    local name="${line%%=*}"
    local value="${line#*=}"
    name="${name%"${name##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ ! "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid env key in env.txt" >&2
      return 1
    fi
    if [[ ( "$value" == \"*\" && "$value" == *\" ) || ( "$value" == \'*\' && "$value" == *\' ) ]]; then
      value="${value:1:${#value}-2}"
    fi
    AINOVEL_ENV_FILE_KEYS["$name"]=1
    AINOVEL_ENV_FILE_VALUES["$name"]="$value"
  done < "$env_file"
}

ainovel_require_env_file_vars() {
  local missing=()
  local name
  for name in "$@"; do
    if [[ -z "${AINOVEL_ENV_FILE_KEYS[$name]+present}" || -z "${AINOVEL_ENV_FILE_VALUES[$name]:-}" ]]; then
      missing+=("$name")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    echo "Missing required env.txt variables: ${missing[*]}" >&2
    return 1
  fi
}

ainovel_require_deployment_env_sources() {
  ainovel_require_env_file_vars \
    ENV AUTH_MODE ADMIN_USERNAME ADMIN_PASSWORD_HASH \
    JWT_SECRET JWT_ISSUER JWT_AUDIENCE \
    MYSQL_PASSWORD SPRING_JPA_HIBERNATE_DDL_AUTO \
    ADMIN_TRUSTED_ORIGINS ADMIN_SESSION_COOKIE_SECURE \
    EXTERNAL_AI_HMAC_CALLER EXTERNAL_AI_HMAC_SECRET \
    EXTERNAL_USER_INTERNAL_GRPC_TOKEN EXTERNAL_PAY_SERVICE_JWT || return 1

  if [[ "${AINOVEL_ENV_FILE_VALUES[AUTH_MODE]}" == "totp" ]]; then
    ainovel_require_env_file_vars ADMIN_TOTP_ENCRYPTION_KEYS ADMIN_TOTP_ACTIVE_KEY_VERSION || return 1
  fi
}

ainovel_run_without_host_runtime_env() {
  local env_args=()
  local name
  declare -A seen=()
  for name in "${!AINOVEL_ENV_FILE_KEYS[@]}" "${AINOVEL_COMPOSE_ENV_NAMES[@]}"; do
    [[ -n "${seen[$name]+present}" ]] && continue
    seen["$name"]=1
    env_args+=(-u "$name")
  done
  env "${env_args[@]}" "$@"
}
