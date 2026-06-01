#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

COMPOSE_ARGS=(-f "$ROOT_DIR/docker-compose.yml")
if [[ -f "$ROOT_DIR/env.txt" ]]; then
  COMPOSE_ARGS=(--env-file "$ROOT_DIR/env.txt" "${COMPOSE_ARGS[@]}")
fi

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

  echo "Removing conflicting container $name from ${working_dir:-unknown working dir}"
  docker rm -f "$name" >/dev/null
}

cleanup_conflicting_container "ainovel-backend"
cleanup_conflicting_container "ainovel-frontend"

for attempt in 1 2 3; do
  if docker compose "${COMPOSE_ARGS[@]}" up -d --build --pull never --remove-orphans; then
    exit 0
  fi

  if [[ "$attempt" == "3" ]]; then
    break
  fi

  echo "Docker Compose build failed; retrying ($((attempt + 1))/3)..." >&2
  sleep 3
done

exit 1
