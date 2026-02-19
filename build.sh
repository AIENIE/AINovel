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

SUDO_PASS="${SUDO_PASSWORD:-${SUDO_PASS:-}}"

is_windows() {
  local sys
  sys=$(uname -s 2>/dev/null || echo "")
  case "$sys" in
    MINGW*|MSYS*|CYGWIN*) return 0 ;;
    *) return 1 ;;
  esac
}

run_sudo() {
  if [[ "$EUID" -eq 0 ]]; then
    "$@"
  elif ! command -v sudo >/dev/null 2>&1; then
    "$@"
  else
    echo "$SUDO_PASS" | sudo -S -p "" "$@"
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
      return
    fi
  fi

  if run_sudo pgrep -x nginx >/dev/null 2>&1; then
    run_sudo pkill -HUP -x nginx || true
    return
  fi

  if command -v systemctl >/dev/null 2>&1; then
    run_sudo systemctl start nginx
    return
  fi

  if command -v service >/dev/null 2>&1; then
    run_sudo service nginx start
    return
  fi

  run_sudo nginx
}

ensure_hosts_entry() {
  for domain in ainovel.seekerhut.com ainovel.aienie.com; do
    if ! grep -qE "^[^#]*\s+${domain}" /etc/hosts; then
      run_sudo sh -c "echo \"127.0.0.1 ${domain}\" >> /etc/hosts"
    fi
  done
}

install_dev_nginx_conf() {
  local source_conf="$ROOT_DIR/deploy/nginx/ainovel.conf"
  local target_conf="/etc/nginx/conf.d/ainovel.conf"
  local generated_conf=""

  if [[ ! -f "$source_conf" ]]; then
    generated_conf="$(mktemp)"
    cat >"$generated_conf" <<'EOF'
server {
    listen 80;
    server_name ainovel.seekerhut.com ainovel.aienie.com;

    location /api/ {
        proxy_pass http://127.0.0.1:11041;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:11040;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF
    source_conf="$generated_conf"
  fi

  run_sudo install -m 0644 "$source_conf" "$target_conf"
  if [[ -n "$generated_conf" ]]; then
    rm -f "$generated_conf"
  fi
  reload_nginx
}

build_frontend() {
  local front_dir="$ROOT_DIR/frontend"
  run_user bash -c "cd '$front_dir' && npm ci --legacy-peer-deps"
  if run_user bash -c "cd '$front_dir' && npm run" | grep -q "^  test"; then
    run_user bash -c "cd '$front_dir' && npm run test"
  fi
  run_user bash -c "cd '$front_dir' && npm run build"
}

build_backend() {
  local backend_dir="$ROOT_DIR/backend"
  if [[ -d "$backend_dir/target" ]]; then
    run_sudo rm -rf "$backend_dir/target"
  fi
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

  run_user cp "$jar_path" "$backend_dir/target/app.jar"
}

wait_for_port() {
  local host="$1"
  local port="$2"
  local label="$3"
  local retries=90
  local count=0

  while (( count < retries )); do
    if (echo >"/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
      return 0
    fi
    count=$((count + 1))
    sleep 1
  done

  echo "Timeout waiting for ${label} at ${host}:${port}"
  exit 1
}

wait_for_health() {
  local service="$1"
  local label="$2"
  local retries=30
  local count=0
  local compose_file="$ROOT_DIR/deploy/docker-compose.yml"
  local project_name="ainovel-deps"

  local container_id
  container_id="$(run_sudo docker compose -p "$project_name" -f "$compose_file" ps -q "$service" 2>/dev/null || true)"
  if [[ -z "$container_id" ]]; then
    echo "Unable to resolve container for ${label}"
    exit 1
  fi

  while (( count < retries )); do
    local status
    status="$(run_sudo docker inspect -f '{{.State.Health.Status}}' "$container_id" 2>/dev/null || true)"
    if [[ "$status" == "healthy" ]]; then
      return 0
    fi
    if [[ "$status" == "unhealthy" ]]; then
      echo "${label} reported unhealthy status"
      exit 1
    fi
    count=$((count + 1))
    sleep 2
  done

  echo "Timeout waiting for ${label} to become healthy"
  exit 1
}

rollout_compose() {
  local compose_file="$ROOT_DIR/docker-compose.yml"
  if is_windows; then
    compose_file="$ROOT_DIR/docker-compose.windows.yml"
  fi
  run_sudo docker compose -f "$compose_file" down --remove-orphans
  run_sudo docker compose -f "$compose_file" up -d
}

build_frontend
build_backend
echo "使用统一依赖：MYSQL=${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3308} REDIS=${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6381} QDRANT=${QDRANT_HOST:-http://127.0.0.1}:${QDRANT_PORT:-6335} CONSUL=${CONSUL_SCHEME:-http}://${CONSUL_HOST:-127.0.0.1}:${CONSUL_PORT:-8502}"
rollout_compose

if [[ "$INIT_MODE" == "true" ]]; then
  if is_windows; then
    echo "--init 需要修改 hosts 与配置系统 Nginx，仅支持 Linux 环境。Windows 环境请手动配置或在 Linux 服务器上执行。" >&2
  else
    ensure_nginx_installed
    ensure_hosts_entry
    install_dev_nginx_conf
  fi
fi

echo "Deployment finished. Frontend: http://ainovel.seekerhut.com. Backend API: http://ainovel.seekerhut.com:11041/api"
