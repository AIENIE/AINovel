#!/usr/bin/env bash
set -euo pipefail

trim_value() {
  local value="${1-}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

load_env_file_preserve_existing() {
  local env_file="$1"
  if [[ ! -f "$env_file" ]]; then
    echo "Environment file not found: $env_file" >&2
    return 1
  fi

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line key value
    line="$(trim_value "$raw_line")"
    if [[ -z "$line" || "${line:0:1}" == "#" || "$line" != *=* ]]; then
      continue
    fi

    key="$(trim_value "${line%%=*}")"
    value="$(trim_value "${line#*=}")"
    if [[ "$value" == \"*\" && "$value" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi

    if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && [[ -z "${!key+x}" ]]; then
      export "$key=$value"
    fi
  done < "$env_file"
}

resolve_backend_env_file() {
  local backend_dir="$1"
  local explicit="${ENV_FILE:-}"
  if [[ -n "$explicit" ]]; then
    if [[ -f "$explicit" ]]; then
      printf '%s\n' "$explicit"
      return 0
    fi
    echo "ENV_FILE points to a missing file: $explicit" >&2
    return 1
  fi

  local backend_env="$backend_dir/env.txt"
  if [[ -f "$backend_env" ]]; then
    printf '%s\n' "$backend_env"
    return 0
  fi

  local parent_env
  parent_env="$(cd "$backend_dir/.." && pwd)/env.txt"
  if [[ -f "$parent_env" ]]; then
    printf '%s\n' "$parent_env"
    return 0
  fi

  echo "No environment file found. Checked ENV_FILE, $backend_env, and $parent_env" >&2
  return 1
}

set_default() {
  local key="$1"
  local value="$2"
  if [[ -z "${!key:-}" ]]; then
    export "$key=$value"
  fi
}

is_placeholder_value() {
  local value
  value="$(trim_value "${1:-}")"
  if [[ -z "$value" ]]; then
    return 0
  fi

  local upper="${value^^}"
  [[ "$upper" == REPLACE_ME* || "$upper" == *REPLACE_WITH_YOUR_OWN* || "$upper" == *REPLACE-WITH-YOUR-OWN* || "$upper" == *CHANGE_ME* || "$upper" == *CHANGE-ME* ]]
}

generate_pay_service_jwt() {
  if ! command -v node >/dev/null 2>&1; then
    echo "Missing required command: node (needed to generate EXTERNAL_PAY_SERVICE_JWT)." >&2
    return 1
  fi

  PAY_JWT_SECRET="${EXTERNAL_PAY_JWT_SECRET:-}" \
  PAY_JWT_ISSUER="${EXTERNAL_PAY_JWT_ISSUER:-}" \
  PAY_JWT_AUDIENCE="${EXTERNAL_PAY_JWT_AUDIENCE:-}" \
  PAY_JWT_ROLE="${EXTERNAL_PAY_JWT_ROLE:-}" \
  PAY_JWT_SERVICE="${EXTERNAL_PAY_JWT_SERVICE:-}" \
  PAY_JWT_SCOPES="${EXTERNAL_PAY_JWT_SCOPES:-}" \
  PAY_JWT_TTL_SECONDS="${EXTERNAL_PAY_JWT_TTL_SECONDS:-}" \
    node <<'NODE'
const crypto = require('node:crypto');

function b64url(input) {
  const raw = Buffer.isBuffer(input) ? input : Buffer.from(input, 'utf8');
  return raw.toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

const now = Math.floor(Date.now() / 1000);
const ttl = Math.max(300, Number.parseInt(process.env.PAY_JWT_TTL_SECONDS || '3600', 10) || 3600);
const scopes = String(process.env.PAY_JWT_SCOPES || '')
  .split(/[,\s]+/)
  .map(value => value.trim())
  .filter(Boolean);

const header = { alg: 'HS256', typ: 'JWT' };
const payload = {
  sub: process.env.PAY_JWT_SERVICE || 'ainovel',
  service: process.env.PAY_JWT_SERVICE || 'ainovel',
  role: process.env.PAY_JWT_ROLE || 'SERVICE',
  scopes,
  iat: now,
  exp: now + ttl,
  iss: process.env.PAY_JWT_ISSUER || 'aienie-services',
  aud: process.env.PAY_JWT_AUDIENCE || 'aienie-payservice-grpc'
};

const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
const secret = String(process.env.PAY_JWT_SECRET || '');
const signature = crypto.createHmac('sha256', secret).update(signingInput).digest();
process.stdout.write(`${signingInput}.${b64url(signature)}`);
NODE
}

is_pay_service_jwt_claims_valid() {
  if ! command -v node >/dev/null 2>&1; then
    return 1
  fi

  PAY_JWT_RAW="${EXTERNAL_PAY_SERVICE_JWT:-}" \
  PAY_JWT_ISSUER="${EXTERNAL_PAY_JWT_ISSUER:-}" \
  PAY_JWT_AUDIENCE="${EXTERNAL_PAY_JWT_AUDIENCE:-}" \
  PAY_JWT_ROLE="${EXTERNAL_PAY_JWT_ROLE:-}" \
  PAY_JWT_SCOPES="${EXTERNAL_PAY_JWT_SCOPES:-}" \
    node <<'NODE'
function parseToken(raw) {
  let token = String(raw || '').trim();
  if (token.toLowerCase().startsWith('bearer ')) {
    token = token.slice(7).trim();
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  } catch {
    return null;
  }
}

function parseScopes(value) {
  if (Array.isArray(value)) {
    return value.map(item => String(item || '').trim()).filter(Boolean);
  }
  return String(value || '')
    .split(/[,\s]+/)
    .map(item => item.trim())
    .filter(Boolean);
}

const payload = parseToken(process.env.PAY_JWT_RAW);
if (!payload) {
  process.exit(1);
}

const expectedRole = String(process.env.PAY_JWT_ROLE || 'SERVICE').trim();
const expectedIssuer = String(process.env.PAY_JWT_ISSUER || 'aienie-services').trim();
const expectedAudience = String(process.env.PAY_JWT_AUDIENCE || 'aienie-payservice-grpc').trim();
const requiredScopes = String(process.env.PAY_JWT_SCOPES || '')
  .split(/[,\s]+/)
  .map(value => value.trim())
  .filter(Boolean);
const actualScopes = parseScopes(payload.scopes);
const audiences = Array.isArray(payload.aud)
  ? payload.aud.map(value => String(value || '').trim()).filter(Boolean)
  : String(payload.aud || '').split(/[,\s]+/).map(value => value.trim()).filter(Boolean);

if (!String(payload.role || '').trim() || String(payload.role).toUpperCase() !== expectedRole.toUpperCase()) {
  process.exit(1);
}
if (expectedIssuer && String(payload.iss || '') !== expectedIssuer) {
  process.exit(1);
}
if (expectedAudience && !audiences.includes(expectedAudience)) {
  process.exit(1);
}
for (const scope of requiredScopes) {
  if (!actualScopes.some(value => value.toLowerCase() === scope.toLowerCase())) {
    process.exit(1);
  }
}

const exp = Number(payload.exp || 0);
const now = Math.floor(Date.now() / 1000);
if (!Number.isFinite(exp) || exp <= now + 30) {
  process.exit(1);
}
process.exit(0);
NODE
}

ensure_pay_service_jwt() {
  if is_placeholder_value "${EXTERNAL_PAY_SERVICE_JWT:-}"; then
    if is_placeholder_value "${EXTERNAL_PAY_JWT_SECRET:-}"; then
      echo "EXTERNAL_PAY_SERVICE_JWT is unset and EXTERNAL_PAY_JWT_SECRET is invalid." >&2
      echo "Set EXTERNAL_PAY_SERVICE_JWT directly, or provide EXTERNAL_PAY_JWT_SECRET and the claim variables for auto generation." >&2
      return 1
    fi
    export EXTERNAL_PAY_SERVICE_JWT
    EXTERNAL_PAY_SERVICE_JWT="$(generate_pay_service_jwt)"
    return 0
  fi

  if ! is_pay_service_jwt_claims_valid; then
    if is_placeholder_value "${EXTERNAL_PAY_JWT_SECRET:-}"; then
      echo "EXTERNAL_PAY_SERVICE_JWT has invalid or expired claims and EXTERNAL_PAY_JWT_SECRET is unavailable for regeneration." >&2
      return 1
    fi
    export EXTERNAL_PAY_SERVICE_JWT
    EXTERNAL_PAY_SERVICE_JWT="$(generate_pay_service_jwt)"
  fi
}

apply_backend_runtime_defaults() {
  set_default BACKEND_PORT "11041"
  set_default MYSQL_HOST "192.168.5.208"
  set_default MYSQL_PORT "3306"
  set_default MYSQL_DB "ainovel"
  set_default MYSQL_USER "ainovel"
  set_default MYSQL_PASSWORD "ainovelpwd"
  set_default REDIS_HOST "192.168.5.208"
  set_default REDIS_PORT "6379"
  set_default REDIS_PASSWORD ""
  set_default REDIS_KEY_PREFIX "aienie:ainovel:"
  set_default QDRANT_HOST "http://192.168.5.208"
  set_default QDRANT_PORT "6333"
  set_default QDRANT_ENABLED "true"
  set_default CONSUL_ENABLED "true"
  set_default CONSUL_SCHEME "http"
  set_default CONSUL_HOST "192.168.5.208"
  set_default CONSUL_PORT "60000"
  set_default CONSUL_DATACENTER ""
  set_default CONSUL_CACHE_SECONDS "30"
  set_default USER_HTTP_SERVICE_NAME "aienie-userservice-http"
  set_default USER_HTTP_ADDR "http://192.168.5.208:10000"
  set_default USER_GRPC_SERVICE_NAME "aienie-userservice-grpc"
  set_default USER_GRPC_ADDR "static://192.168.5.208:10001"
  set_default PAY_GRPC_SERVICE_NAME "aienie-payservice-grpc"
  set_default PAY_GRPC_ADDR "static://192.168.5.208:20021"
  set_default AI_GRPC_SERVICE_NAME "aienie-aiservice-grpc"
  set_default AI_GRPC_ADDR "static://192.168.5.208:10011"
  set_default USER_SESSION_GRPC_TIMEOUT_MS "5000"
  set_default USER_GRPC_SERVICE_TAG ""
  set_default SSO_SESSION_VALIDATION_ENABLED "true"
  set_default SSO_CALLBACK_ORIGIN "https://ainovel.seekerhut.com"
  set_default VITE_SSO_ENTRY_BASE_URL "https://ainovel.seekerhut.com"
  set_default EXTERNAL_PROJECT_KEY "ainovel"
  set_default EXTERNAL_TIMEOUT_MS "120000"
  set_default EXTERNAL_SECURITY_FAIL_FAST "true"
  set_default EXTERNAL_GRPC_TLS_ENABLED "false"
  set_default EXTERNAL_GRPC_PLAINTEXT_ENABLED "true"
  set_default EXTERNAL_AI_HMAC_CALLER "integration-test"
  set_default EXTERNAL_AI_HMAC_SECRET "0f18f9c1548e4f5d8520c55f5c8d0b3d9e95bd5f81a40fbb8e2ffeb8b7f0d530"
  set_default EXTERNAL_USER_INTERNAL_GRPC_TOKEN "local-userservice-internal-token"
  set_default EXTERNAL_PAY_SERVICE_JWT "REPLACE_ME_PAY_SERVICE_JWT"
  set_default JWT_SECRET "replace-with-your-own-long-random-secret"
  set_default EXTERNAL_PAY_JWT_SECRET "$JWT_SECRET"
  set_default EXTERNAL_PAY_JWT_ISSUER "aienie-services"
  set_default EXTERNAL_PAY_JWT_AUDIENCE "aienie-payservice-grpc"
  set_default EXTERNAL_PAY_JWT_ROLE "SERVICE"
  set_default EXTERNAL_PAY_JWT_SERVICE "$EXTERNAL_PROJECT_KEY"
  set_default EXTERNAL_PAY_JWT_SCOPES "billing.read,billing.write"
  set_default EXTERNAL_PAY_JWT_TTL_SECONDS "3600"
  set_default APP_TIME_ZONE "Asia/Shanghai"
  set_default JAVA_OPTS "-Duser.timezone=${APP_TIME_ZONE}"
  set_default ADMIN_USERNAME "admin"
  set_default SPRINGDOC_API_DOCS_ENABLED "false"
  set_default SPRINGDOC_SWAGGER_UI_ENABLED "false"
  set_default APP_SECURITY_CORS_ALLOWED_ORIGINS "https://ainovel.seekerhut.com,https://ainovel.aienie.com,http://127.0.0.1:11040,http://localhost:11040"
  set_default APP_SECURITY_CORS_ALLOWED_METHODS "GET,POST,PUT,DELETE,OPTIONS,PATCH"
  set_default APP_SECURITY_CORS_ALLOWED_HEADERS "Authorization,Content-Type,Idempotency-Key,X-Requested-With"
  set_default DB_URL "jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
  set_default DB_USERNAME "${MYSQL_USER}"
  set_default DB_PASSWORD "${MYSQL_PASSWORD}"
  if [[ -z "${PORT:-}" && -n "${BACKEND_PORT:-}" ]]; then
    export PORT="$BACKEND_PORT"
  fi
}
