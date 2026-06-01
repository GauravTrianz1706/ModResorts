# =============================================================================
# ModResorts - Multi-Stage Dockerfile
# Java EE WAR application on Open Liberty
# Build Tool: Maven | Java Version: 8 | Package: WAR
# =============================================================================

# ---- Stage 1: Builder ----
FROM maven:3.9.4-eclipse-temurin-8 AS builder

WORKDIR /workspace

# Copy build descriptor first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer if pom.xml unchanged)
RUN mvn dependency:go-offline -B

# Copy full project source
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:8-jdk

# Install Open Liberty runtime
ARG LIBERTY_VERSION=23.0.0.12
ENV LIBERTY_HOME=/opt/ol/wlp

# Install required tools and Open Liberty
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        unzip \
    && curl -fsSL "https://public.dhe.ibm.com/ibmdl/export/pub/software/openliberty/runtime/release/${LIBERTY_VERSION}/openliberty-${LIBERTY_VERSION}.zip" \
       -o /tmp/openliberty.zip \
    && unzip -q /tmp/openliberty.zip -d /opt/ol \
    && rm /tmp/openliberty.zip \
    && apt-get remove -y curl unzip \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup -d /home/appuser -s /bin/bash appuser \
    && mkdir -p /home/appuser \
    && chown -R appuser:appgroup /home/appuser

# Create Liberty server
RUN ${LIBERTY_HOME}/bin/server create modresorts

# Copy server configuration
COPY --chown=appuser:appgroup docker/server.xml ${LIBERTY_HOME}/usr/servers/modresorts/server.xml

# Copy the built WAR from builder stage
COPY --from=builder --chown=appuser:appgroup /workspace/target/modresorts-2.0.0.war \
    ${LIBERTY_HOME}/usr/servers/modresorts/apps/modresorts.war

# Set ownership of Liberty directory
RUN chown -R appuser:appgroup ${LIBERTY_HOME}

# Environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions" \
    TZ=UTC \
    WLP_OUTPUT_DIR=/opt/ol/wlp/output \
    LOG_DIR=/opt/ol/wlp/output/modresorts/logs \
    LIBERTY_HOME=/opt/ol/wlp

# Application port (HTTP) and HTTPS port
EXPOSE 9080 9443

# Switch to non-root user
USER appuser

# Graceful shutdown support
STOPSIGNAL SIGTERM

# Start Liberty server in foreground
CMD ["/opt/ol/wlp/bin/server", "run", "modresorts"]
