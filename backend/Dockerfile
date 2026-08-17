# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -e -DskipTests clean package

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/ai-novel-backend-0.1.0.jar app.jar
EXPOSE 11041
ENV JAVA_OPTS=""
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 CMD curl -fsS http://127.0.0.1:11041/api/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
