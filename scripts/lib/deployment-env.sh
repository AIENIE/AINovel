#!/usr/bin/env bash

AINOVEL_COMPOSE_ENV_NAMES=(
  BACKEND_PORT FRONTEND_PORT JAVA_OPTS
  DOCKER_LOG_MAX_SIZE DOCKER_LOG_MAX_FILE
  APP_LOG_DIR_HOST APP_RECORD_DIR_HOST
)
AINOVEL_DEPLOYMENT_ENV_LIB_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AINOVEL_TEMPLATE_PLACEHOLDER_MANIFEST="$AINOVEL_DEPLOYMENT_ENV_LIB_DIR/../../backend/src/main/resources/env-template-placeholders.properties"

declare -Ag AINOVEL_ENV_FILE_KEYS=()
declare -Ag AINOVEL_ENV_FILE_VALUES=()
declare -Ag AINOVEL_ENV_FILE_RAW_VALUES=()
declare -Ag AINOVEL_TEMPLATE_PLACEHOLDERS=()

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
  AINOVEL_ENV_FILE_RAW_VALUES=()

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
    local raw_value="$value"
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
    AINOVEL_ENV_FILE_RAW_VALUES["$name"]="$raw_value"
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

ainovel_validate_admin_auth_policy() {
  local env_name="${AINOVEL_ENV_FILE_VALUES[ENV]}"
  local auth_mode="${AINOVEL_ENV_FILE_VALUES[AUTH_MODE]}"
  if [[ "${AINOVEL_ENV_FILE_RAW_VALUES[ENV]}" != "$env_name" \
    || "${AINOVEL_ENV_FILE_RAW_VALUES[AUTH_MODE]}" != "$auth_mode" ]]; then
    echo "ENV and AUTH_MODE must use exact unquoted values without surrounding whitespace" >&2
    return 1
  fi
  case "$env_name:$auth_mode" in
    local:password|local:totp|test:totp|production:totp) return 0 ;;
    *)
      echo "Invalid ENV/AUTH_MODE policy; allowed combinations: local/password, local/totp, test/totp, production/totp" >&2
      return 1
      ;;
  esac
}

ainovel_load_template_placeholder_manifest() {
  local manifest="$AINOVEL_TEMPLATE_PLACEHOLDER_MANIFEST"
  if [[ ! -f "$manifest" || -L "$manifest" ]]; then
    echo "Missing regular runtime template placeholder manifest" >&2
    return 1
  fi
  AINOVEL_TEMPLATE_PLACEHOLDERS=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" != *=* ]]; then
      echo "Invalid runtime template placeholder manifest entry" >&2
      return 1
    fi
    local name="${line%%=*}"
    local value="${line#*=}"
    if [[ ! "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ || -z "$value" \
      || -n "${AINOVEL_TEMPLATE_PLACEHOLDERS[$name]+present}" ]]; then
      echo "Invalid runtime template placeholder manifest entry" >&2
      return 1
    fi
    AINOVEL_TEMPLATE_PLACEHOLDERS["$name"]="$value"
  done < "$manifest"
  if (( ${#AINOVEL_TEMPLATE_PLACEHOLDERS[@]} == 0 )); then
    echo "Runtime template placeholder manifest is empty" >&2
    return 1
  fi
}

ainovel_reject_env_file_placeholders() {
  ainovel_load_template_placeholder_manifest || return 1
  local placeholders=()
  local name
  for name in "${!AINOVEL_TEMPLATE_PLACEHOLDERS[@]}"; do
    if [[ "${AINOVEL_ENV_FILE_VALUES[$name]:-}" == "${AINOVEL_TEMPLATE_PLACEHOLDERS[$name]}" ]]; then
      placeholders+=("$name")
    fi
  done
  if (( ${#placeholders[@]} > 0 )); then
    echo "env.txt contains template placeholder values for: ${placeholders[*]}" >&2
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
    EXTERNAL_USER_SERVICE_JWT_CALLER_ID EXTERNAL_USER_SERVICE_JWT_ISSUER \
    EXTERNAL_USER_SERVICE_JWT_SECRET EXTERNAL_USER_SERVICE_JWT_AUDIENCE \
    EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS EXTERNAL_USER_SERVICE_JWT_SCOPES \
    EXTERNAL_PAY_SERVICE_JWT || return 1

  if [[ -n "${AINOVEL_ENV_FILE_VALUES[EXTERNAL_USER_INTERNAL_GRPC_TOKEN]:-}" ]]; then
    echo "Legacy UserService static gRPC token is forbidden" >&2
    return 1
  fi
  local user_jwt_ttl="${AINOVEL_ENV_FILE_VALUES[EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS]}"
  if [[ ! "${AINOVEL_ENV_FILE_VALUES[EXTERNAL_USER_SERVICE_JWT_CALLER_ID]}" =~ ^[a-z0-9][a-z0-9._-]{1,63}$ \
    || ! "${AINOVEL_ENV_FILE_VALUES[EXTERNAL_USER_SERVICE_JWT_ISSUER]}" =~ ^[a-z0-9][a-z0-9._-]{1,63}$ \
    || "${AINOVEL_ENV_FILE_VALUES[EXTERNAL_USER_SERVICE_JWT_AUDIENCE]}" != "aienie-userservice-grpc" \
    || "${AINOVEL_ENV_FILE_VALUES[EXTERNAL_USER_SERVICE_JWT_SCOPES]}" != "user.auth.session.read" \
    || ! "$user_jwt_ttl" =~ ^[0-9]+$ \
    || ${#AINOVEL_ENV_FILE_VALUES[EXTERNAL_USER_SERVICE_JWT_SECRET]} -lt 32 ]]; then
    echo "Invalid UserService caller JWT configuration" >&2
    return 1
  fi
  if (( 10#$user_jwt_ttl < 30 || 10#$user_jwt_ttl > 900 )); then
    echo "Invalid UserService caller JWT configuration" >&2
    return 1
  fi

  ainovel_validate_admin_auth_policy || return 1

  if [[ "${AINOVEL_ENV_FILE_VALUES[AUTH_MODE]}" == "totp" ]]; then
    ainovel_require_env_file_vars ADMIN_TOTP_ENCRYPTION_KEYS ADMIN_TOTP_ACTIVE_KEY_VERSION || return 1
  fi

  ainovel_reject_env_file_placeholders || return 1
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
