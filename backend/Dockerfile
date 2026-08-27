# =============================================================================
# Build stage
#
# Dependencies are resolved in a layer of their own, keyed on pom.xml alone, so
# that editing source code does not re-download the whole dependency tree on
# every build.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests are skipped here on purpose. They need a Docker daemon for Testcontainers,
# which is not available inside this build, and they belong in CI rather than in
# an image build.
RUN mvn -B -ntp clean package -DskipTests

# =============================================================================
# Runtime stage
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# Run as an unprivileged user. A process that only needs to read its own jar and
# write to one directory has no reason to be root.
RUN addgroup -S knowledge && adduser -S -G knowledge knowledge

WORKDIR /app

COPY --from=build /build/target/knowledge-base-*.jar app.jar

# Uploaded files live here. Mounted as a volume in docker-compose so they survive
# a container rebuild.
RUN mkdir -p /app/storage && chown -R knowledge:knowledge /app

USER knowledge

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENV STORAGE_ROOT=/app/storage

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
