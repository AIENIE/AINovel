#!/bin/sh
set -eu
fail(){ printf 'AINovel production env loader: %s\n' "$*" >&2; exit 1; }
[ "$#" -eq 1 ] || fail 'exactly one env file is required'
file=$1; [ "$file" = /app/env.txt ] || fail 'runtime env path is fixed'
[ -f "$file" ] && [ ! -L "$file" ] && [ -r "$file" ] || fail 'runtime env must be a readable regular non-link file'
case "$(stat -c '%a' -- "$file")" in 400|600) ;; *) fail 'runtime env must be owner-only (0400 or 0600)';; esac
loaded='|'; cr=$(printf '\r')
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in *"$cr"*) fail 'CR bytes are forbidden';; ''|'#'*) continue;; export\ *) fail 'export syntax is forbidden';; *=*) ;; *) fail 'non-assignment line is forbidden';; esac
  key=${line%%=*}; value=${line#*=}
  case "$key" in ''|[0-9]*|*[!A-Za-z0-9_]*) fail 'invalid environment key';; esac
  case "$loaded" in *"|$key|"*) fail "duplicate environment key: $key";; esac; loaded="${loaded}${key}|"
  case "$key" in PATH|JAVA_HOME|JRE_HOME|CLASSPATH|JAVA_OPTS|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_*|BASH_ENV|IFS|LD_*|DYLD_*|GCONV_PATH|LOCPATH|NLSPATH|HOSTALIASES|LOCALDOMAIN|RES_OPTIONS|HTTP_PROXY|HTTPS_PROXY|ALL_PROXY|NO_PROXY|SOCKS_PROXY|SSL_CERT_FILE|SSL_CERT_DIR|JAVA_SECURITY_*|JAVAX_NET_SSL_*|JDK_TLS_*|SPRING_APPLICATION_JSON|SPRING_CONFIG_*|SPRING_PROFILES_*|SERVER_ADDRESS|SERVER_PORT|EXTERNAL_GRPC_TRUST_CERT_COLLECTION) fail "target-policy-owned variable is forbidden: $key";; esac
  export "$key=$value"
done < "$file"
