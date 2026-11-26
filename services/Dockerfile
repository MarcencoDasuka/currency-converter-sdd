# syntax=docker/dockerfile:1

# Stage 1: Multi-module Maven build with locked BuildKit cache mount for /root/.m2/repository
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

ARG SERVICE_NAME

COPY pom.xml ./
COPY common-lib/pom.xml common-lib/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY rate-service/pom.xml rate-service/pom.xml
COPY conversion-service/pom.xml conversion-service/pom.xml
COPY alert-service/pom.xml alert-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

COPY common-lib/src common-lib/src
COPY ${SERVICE_NAME}/src ${SERVICE_NAME}/src

RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn package -pl "${SERVICE_NAME}" -am -DskipTests -B \
    && cp "${SERVICE_NAME}/target/${SERVICE_NAME}-"*.jar /workspace/app.jar

# Stage 2: Minimal non-root JRE runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

ARG SERVER_PORT=8080
ENV SERVER_PORT=${SERVER_PORT}
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

RUN addgroup -g 1000 appgroup \
    && adduser -u 1000 -G appgroup -D -H appuser

COPY --from=builder --chown=1000:1000 /workspace/app.jar /app/app.jar

USER 1000:1000
EXPOSE ${SERVER_PORT}

HEALTHCHECK --interval=10s --timeout=3s --start-period=25s --retries=5 \
  CMD wget -q --spider "http://127.0.0.1:${SERVER_PORT}/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
