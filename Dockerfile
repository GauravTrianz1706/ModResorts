# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (layer cache)
RUN mvn dependency:go-offline -B

# Copy source code and web content
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR (skip tests)
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime  (explicit base image: eclipse-temurin:8-jre)
# Open Liberty is extracted on top of the JRE base image
# ============================================================
FROM eclipse-temurin:8-jre

ARG LIBERTY_VERSION=23.0.0.12

# Install minimal tools needed to bootstrap Liberty, then clean up
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip \
    && mkdir -p /opt/ol \
    && curl -fsSL "https://public.dhe.ibm.com/ibmdl/export/pub/software/openliberty/runtime/release/${LIBERTY_VERSION}/openliberty-${LIBERTY_VERSION}.zip" \
       -o /tmp/liberty.zip \
    && unzip /tmp/liberty.zip -d /opt/ol \
    && mv /opt/ol/wlp* /opt/ol/wlp 2>/dev/null || true \
    && rm /tmp/liberty.zip \
    && apt-get purge -y --auto-remove curl unzip \
    && rm -rf /var/lib/apt/lists/*

ENV LIBERTY_HOME=/opt/ol/wlp

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup -d ${LIBERTY_HOME} -s /bin/false appuser

# Create Liberty server
RUN ${LIBERTY_HOME}/bin/server create modresorts

# Copy Liberty server configuration
COPY --chown=appuser:appgroup docker/server.xml ${LIBERTY_HOME}/usr/servers/modresorts/server.xml

# Copy WAR artifact from builder stage
COPY --from=builder --chown=appuser:appgroup /workspace/target/modresorts-2.0.0.war \
    ${LIBERTY_HOME}/usr/servers/modresorts/apps/modresorts.war

# Set ownership on server directory
RUN chown -R appuser:appgroup ${LIBERTY_HOME}/usr/servers/modresorts

# Environment variables
ENV TZ=UTC \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    WLP_OUTPUT_DIR=/opt/ol/wlp/output \
    LOG_DIR=/opt/ol/wlp/output/modresorts/logs \
    WEATHER_API_KEY="" \
    JNDI_FACTORY="com.sun.jndi.fscontext.RefFSContextFactory" \
    JNDI_PROVIDER_URL="" \
    SERVER_DISPLAY_NAME="modresorts" \
    SERVER_FULL_NAME="modresorts"

EXPOSE 9080

USER appuser

WORKDIR ${LIBERTY_HOME}

CMD ["bin/server", "run", "modresorts"]
