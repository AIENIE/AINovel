# Build stage
FROM maven:3.9-eclipse-temurin-25@sha256:dde1fa3cdee15fc123221a5b325b30eb30d74c9a3433305704cdcbc1ce67b111 AS builder
WORKDIR /app
COPY pom.xml .
RUN \
    mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN \
    mvn -q -e -DskipTests clean package

# Runtime stage
FROM eclipse-temurin:25-jre@sha256:7c1c6297dc3a3ff947922f3ab14ecd326e29083b9edaa8dbff3b94fef1688311
WORKDIR /app
RUN groupadd --system --gid 10001 ainovel \
    && useradd --system --uid 10001 --gid ainovel --home-dir /nonexistent --shell /usr/sbin/nologin ainovel \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/ai-novel-backend-0.1.0.jar app.jar
COPY start-backend.sh /app/bin/start-backend.sh
RUN chmod 0444 /app/app.jar \
    && chmod 0555 /app/bin/start-backend.sh
ENV PORT=11041
EXPOSE 11041
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 \
  CMD /opt/java/openjdk/bin/java -Dloader.main=com.ainovel.app.health.StrictHttpHealthProbe -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher http://127.0.0.1:11041/api/actuator/health/readiness
USER ainovel:ainovel
ENTRYPOINT ["/app/bin/start-backend.sh"]
