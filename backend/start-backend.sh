#!/bin/sh
set -eu


[ "$#" -eq 1 ] || {
  echo "AINovel launcher requires exactly one runtime env file" >&2
  exit 64
}
[ -r /app/bin/staging-load-env-file.sh ] || {
  echo "AINovel runtime env loader is unavailable" >&2
  exit 1
}
# shellcheck source=/dev/null
. /app/bin/staging-load-env-file.sh
load_one_env_file "$1"



[ -f /app/application.yml ] || { echo "runtime application.yml is missing" >&2; exit 1; }
export SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/application.yml
exec /opt/java/openjdk/bin/java -Duser.timezone=Asia/Shanghai \
  -Dapp.external.grpc.trust-cert-collection=/run/aienie/trust/staging-root.pem \
  -jar /app/app.jar
