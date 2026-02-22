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

if [[ "$INIT_MODE" == "true" ]]; then
  bash "$ROOT_DIR/build.sh" --init
else
  bash "$ROOT_DIR/build.sh"
fi

echo "Production deployment finished: https://${APP_PROD_DOMAIN:-ainovel.aienie.com}"
