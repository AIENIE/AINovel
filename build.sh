#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/lib/deployment-env.sh
source "$ROOT_DIR/scripts/lib/deployment-env.sh"
ENV_FILE="$ROOT_DIR/env.txt"
ainovel_read_env_file "$ENV_FILE"
ainovel_require_deployment_env_sources
COMPOSE_ARGS=(--env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.yml")

cleanup_conflicting_container() {
  local name="$1"
  if ! docker inspect "$name" >/dev/null 2>&1; then
    return 0
  fi

  local working_dir
  working_dir="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$name" 2>/dev/null || true)"
  if [[ "$working_dir" == "$ROOT_DIR" ]]; then
    return 0
  fi

  local current_branch
  current_branch="$(git -C "$ROOT_DIR" branch --show-current)"
  if [[ "$current_branch" != "master" && "${AINOVEL_ALLOW_SHARED_DEPLOY:-}" != "1" ]]; then
    echo "Refusing to replace shared AINovel containers owned by ${working_dir:-unknown working dir} from branch ${current_branch:-detached}." >&2
    echo "Deploy from master, or set AINOVEL_ALLOW_SHARED_DEPLOY=1 for an intentional shared deployment." >&2
    exit 1
  fi

  echo "Removing conflicting container $name from ${working_dir:-unknown working dir}"
  docker rm -f "$name" >/dev/null
}

cleanup_conflicting_container "ainovel-backend"
cleanup_conflicting_container "ainovel-frontend"

for attempt in 1 2 3; do
  if ainovel_run_without_host_runtime_env docker compose "${COMPOSE_ARGS[@]}" up -d --build --pull never --remove-orphans; then
    healthy=0
    for _ in $(seq 1 60); do
      backend_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' ainovel-backend 2>/dev/null || true)"
      frontend_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' ainovel-frontend 2>/dev/null || true)"
      if [[ "$backend_health" == "healthy" && "$frontend_health" == "healthy" ]]; then
        healthy=1
        break
      fi
      if [[ "$backend_health" == "unhealthy" || "$frontend_health" == "unhealthy" ]]; then
        break
      fi
      sleep 2
    done
    if [[ "$healthy" == "1" ]]; then
      exit 0
    fi
    echo "Deployment did not become ready (backend=$backend_health frontend=$frontend_health)." >&2
    docker compose "${COMPOSE_ARGS[@]}" ps >&2 || true
    docker compose "${COMPOSE_ARGS[@]}" logs --tail 100 backend frontend >&2 || true
  fi

  if [[ "$attempt" == "3" ]]; then
    break
  fi

  echo "Docker Compose build failed; retrying ($((attempt + 1))/3)..." >&2
  sleep 3
done

exit 1
