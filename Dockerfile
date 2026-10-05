# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — BUILD
# Uses full JDK + Maven to compile, test-skip, and package the fat JAR.
# The build layer is discarded; only the JAR is copied to the runtime image.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy dependency descriptor first for better Docker layer caching.
# Dependency downloads are cached as long as pom.xml does not change.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build (tests run against H2 — safe to include in build image)
COPY src ./src
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — RUNTIME
# Minimal JRE-only image.  No Maven, no JDK compiler, no source code.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Install curl so the HEALTHCHECK can call /actuator/health
RUN apk add --no-cache curl

# ── Security: run as non-root ─────────────────────────────────────────────────
RUN addgroup -S tcgroup && adduser -S tcuser -G tcgroup
USER tcuser

WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=builder --chown=tcuser:tcgroup \
     /build/target/train-concierge-*.jar app.jar

# Expose the application port
EXPOSE 8080

# ── Health check ──────────────────────────────────────────────────────────────
# Polls Spring Actuator /actuator/health every 30 s.
# Container is marked unhealthy after 3 consecutive failures.
# start_period gives the JVM time to start before the first check fires.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# ── JVM tuning for containers ─────────────────────────────────────────────────
# -XX:+UseContainerSupport   → respect Docker CPU/memory limits (default in JDK 17)
# -XX:MaxRAMPercentage=75.0  → use at most 75 % of container RAM for the heap
# -Djava.security.egd        → faster random number generation (important for JWT)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
