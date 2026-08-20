# ============================================================
# Stage 1: Build Application
# ============================================================
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

# Copy Maven wrapper and project descriptor
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Normalize Windows line endings and make Maven wrapper executable
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Copy application source code
COPY src ./src

# Build the Spring Boot application without running tests
RUN ./mvnw clean package -DskipTests


# ============================================================
# Stage 2: Production Runtime Image
# ============================================================
FROM eclipse-temurin:25-jre-noble AS runner

WORKDIR /app

# Install curl for the Docker container health check
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create an unprivileged user for improved container security
RUN groupadd -g 1001 slicelink && \
    useradd -u 1001 -g slicelink -m -s /bin/sh slicelink

# Copy the compiled Spring Boot JAR from the builder stage
COPY --from=builder \
    --chown=slicelink:slicelink \
    /workspace/target/slicelink-backend-0.0.1-SNAPSHOT.jar \
    /app/slicelink.jar

# Configure JVM for container-aware memory management
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Spring Boot server port
ENV SERVER_PORT=8080

# Run the application as a non-root user
USER slicelink

# Expose Spring Boot application port
EXPOSE 8080

# Docker health check using Spring Boot Actuator
HEALTHCHECK \
    --interval=15s \
    --timeout=5s \
    --start-period=30s \
    --retries=3 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

# Start Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/slicelink.jar"]