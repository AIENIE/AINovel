#!/usr/bin/env sh
set -eu
[ "$#" -eq 1 ] || { echo 'AINovel migration action is required' >&2; exit 64; }
case "$1" in checkpoint|precheck|execute|reconcile) ;; *) echo 'AINovel migration action is invalid' >&2; exit 64;; esac
[ -r /app/env.txt ] || { echo 'AINovel protected environment is unavailable' >&2; exit 70; }
[ -r /app/bin/production-load-env-file.sh ] || { echo 'AINovel environment loader is unavailable' >&2; exit 70; }
. /app/bin/production-load-env-file.sh /app/env.txt
[ "${ENV:-}" = production ] || { echo 'AINovel production environment is required' >&2; exit 70; }
export AIENIE_NOVEL_MIGRATION_LEDGER=/app/release/migrations/flyway-ledger.json
exec /opt/java/openjdk/bin/java \
  -Dloader.main=com.ainovel.app.config.ProductionNovelMigrationMain \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher "$1"
