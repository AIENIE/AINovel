#!/usr/bin/env bash
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$BACKEND_DIR/target"
OUTPUT_FILE="$TARGET_DIR/vscode.env"

# shellcheck source=../scripts/runtime-env.sh
source "$BACKEND_DIR/scripts/runtime-env.sh"

quote_env_value() {
  local value="${1-}"
  if [[ -z "$value" ]]; then
    printf ''
    return
  fi

  if [[ "$value" =~ ^[A-Za-z0-9_./:=,@%+~-]+$ ]]; then
    printf '%s' "$value"
    return
  fi

  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '"%s"' "$value"
}

should_export_key() {
  local key="$1"
  case "$key" in
    PORT|BACKEND_PORT|DB_URL|DB_USERNAME|DB_PASSWORD|JWT_SECRET|JAVA_OPTS)
      return 0
      ;;
    ADMIN_*|AI_*|APP_*|CONSUL_*|EXTERNAL_*|MYSQL_*|PAY_*|QDRANT_*|REDIS_*|SMTP_*|SPRINGDOC_*|SSO_*|USER_*|VITE_*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

write_env_file() {
  local key value
  : > "$OUTPUT_FILE"

  while IFS= read -r key; do
    if ! should_export_key "$key"; then
      continue
    fi
    value="${!key-}"
    printf '%s=%s\n' "$key" "$(quote_env_value "$value")" >> "$OUTPUT_FILE"
  done < <(compgen -e | LC_ALL=C sort)
}

mkdir -p "$TARGET_DIR"
ENV_SOURCE="$(resolve_backend_env_file "$BACKEND_DIR")"
load_env_file_preserve_existing "$ENV_SOURCE"
apply_backend_runtime_defaults
ensure_pay_service_jwt
write_env_file

echo "Wrote $OUTPUT_FILE from $ENV_SOURCE"
