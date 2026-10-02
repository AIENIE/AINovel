#!/bin/sh
set -eu
JAVA_HOME=/opt/java/openjdk; PATH=/opt/java/openjdk/bin:/usr/bin:/bin; export JAVA_HOME PATH
unset JAVA_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS MAVEN_OPTS MAVEN_ARGS CLASSPATH BASH_ENV \
  LD_PRELOAD LD_LIBRARY_PATH LD_AUDIT HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY SSL_CERT_FILE SSL_CERT_DIR \
  GRPC_DEFAULT_SSL_ROOTS_FILE_PATH EXTERNAL_GRPC_TRUST_CERT_COLLECTION ELASTICSEARCH_HOSTS ELASTICSEARCH_USERNAME ELASTICSEARCH_PASSWORD
[ "$#" -eq 1 ] || { echo 'AINovel production launcher requires one env file' >&2; exit 64; }
[ -r /app/bin/production-load-env-file.sh ] || { echo 'AINovel production env loader is unavailable' >&2; exit 1; }
. /app/bin/production-load-env-file.sh
load_one_env_file "$1"
[ -f /app/application.yml ] || exit 1
export SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/application.yml
export SPRING_PROFILES_ACTIVE=production
/opt/java/openjdk/bin/java -Dloader.main=com.aienie.configpair.ConfigurationPreflight -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher production
[ -f /app/application.yml ] || { echo "runtime application.yml is missing" >&2; exit 1; }
export SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/application.yml
exec /opt/java/openjdk/bin/java -Duser.timezone=Asia/Shanghai -jar /app/app.jar
