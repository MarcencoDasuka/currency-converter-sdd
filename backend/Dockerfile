# Stage 1: Build JAR using Maven Wrapper on Java 21 JDK
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install curl in build stage for wrapper dependency fetching if needed
RUN apk add --no-cache curl

# Copy Maven Wrapper and POM first to leverage Docker layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Normalize line endings and grant execute permissions
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Pre-fetch dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and package application
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal hardened JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-privileged user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/currency-converter-backend-1.0.0.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=25s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/api/v1/currencies || exit 1

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
