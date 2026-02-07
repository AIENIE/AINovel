#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
echo "AINovel 已切换为统一通用依赖模式；请在树莓派 common-services 目录统一启动 MySQL/Redis。"
