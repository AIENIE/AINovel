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
      export "$key=$value"
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
  for domain in "$APP_TEST_DOMAIN" "$APP_PROD_DOMAIN" userservice.seekerhut.com payservice.seekerhut.com aiservice.seekerhut.com; do
    if ! grep -qE "^[^#]*[[:space:]]${domain}([[:space:]]|$)" /etc/hosts; then
      run_sudo sh -c "echo \"127.0.0.1 ${domain}\" >> /etc/hosts"
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
    server_name ${APP_TEST_DOMAIN} ${APP_PROD_DOMAIN};
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl;
    server_name ${APP_TEST_DOMAIN} ${APP_PROD_DOMAIN};

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
    server_name ${APP_TEST_DOMAIN} ${APP_PROD_DOMAIN};

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

  export MYSQL_HOST=127.0.0.1
  export MYSQL_PORT=3308
  export REDIS_HOST=127.0.0.1
  export REDIS_PORT=6381
  export QDRANT_HOST=http://127.0.0.1
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
set_default SSO_SESSION_VALIDATION_ENABLED "false"
set_default SSO_CALLBACK_ORIGIN "https://${APP_TEST_DOMAIN}"
set_default VITE_SSO_ENTRY_BASE_URL "https://${APP_TEST_DOMAIN}"
set_default EXTERNAL_PROJECT_KEY "ainovel"
set_default DEPS_AUTO_BOOTSTRAP "true"
set_default JWT_SECRET "replace-with-your-own-long-random-secret"

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
echo "Frontend URL: https://${APP_TEST_DOMAIN}"
echo "Backend API: https://${APP_TEST_DOMAIN}/api"
echo "Consul: ${CONSUL_SCHEME}://${CONSUL_HOST}:${CONSUL_PORT}"
