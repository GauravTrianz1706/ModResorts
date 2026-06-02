# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /workspace

# Copy Maven build descriptor first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy the full project source
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR artifact (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM openjdk:8-jdk

LABEL maintainer="ModResorts Team" \
      application="modresorts" \
      version="2.0.0"

# Set environment variables
ENV TZ=UTC \
    LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    CATALINA_HOME=/opt/tomcat \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions"

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser -d /opt/tomcat -s /sbin/nologin appuser

# Install Tomcat 9 (supports Servlet 4.0 / Java EE 8)
ARG TOMCAT_VERSION=9.0.85
RUN apt-get update && apt-get install -y --no-install-recommends \
        tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/tomcat \
    && cd /tmp \
    && apt-get clean

# Download and install Tomcat
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && wget -q "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" -O /tmp/tomcat.tar.gz \
    && tar -xzf /tmp/tomcat.tar.gz -C /opt/tomcat --strip-components=1 \
    && rm /tmp/tomcat.tar.gz \
    && apt-get remove -y wget \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf /opt/tomcat/webapps/ROOT \
    && rm -rf /opt/tomcat/webapps/examples \
    && rm -rf /opt/tomcat/webapps/docs \
    && rm -rf /opt/tomcat/webapps/host-manager \
    && rm -rf /opt/tomcat/webapps/manager

# Copy the WAR from builder stage
COPY --from=builder /workspace/target/modresorts-2.0.0.war /opt/tomcat/webapps/resorts.war

# Set ownership
RUN chown -R appuser:appuser /opt/tomcat

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Start Tomcat
CMD ["/opt/tomcat/bin/catalina.sh", "run"]
