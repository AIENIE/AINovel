#!/usr/bin/env bash
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$BACKEND_DIR/.." && pwd)"
RUN_DIR="$PROJECT_DIR/tmp/local-run"
BACKEND_OUT_LOG="$RUN_DIR/backend.out.log"
BACKEND_ERR_LOG="$RUN_DIR/backend.err.log"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"

# shellcheck source=scripts/runtime-env.sh
source "$BACKEND_DIR/scripts/runtime-env.sh"

usage() {
  echo "Usage: $0 <start|stop|restart|status|logs>" >&2
  exit 1
}

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: $name" >&2
    exit 1
  fi
}

bootstrap_runtime_env() {
  local require_env_file="$1"
  local env_file=""
  if env_file="$(resolve_backend_env_file "$BACKEND_DIR")"; then
    load_env_file_preserve_existing "$env_file"
  elif [[ "$require_env_file" == "true" ]]; then
    exit 1
  fi
  apply_backend_runtime_defaults
  APP_PORT="${PORT:-${BACKEND_PORT:-11041}}"
  ENV_SOURCE="${env_file:-}"
}

read_pid_file() {
  if [[ ! -f "$BACKEND_PID_FILE" ]]; then
    return 1
  fi

  local raw
  raw="$(tr -d '[:space:]' < "$BACKEND_PID_FILE")"
  if [[ "$raw" =~ ^[0-9]+$ ]]; then
    printf '%s\n' "$raw"
    return 0
  fi
  return 1
}

is_pid_alive() {
  local pid="$1"
  kill -0 "$pid" 2>/dev/null
}

list_listening_pids() {
  local port="$1"

  if command -v lsof >/dev/null 2>&1; then
    lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk '!seen[$0]++'
    return 0
  fi

  if command -v ss >/dev/null 2>&1; then
    ss -ltnp "sport = :$port" 2>/dev/null | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | awk '!seen[$0]++'
    return 0
  fi

  if command -v fuser >/dev/null 2>&1; then
    fuser -n tcp "$port" 2>/dev/null | tr ' ' '\n' | awk '/^[0-9]+$/ && !seen[$0]++'
    return 0
  fi

  echo "Missing a port inspection command (lsof, ss, or fuser)." >&2
  return 1
}

kill_pid() {
  local pid="$1"
  local label="$2"
  if ! is_pid_alive "$pid"; then
    return 0
  fi

  echo "Stopping $label (PID $pid)"
  kill "$pid" 2>/dev/null || true
  for _ in {1..20}; do
    if ! is_pid_alive "$pid"; then
      return 0
    fi
    sleep 0.5
  done

  echo "Force stopping $label (PID $pid)"
  kill -9 "$pid" 2>/dev/null || true
}

stop_recorded_process() {
  local pid=""
  if pid="$(read_pid_file)"; then
    kill_pid "$pid" "backend from pid file"
  fi
  rm -f "$BACKEND_PID_FILE"
}

stop_port_residue() {
  local pid
  local any=false
  while IFS= read -r pid; do
    [[ -z "$pid" ]] && continue
    any=true
    kill_pid "$pid" "listener on port $APP_PORT"
  done < <(list_listening_pids "$APP_PORT" || true)

  if [[ "$any" == "false" ]]; then
    echo "No listening residue found on port $APP_PORT"
  fi
}

build_backend() {
  require_command mvn
  mkdir -p "$RUN_DIR"
  (
    cd "$BACKEND_DIR"
    mvn -q -DskipTests clean package
  )

  local jar_path
  jar_path="$(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' ! -name 'app.jar' | LC_ALL=C sort | head -n 1)"
  if [[ -z "$jar_path" ]]; then
    echo "Packaged backend jar not found under $BACKEND_DIR/target" >&2
    exit 1
  fi

  cp "$jar_path" "$BACKEND_DIR/target/app.jar"
}

wait_for_port() {
  local port="$1"
  local timeout_seconds="${2:-180}"
  local deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    if [[ -f "$BACKEND_PID_FILE" ]]; then
      local pid=""
      pid="$(read_pid_file || true)"
      if [[ -n "$pid" ]] && ! is_pid_alive "$pid"; then
        echo "Backend exited before port $port became ready." >&2
        return 1
      fi
    fi

    if bash -lc "</dev/tcp/127.0.0.1/$port" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for backend port $port" >&2
  return 1
}

show_startup_logs() {
  echo "Backend stdout tail:"
  [[ -f "$BACKEND_OUT_LOG" ]] && tail -n 40 "$BACKEND_OUT_LOG" || true
  echo "Backend stderr tail:"
  [[ -f "$BACKEND_ERR_LOG" ]] && tail -n 80 "$BACKEND_ERR_LOG" || true
}

start_backend() {
  require_command java
  build_backend

  mkdir -p "$RUN_DIR"
  : > "$BACKEND_OUT_LOG"
  : > "$BACKEND_ERR_LOG"

  local java_opts="${JAVA_OPTS:-}"
  if [[ -z "$java_opts" ]]; then
    java_opts="-Duser.timezone=${APP_TIME_ZONE:-Asia/Shanghai}"
  fi

  local -a args=()
  if [[ -n "$java_opts" ]]; then
    # shellcheck disable=SC2206
    args=($java_opts)
  fi
  args+=("-jar" "$BACKEND_DIR/target/app.jar")

  (
    cd "$BACKEND_DIR"
    if command -v setsid >/dev/null 2>&1; then
      setsid java "${args[@]}" < /dev/null >> "$BACKEND_OUT_LOG" 2>> "$BACKEND_ERR_LOG" &
    else
      nohup java "${args[@]}" < /dev/null >> "$BACKEND_OUT_LOG" 2>> "$BACKEND_ERR_LOG" &
    fi
    echo $! > "$BACKEND_PID_FILE"
  )

  if ! wait_for_port "$APP_PORT"; then
    show_startup_logs
    exit 1
  fi

  echo "Backend started from ${ENV_SOURCE:-current environment}"
  echo "Port: $APP_PORT"
  echo "PID file: $BACKEND_PID_FILE"
  echo "Logs: $BACKEND_OUT_LOG / $BACKEND_ERR_LOG"
}

status_backend() {
  local pid=""
  local pid_alive=false
  if pid="$(read_pid_file)"; then
    if is_pid_alive "$pid"; then
      pid_alive=true
    fi
  fi

  local -a port_pids=()
  while IFS= read -r item; do
    [[ -z "$item" ]] && continue
    port_pids+=("$item")
  done < <(list_listening_pids "$APP_PORT" || true)

  if [[ "$pid_alive" == "true" && "${#port_pids[@]}" -gt 0 ]]; then
    echo "Backend is running. PID file: $pid. Listening on port $APP_PORT via PID(s): ${port_pids[*]}"
    return 0
  fi

  if [[ "$pid_alive" == "false" && "${#port_pids[@]}" -eq 0 ]]; then
    echo "Backend is stopped. No PID file process and no listener on port $APP_PORT."
    return 1
  fi

  echo "Backend is degraded. PID file process: ${pid:-none}. Listening PID(s) on port $APP_PORT: ${port_pids[*]:-none}"
  return 2
}

stop_backend() {
  mkdir -p "$RUN_DIR"
  stop_recorded_process
  stop_port_residue
  echo "Backend stopped."
}

show_logs() {
  echo "==> $BACKEND_OUT_LOG"
  [[ -f "$BACKEND_OUT_LOG" ]] && tail -n 80 "$BACKEND_OUT_LOG" || echo "(missing)"
  echo "==> $BACKEND_ERR_LOG"
  [[ -f "$BACKEND_ERR_LOG" ]] && tail -n 80 "$BACKEND_ERR_LOG" || echo "(missing)"
}

main() {
  local command="${1:-}"
  case "$command" in
    start)
      bootstrap_runtime_env true
      ensure_pay_service_jwt
      stop_backend
      start_backend
      ;;
    restart)
      bootstrap_runtime_env true
      ensure_pay_service_jwt
      stop_backend
      start_backend
      ;;
    stop)
      bootstrap_runtime_env false
      stop_backend
      ;;
    status)
      bootstrap_runtime_env false
      status_backend
      ;;
    logs)
      bootstrap_runtime_env false
      show_logs
      ;;
    *)
      usage
      ;;
  esac
}

main "$@"
