#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUN_DIR="$ROOT_DIR/tmp/local-run"
BACKEND_LOG="$RUN_DIR/backend.out.log"
BACKEND_ERR="$RUN_DIR/backend.err.log"
FRONTEND_LOG="$RUN_DIR/frontend.out.log"
FRONTEND_ERR="$RUN_DIR/frontend.err.log"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"

if [[ $# -gt 0 ]]; then
  echo "Usage: $0"
  exit 1
fi

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd"
    exit 1
  fi
}

set_default_env() {
  local key="$1"
  local value="$2"
  if [[ -z "${!key:-}" ]]; then
    export "$key=$value"
  fi
}

load_env_file() {
  local env_file="$1"
  if [[ ! -f "$env_file" ]]; then
    return 1
  fi

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line key value
    line="${raw_line%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" != *"="* ]] && continue

    key="${line%%=*}"
    value="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi

    if [[ ${#value} -ge 2 ]]; then
      if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then
        value="${value:1:${#value}-2}"
      elif [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
        value="${value:1:${#value}-2}"
      fi
    fi

    if [[ -z "${!key+x}" ]]; then
      export "$key=$value"
    fi
  done <"$env_file"
  return 0
}

load_env_sources() {
  local loaded=false
  if load_env_file "$ROOT_DIR/.env"; then
    loaded=true
  fi
  if load_env_file "$ROOT_DIR/env.txt"; then
    loaded=true
  fi
  if [[ "$loaded" == false ]]; then
    echo "No .env or env.txt found, using built-in defaults."
  fi
}

stop_managed_process() {
  local pid_file="$1"
  local name="$2"
  if [[ ! -f "$pid_file" ]]; then
    return
  fi

  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    echo "Stopping existing $name process ($pid)"
    kill "$pid" 2>/dev/null || true
    for _ in {1..20}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  fi

  rm -f "$pid_file"
}

wait_for_port() {
  local host="$1"
  local port="$2"
  local label="$3"
  local retries=180
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

wait_for_http() {
  local url="$1"
  local label="$2"
  local retries=180
  local count=0

  while (( count < retries )); do
    if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
      return 0
    fi
    count=$((count + 1))
    sleep 1
  done

  echo "Timeout waiting for ${label} at ${url}"
  exit 1
}

build_frontend() {
  local front_dir="$ROOT_DIR/frontend"
  (
    cd "$front_dir"
    npm ci --legacy-peer-deps
    npm run build
  )
}

build_backend() {
  local backend_dir="$ROOT_DIR/backend"
  if [[ -d "$backend_dir/target" ]]; then
    rm -rf "$backend_dir/target"
  fi

  (
    cd "$backend_dir"
    mvn -q test
    mvn -q -Dmaven.test.skip=true clean package
  )

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

  cp "$jar_path" "$backend_dir/target/app.jar"
}

start_backend() {
  local backend_dir="$ROOT_DIR/backend"
  local jar_path="$backend_dir/target/app.jar"
  local -a cmd
  cmd=(java)
  if [[ -n "${JAVA_OPTS:-}" ]]; then
    # shellcheck disable=SC2206
    local java_opts=( ${JAVA_OPTS} )
    cmd+=("${java_opts[@]}")
  fi
  cmd+=(-jar "$jar_path")

  (
    cd "$backend_dir"
    nohup "${cmd[@]}" >"$BACKEND_LOG" 2>"$BACKEND_ERR" &
    echo $! >"$BACKEND_PID_FILE"
  )
}

start_frontend() {
  local front_dir="$ROOT_DIR/frontend"
  (
    cd "$front_dir"
    nohup npm run dev -- --host 0.0.0.0 --port 10010 --strictPort >"$FRONTEND_LOG" 2>"$FRONTEND_ERR" &
    echo $! >"$FRONTEND_PID_FILE"
  )
}

print_startup_logs_on_failure() {
  echo "Backend stderr (tail):"
  tail -n 80 "$BACKEND_ERR" 2>/dev/null || true
  echo "Frontend stderr (tail):"
  tail -n 80 "$FRONTEND_ERR" 2>/dev/null || true
}

main() {
  require_command npm
  require_command mvn
  require_command java
  require_command curl

  mkdir -p "$RUN_DIR"
  load_env_sources
  set_default_env MYSQL_HOST 127.0.0.1
  set_default_env MYSQL_PORT 3308
  set_default_env MYSQL_DB ainovel
  set_default_env MYSQL_USER root
  set_default_env MYSQL_PASSWORD 123456
  set_default_env REDIS_HOST 127.0.0.1
  set_default_env REDIS_PORT 6381
  set_default_env REDIS_PASSWORD ""
  set_default_env REDIS_KEY_PREFIX aienie:ainovel:
  set_default_env QDRANT_HOST http://127.0.0.1
  set_default_env QDRANT_PORT 6335
  set_default_env QDRANT_ENABLED true
  set_default_env CONSUL_ENABLED true
  set_default_env CONSUL_SCHEME http
  set_default_env CONSUL_HOST 127.0.0.1
  set_default_env CONSUL_PORT 8502
  set_default_env USER_GRPC_SERVICE_NAME aienie-userservice-grpc
  set_default_env USER_GRPC_SERVICE_TAG ""
  set_default_env USER_SESSION_GRPC_TIMEOUT_MS 2000
  set_default_env USER_GRPC_ADDR static://127.0.0.1:13001
  set_default_env SSO_SESSION_VALIDATION_ENABLED true
  set_default_env DB_URL "jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"
  set_default_env DB_USERNAME "${MYSQL_USER}"
  set_default_env DB_PASSWORD "${MYSQL_PASSWORD}"
  set_default_env PORT 10011

  stop_managed_process "$BACKEND_PID_FILE" backend
  stop_managed_process "$FRONTEND_PID_FILE" frontend

  build_frontend
  build_backend

  start_backend
  start_frontend

  if ! wait_for_port 127.0.0.1 10011 backend; then
    print_startup_logs_on_failure
    exit 1
  fi
  if ! wait_for_http "http://127.0.0.1:10010" frontend; then
    print_startup_logs_on_failure
    exit 1
  fi

  echo "Local deployment finished."
  echo "Frontend: http://127.0.0.1:10010"
  echo "Backend API: http://127.0.0.1:10011/api"
  echo "Backend PID file: $BACKEND_PID_FILE"
  echo "Frontend PID file: $FRONTEND_PID_FILE"
}

main
