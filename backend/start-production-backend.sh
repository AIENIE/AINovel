#!/bin/sh
set -eu
JAVA_HOME=/opt/java/openjdk; PATH=/opt/java/openjdk/bin:/usr/bin:/bin; export JAVA_HOME PATH
unset JAVA_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS MAVEN_OPTS MAVEN_ARGS CLASSPATH BASH_ENV \
  LD_PRELOAD LD_LIBRARY_PATH LD_AUDIT HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY SSL_CERT_FILE SSL_CERT_DIR \
  GRPC_DEFAULT_SSL_ROOTS_FILE_PATH EXTERNAL_GRPC_TRUST_CERT_COLLECTION ELASTICSEARCH_HOSTS ELASTICSEARCH_USERNAME ELASTICSEARCH_PASSWORD
[ "$#" -eq 1 ] || { echo 'AINovel production launcher requires one env file' >&2; exit 64; }
[ -r /app/bin/production-load-env-file.sh ] || { echo 'AINovel production env loader is unavailable' >&2; exit 1; }
. /app/bin/production-load-env-file.sh "$1"
[ "${ENV:-}" = production ] || { echo 'AINovel production ENV is required' >&2; exit 1; }
export SPRING_PROFILES_ACTIVE=production SERVER_ADDRESS=0.0.0.0 SERVER_PORT=11041 PORT=11041
export DB_URL='jdbc:mysql://base.seekerhut.com:13306/ainovel?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=false&serverTimezone=Asia/Shanghai'
export SPRING_FLYWAY_ENABLED=false SPRING_JPA_HIBERNATE_DDL_AUTO=none
export REDIS_HOST=base.seekerhut.com REDIS_PORT=16379 REDIS_SSL_ENABLED=true
export QDRANT_HOST=https://base.seekerhut.com QDRANT_PORT=16333
export USER_HTTP_ADDR=https://userservice.seekerhut.com
export USER_GRPC_ADDR=static://userservice.seekerhut.com:12001
export AI_GRPC_ADDR=static://aiservice.seekerhut.com:12011
export PAY_GRPC_ADDR=static://payservice.seekerhut.com:12021
export EXTERNAL_GRPC_TLS_ENABLED=true EXTERNAL_GRPC_PLAINTEXT_ENABLED=false
export SSO_CALLBACK_ORIGIN=https://ainovel.seekerhut.com
export ADMIN_TRUSTED_ORIGINS=https://ainovel.seekerhut.com
export APP_RECORD_ES_QUERY_ENABLED=false APP_LOG_DIR=/app/logs APP_RECORD_DIR=/app/records
exec /opt/java/openjdk/bin/java -Duser.timezone=Asia/Shanghai -jar /app/app.jar
