# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl \
    && addgroup --system --gid 10001 spring \
    && adduser --system --uid 10001 --ingroup spring --home /app spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/config-server-*.jar app.jar

USER 10001:10001
EXPOSE 8888

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
