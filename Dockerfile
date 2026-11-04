# syntax=docker/dockerfile:1

# ---- build: JDK + Maven wrapper -------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so this layer is reused until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers); Spotless still runs in `validate`.
RUN ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/invoicing-saas-*-SNAPSHOT.jar \
        extract --layers --launcher --destination target/extracted

# ---- runtime: JRE only, non-root ------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home --home /app app

# Spring Boot layers, least to most frequently changing: a code-only change re-pulls only the
# last layer.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
EXPOSE 8080

# Size the heap from the container's memory limit, not the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# No HEALTHCHECK: the runtime image has no curl. Platforms probe
# /actuator/health/liveness and /actuator/health/readiness over HTTP instead.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
