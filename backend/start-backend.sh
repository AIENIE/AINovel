#!/bin/sh
set -eu

JAVA_HOME=/opt/java/openjdk
PATH=/opt/java/openjdk/bin:/usr/bin:/bin
export JAVA_HOME PATH
unset JAVA_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS MAVEN_OPTS MAVEN_ARGS \
  CLASSPATH BASH_ENV LD_PRELOAD LD_LIBRARY_PATH LD_AUDIT HTTP_PROXY HTTPS_PROXY \
  ALL_PROXY NO_PROXY SSL_CERT_FILE SSL_CERT_DIR GRPC_DEFAULT_SSL_ROOTS_FILE_PATH

[ "$#" -eq 1 ] || {
  echo "AINovel launcher requires exactly one runtime env file" >&2
  exit 64
}
[ -r /app/bin/staging-load-env-file.sh ] || {
  echo "AINovel runtime env loader is unavailable" >&2
  exit 1
}
# shellcheck source=/dev/null
. /app/bin/staging-load-env-file.sh "$1"

[ "${ENV:-}" = test ] || {
  echo 'AINovel reviewed launcher is staging-only; production remains frozen' >&2
  exit 1
}

export SERVER_ADDRESS=0.0.0.0 SERVER_PORT=11041 PORT=11041
exec /opt/java/openjdk/bin/java -Duser.timezone=Asia/Shanghai \
  -Dapp.external.grpc.trust-cert-collection=/run/aienie/trust/staging-root.pem \
  -jar /app/app.jar
