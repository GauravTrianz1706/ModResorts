# Multi-stage Dockerfile for ModResorts Java WAR Application
# Stage 1: Build stage using Maven and Amazon Corretto 8
FROM maven:3.9.4-amazoncorretto-8 AS builder

# Set working directory
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code and web content
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR file
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage using Amazon Corretto 8 (explicit base image)
FROM amazoncorretto:8

# Install Tomcat 9 (compatible with Java EE 7 and Servlet 3.1)
ENV TOMCAT_VERSION=9.0.82
ENV CATALINA_HOME=/opt/tomcat

# Create tomcat user for security
RUN yum install -y tar gzip && \
    groupadd -r tomcat && \
    useradd -r -g tomcat -d ${CATALINA_HOME} -s /sbin/nologin tomcat && \
    mkdir -p ${CATALINA_HOME}

# Download and install Tomcat
RUN curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz \
    | tar -xz --strip-components=1 -C ${CATALINA_HOME} && \
    rm -rf ${CATALINA_HOME}/webapps/* && \
    chown -R tomcat:tomcat ${CATALINA_HOME} && \
    chmod +x ${CATALINA_HOME}/bin/*.sh

# Copy WAR file from builder stage
COPY --from=builder /workspace/target/*.war ${CATALINA_HOME}/webapps/ROOT.war

# Set ownership
RUN chown tomcat:tomcat ${CATALINA_HOME}/webapps/ROOT.war

# Set environment variables for JVM tuning
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV CATALINA_OPTS="-Duser.timezone=UTC"

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER tomcat

# Set working directory
WORKDIR ${CATALINA_HOME}

# Health check using Tomcat manager (optional, prefer ECS service health checks)
# HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
#   CMD curl -f http://localhost:8080/health || exit 1

# Start Tomcat
CMD ["bin/catalina.sh", "run"]
