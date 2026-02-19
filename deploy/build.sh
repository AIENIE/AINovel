#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"

cd "$DEPLOY_DIR"
docker compose down --remove-orphans || true
docker compose up -d

echo "AINovel deploy dependencies started:"
echo "  - MySQL:  127.0.0.1:3308"
echo "  - Redis:  127.0.0.1:6381"
echo "  - Qdrant: 127.0.0.1:6335"
echo "  - Consul: 127.0.0.1:8502"
