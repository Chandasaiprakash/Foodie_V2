# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and pom first — layer cache for dependency downloads
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source and build (tests are run by CI, not during Docker build)
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S foodie && adduser -S foodie -G foodie
USER foodie

COPY --from=builder /app/target/*.jar app.jar

# JVM tuning: container-aware heap, virtual threads friendly
ENV JAVA_OPTS="-XX:+UseContainerSupport -Djdk.tracePinnedThreads=short \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseZGC \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
