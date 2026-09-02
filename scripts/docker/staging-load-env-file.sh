#!/bin/sh
set -eu

fail() {
  printf 'AINovel staging env loader: %s\n' "$*" >&2
  exit 1
}

[ "$#" -eq 1 ] || fail 'exactly one env file is required'
env_file="$1"
[ "$env_file" = /app/env.txt ] || fail 'runtime env path is fixed by target policy'
[ -f "$env_file" ] && [ ! -L "$env_file" ] && [ -r "$env_file" ] || fail 'runtime env must be a readable regular non-link file'

loaded='|'
cr=$(printf '\r')
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    *"$cr"*) fail 'CR bytes are forbidden in the canonical env file' ;;
    ''|'#'*) continue ;;
    export\ *) fail 'export syntax is forbidden; use exact KEY=value lines' ;;
    *=*) ;;
    *) fail 'non-assignment line is forbidden' ;;
  esac
  key=${line%%=*}
  value=${line#*=}
  case "$key" in
    ''|[0-9]*|*[!A-Za-z0-9_]*) fail 'invalid environment key' ;;
  esac
  case "$loaded" in
    *"|$key|"*) fail "duplicate environment key: $key" ;;
  esac
  loaded="${loaded}${key}|"
  case "$key" in
    PATH|JAVA_HOME|JRE_HOME|CLASSPATH|JAVA_OPTS|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_*|BASH_ENV|IFS|LD_*|DYLD_*|GCONV_PATH|LOCPATH|NLSPATH|HOSTALIASES|LOCALDOMAIN|RES_OPTIONS|HTTP_PROXY|HTTPS_PROXY|ALL_PROXY|NO_PROXY|SOCKS_PROXY|SSL_CERT_FILE|SSL_CERT_DIR|JAVA_SECURITY_*|JAVAX_NET_SSL_*|JDK_TLS_*|SPRING_APPLICATION_JSON|SPRING_CONFIG_*|SPRING_PROFILES_*|SERVER_ADDRESS|SERVER_PORT|EXTERNAL_GRPC_TRUST_CERT_COLLECTION)
      fail "target-policy-owned variable is forbidden: $key"
      ;;
  esac
  export "$key=$value"
done < "$env_file"
