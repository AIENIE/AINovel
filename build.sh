#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
INIT_MODE=false

if [[ "${1:-}" == "--init" ]]; then
  INIT_MODE=true
  shift
fi

if [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--init]"
  exit 1
fi

trim_value() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

load_env_file() {
  local env_file="$ROOT_DIR/env.txt"
  if [[ ! -f "$env_file" ]]; then
    echo "env.txt not found, using current shell environment and built-in defaults."
    return
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
    if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      if [[ -z "${!key+x}" ]]; then
        export "$key=$value"
      fi
    fi
  done < "$env_file"
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
    echo "Missing required command: node (needed to generate EXTERNAL_PAY_SERVICE_JWT)."
    exit 1
  fi
  PAY_JWT_SECRET="$EXTERNAL_PAY_JWT_SECRET" \
  PAY_JWT_ISSUER="$EXTERNAL_PAY_JWT_ISSUER" \
  PAY_JWT_AUDIENCE="$EXTERNAL_PAY_JWT_AUDIENCE" \
  PAY_JWT_ROLE="$EXTERNAL_PAY_JWT_ROLE" \
  PAY_JWT_SERVICE="$EXTERNAL_PAY_JWT_SERVICE" \
  PAY_JWT_SCOPES="$EXTERNAL_PAY_JWT_SCOPES" \
  PAY_JWT_TTL_SECONDS="$EXTERNAL_PAY_JWT_TTL_SECONDS" \
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
  .map(v => v.trim())
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
  PAY_JWT_RAW="$EXTERNAL_PAY_SERVICE_JWT" \
  PAY_JWT_ISSUER="$EXTERNAL_PAY_JWT_ISSUER" \
  PAY_JWT_AUDIENCE="$EXTERNAL_PAY_JWT_AUDIENCE" \
  PAY_JWT_ROLE="$EXTERNAL_PAY_JWT_ROLE" \
  PAY_JWT_SCOPES="$EXTERNAL_PAY_JWT_SCOPES" \
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
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    return payload;
  } catch {
    return null;
  }
}

function parseScopes(value) {
  if (Array.isArray(value)) {
    return value.map(v => String(v || '').trim()).filter(Boolean);
  }
  return String(value || '')
    .split(/[,\s]+/)
    .map(v => v.trim())
    .filter(Boolean);
}

const payload = parseToken(process.env.PAY_JWT_RAW);
if (!payload) process.exit(1);

const expectedRole = String(process.env.PAY_JWT_ROLE || 'SERVICE').trim();
const expectedIssuer = String(process.env.PAY_JWT_ISSUER || 'aienie-services').trim();
const expectedAudience = String(process.env.PAY_JWT_AUDIENCE || 'aienie-payservice-grpc').trim();
const requiredScopes = String(process.env.PAY_JWT_SCOPES || '')
  .split(/[,\s]+/)
  .map(v => v.trim())
  .filter(Boolean);
const payloadScopes = parseScopes(payload.scopes);
const audClaim = payload.aud;
const audiences = Array.isArray(audClaim)
  ? audClaim.map(v => String(v || '').trim()).filter(Boolean)
  : String(audClaim || '').split(/[,\s]+/).map(v => v.trim()).filter(Boolean);

if (!String(payload.role || '').trim() || String(payload.role).toUpperCase() !== expectedRole.toUpperCase()) process.exit(1);
if (expectedIssuer && String(payload.iss || '') !== expectedIssuer) process.exit(1);
if (expectedAudience && !audiences.includes(expectedAudience)) process.exit(1);
for (const scope of requiredScopes) {
  if (!payloadScopes.some(s => s.toLowerCase() === scope.toLowerCase())) process.exit(1);
}
const exp = Number(payload.exp || 0);
const now = Math.floor(Date.now() / 1000);
if (!Number.isFinite(exp) || exp <= now + 30) process.exit(1);
process.exit(0);
NODE
}

ensure_pay_service_jwt() {
  if is_placeholder_value "${EXTERNAL_PAY_SERVICE_JWT:-}"; then
    if is_placeholder_value "${EXTERNAL_PAY_JWT_SECRET:-}"; then
      echo "EXTERNAL_PAY_SERVICE_JWT is unset and EXTERNAL_PAY_JWT_SECRET is invalid."
      echo "Please set EXTERNAL_PAY_SERVICE_JWT directly, or set EXTERNAL_PAY_JWT_SECRET and claim vars for auto generation."
      exit 1
    fi
    export EXTERNAL_PAY_SERVICE_JWT
    EXTERNAL_PAY_SERVICE_JWT="$(generate_pay_service_jwt)"
    echo "Generated EXTERNAL_PAY_SERVICE_JWT from EXTERNAL_PAY_JWT_SECRET."
    return
  fi
  if ! is_pay_service_jwt_claims_valid; then
    if is_placeholder_value "${EXTERNAL_PAY_JWT_SECRET:-}"; then
      echo "EXTERNAL_PAY_SERVICE_JWT has invalid/expired claims and EXTERNAL_PAY_JWT_SECRET is unavailable for regeneration."
      exit 1
    fi
    export EXTERNAL_PAY_SERVICE_JWT
    EXTERNAL_PAY_SERVICE_JWT="$(generate_pay_service_jwt)"
    echo "Regenerated EXTERNAL_PAY_SERVICE_JWT because existing token claims are invalid or expired."
  fi
}

is_windows() {
  local sys
  sys=$(uname -s 2>/dev/null || echo "")
  case "$sys" in
    MINGW*|MSYS*|CYGWIN*) return 0 ;;
    *) return 1 ;;
  esac
}

SUDO_PASS="${SUDO_PASSWORD:-${SUDO_PASS:-}}"

run_sudo() {
  if [[ "$EUID" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    if [[ -n "$SUDO_PASS" ]]; then
      echo "$SUDO_PASS" | sudo -E -S -p "" "$@"
    else
      sudo -E "$@"
    fi
  else
    "$@"
  fi
}

run_user() {
  if is_windows; then
    "$@"
    return
  fi
  if [[ "$EUID" -eq 0 && -n "${SUDO_USER:-}" ]]; then
    local user_home
    user_home="$(getent passwd "$SUDO_USER" | cut -d: -f6)"
    if command -v runuser >/dev/null 2>&1; then
      runuser -u "$SUDO_USER" -- env HOME="$user_home" PATH="$PATH" "$@"
    else
      su -s /bin/bash "$SUDO_USER" -c "HOME='$user_home' PATH='$PATH' $(printf '%q ' "$@")"
    fi
  else
    "$@"
  fi
}

port_open() {
  local host="$1"
  local port="$2"
  timeout 2 bash -lc "</dev/tcp/${host}/${port}" >/dev/null 2>&1
}

ensure_nginx_installed() {
  if ! command -v nginx >/dev/null 2>&1; then
    run_sudo env DEBIAN_FRONTEND=noninteractive apt-get update
    run_sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y nginx
  fi
}

reload_nginx() {
  run_sudo nginx -t
  if command -v systemctl >/dev/null 2>&1; then
    if run_sudo systemctl is-active --quiet nginx; then
      run_sudo systemctl reload nginx
    else
      run_sudo systemctl start nginx
    fi
  elif command -v service >/dev/null 2>&1; then
    run_sudo service nginx reload || run_sudo service nginx start
  else
    run_sudo nginx -s reload || run_sudo nginx
  fi
}

ensure_hosts_entry() {
  local domain
  if ! grep -qE "^[^#]*[[:space:]]${DEPLOY_DOMAIN}([[:space:]]|$)" /etc/hosts; then
    run_sudo sh -c "echo \"127.0.0.1 ${DEPLOY_DOMAIN}\" >> /etc/hosts"
  fi

  local upstream_localhost="${PIN_UPSTREAM_SERVICES_TO_LOCALHOST,,}"
  for domain in userservice.seekerhut.com payservice.seekerhut.com aiservice.seekerhut.com; do
    if [[ "$upstream_localhost" == "true" ]]; then
      if ! grep -qE "^[^#]*[[:space:]]${domain}([[:space:]]|$)" /etc/hosts; then
        run_sudo sh -c "echo \"127.0.0.1 ${domain}\" >> /etc/hosts"
      fi
    else
      run_sudo sed -i -E "/^127\\.0\\.0\\.1[[:space:]].*[[:space:]]${domain}([[:space:]]|$)/d" /etc/hosts
    fi
  done
}

install_nginx_conf() {
  local conf_file="/etc/nginx/sites-available/ainovel.local.conf"
  local enabled_link="/etc/nginx/sites-enabled/ainovel.local.conf"
  local cert="/etc/nginx/ssl/seekerhut.com.crt"
  local key="/etc/nginx/ssl/seekerhut.com.key"

  local tmp
  tmp="$(mktemp)"

  if [[ -f "$cert" && -f "$key" ]]; then
    cat >"$tmp" <<NGINX
server {
    listen 80;
    server_name ${DEPLOY_DOMAIN};
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl;
    server_name ${DEPLOY_DOMAIN};

    ssl_certificate ${cert};
    ssl_certificate_key ${key};

    location /api/ {
        proxy_pass http://127.0.0.1:${BACKEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:${FRONTEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
NGINX
  else
    cat >"$tmp" <<NGINX
server {
    listen 80;
    server_name ${DEPLOY_DOMAIN};

    location /api/ {
        proxy_pass http://127.0.0.1:${BACKEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:${FRONTEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
NGINX
  fi

  run_sudo install -m 0644 "$tmp" "$conf_file"
  rm -f "$tmp"
  if [[ ! -L "$enabled_link" ]]; then
    run_sudo ln -sf "$conf_file" "$enabled_link"
  fi
  reload_nginx
}

ensure_local_deps() {
  local compose_file="$ROOT_DIR/backend/deploy/deps-compose.yml"
  if [[ ! -f "$compose_file" ]]; then
    echo "Missing dependency compose file: $compose_file"
    exit 1
  fi
  run_sudo docker compose -f "$compose_file" up -d

  export MYSQL_HOST=host.docker.internal
  export MYSQL_PORT=3308
  export REDIS_HOST=host.docker.internal
  export REDIS_PORT=6381
  export QDRANT_HOST=http://host.docker.internal
  export QDRANT_PORT=6335
}

prepare_dependencies() {
  local missing=false
  if ! port_open "$MYSQL_HOST" "$MYSQL_PORT"; then
    missing=true
    echo "Dependency unavailable: MySQL ${MYSQL_HOST}:${MYSQL_PORT}"
  fi
  if ! port_open "$REDIS_HOST" "$REDIS_PORT"; then
    missing=true
    echo "Dependency unavailable: Redis ${REDIS_HOST}:${REDIS_PORT}"
  fi
  if ! port_open "$QDRANT_TCP_HOST" "$QDRANT_PORT"; then
    missing=true
    echo "Dependency unavailable: Qdrant ${QDRANT_TCP_HOST}:${QDRANT_PORT}"
  fi

  if [[ "$missing" == true && "${DEPS_AUTO_BOOTSTRAP,,}" == "true" ]]; then
    echo "Remote dependencies unavailable, bootstrapping local Docker dependencies..."
    ensure_local_deps
  elif [[ "$missing" == true ]]; then
    echo "Dependencies unavailable and DEPS_AUTO_BOOTSTRAP is disabled."
    exit 1
  fi
}

build_frontend() {
  local front_dir="$ROOT_DIR/frontend"
  run_user bash -c "cd '$front_dir' && npm ci --legacy-peer-deps || npm install --legacy-peer-deps"
  if run_user bash -c "cd '$front_dir' && npm run" | grep -q "^  test"; then
    run_user bash -c "cd '$front_dir' && npm run test"
  fi
  run_user bash -c "cd '$front_dir' && npm run build"
}

build_backend() {
  local backend_dir="$ROOT_DIR/backend"
  run_user bash -c "cd '$backend_dir' && mvn -q test"
  run_user bash -c "cd '$backend_dir' && mvn -q -Dmaven.test.skip=true clean package"

  local jar_path=""
  shopt -s nullglob
  for jar in "$backend_dir/target/"*.jar; do
    if [[ "$jar" != *.original ]]; then
      jar_path="$jar"
      break
    fi
  done
  shopt -u nullglob

  if [[ -z "$jar_path" ]]; then
    echo "Backend jar not found in $backend_dir/target"
    exit 1
  fi
  run_user rm -rf "$backend_dir/target/app.jar"
  run_user cp "$jar_path" "$backend_dir/target/app.jar"
}

rollout_app() {
  local compose_file="$ROOT_DIR/docker-compose.yml"
  run_sudo docker compose -f "$compose_file" down --remove-orphans
  run_sudo docker compose -f "$compose_file" up -d
}

load_env_file

set_default APP_TEST_DOMAIN "ainovel.seekerhut.com"
set_default APP_PROD_DOMAIN "ainovel.aienie.com"
set_default DEPLOY_DOMAIN "$APP_TEST_DOMAIN"
set_default PIN_UPSTREAM_SERVICES_TO_LOCALHOST "false"
set_default FRONTEND_PORT "11040"
set_default BACKEND_PORT "11041"

set_default MYSQL_HOST "192.168.1.4"
set_default MYSQL_PORT "3306"
set_default MYSQL_DB "ainovel"
set_default MYSQL_USER "ainovel"
set_default MYSQL_PASSWORD "ainovelpwd"

set_default REDIS_HOST "192.168.1.4"
set_default REDIS_PORT "6379"
set_default REDIS_PASSWORD ""
set_default REDIS_KEY_PREFIX "aienie:ainovel:"

set_default QDRANT_HOST "http://192.168.1.4"
set_default QDRANT_PORT "6333"
set_default QDRANT_ENABLED "true"

set_default CONSUL_ENABLED "true"
set_default CONSUL_SCHEME "http"
set_default CONSUL_HOST "192.168.1.4"
set_default CONSUL_PORT "60000"
set_default CONSUL_DATACENTER ""
set_default CONSUL_CACHE_SECONDS "30"

set_default USER_HTTP_SERVICE_NAME "aienie-userservice-http"
set_default USER_HTTP_ADDR "https://userservice.seekerhut.com"
set_default USER_GRPC_SERVICE_NAME "aienie-userservice-grpc"
set_default USER_GRPC_ADDR "static://userservice.seekerhut.com:10001"
set_default PAY_GRPC_SERVICE_NAME "aienie-payservice-grpc"
set_default PAY_GRPC_ADDR "static://payservice.seekerhut.com:20021"
set_default AI_GRPC_SERVICE_NAME "aienie-aiservice-grpc"
set_default AI_GRPC_ADDR "static://aiservice.seekerhut.com:10011"

set_default USER_SESSION_GRPC_TIMEOUT_MS "5000"
set_default USER_GRPC_SERVICE_TAG ""
set_default SSO_SESSION_VALIDATION_ENABLED "true"
set_default SSO_CALLBACK_ORIGIN "https://${DEPLOY_DOMAIN}"
set_default VITE_SSO_ENTRY_BASE_URL "https://${DEPLOY_DOMAIN}"
set_default EXTERNAL_PROJECT_KEY "ainovel"
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
set_default JAVA_OPTS "-Duser.timezone=Asia/Shanghai"
set_default SPRINGDOC_API_DOCS_ENABLED "false"
set_default SPRINGDOC_SWAGGER_UI_ENABLED "false"
set_default APP_SECURITY_CORS_ALLOWED_ORIGINS "https://ainovel.seekerhut.com,https://ainovel.aienie.com,http://127.0.0.1:11040,http://localhost:11040"
set_default APP_SECURITY_CORS_ALLOWED_METHODS "GET,POST,PUT,DELETE,OPTIONS,PATCH"
set_default APP_SECURITY_CORS_ALLOWED_HEADERS "Authorization,Content-Type,Idempotency-Key,X-Requested-With"
set_default DEPS_AUTO_BOOTSTRAP "true"

ensure_pay_service_jwt

QDRANT_TCP_HOST="${QDRANT_HOST#http://}"
QDRANT_TCP_HOST="${QDRANT_TCP_HOST#https://}"
QDRANT_TCP_HOST="${QDRANT_TCP_HOST%%/*}"

prepare_dependencies
build_frontend
build_backend
rollout_app

if [[ "$INIT_MODE" == "true" ]] || ! is_windows; then
  ensure_nginx_installed
  ensure_hosts_entry
  install_nginx_conf
fi

echo "Deployment finished."
echo "Frontend URL: https://${DEPLOY_DOMAIN}"
echo "Backend API: https://${DEPLOY_DOMAIN}/api"
echo "Consul: ${CONSUL_SCHEME}://${CONSUL_HOST}:${CONSUL_PORT}"
