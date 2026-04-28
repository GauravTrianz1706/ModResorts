# Multi-stage Dockerfile for ModResorts Java WAR Application
# Builder stage: Build the WAR file using Maven
FROM maven:3.9.4-eclipse-temurin-8 AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR file
RUN mvn clean package -DskipTests -B

# Runtime stage: Deploy WAR to Tomcat with explicit base image
FROM amazoncorretto:8

# Install Tomcat 9
ENV CATALINA_HOME=/opt/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV TOMCAT_VERSION=9.0.82

RUN yum install -y tar gzip && \
    mkdir -p $CATALINA_HOME && \
    curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz | \
    tar -xz --strip-components=1 -C $CATALINA_HOME && \
    yum clean all && \
    rm -rf /var/cache/yum

# Create non-root user for security
RUN groupadd -r tomcat && useradd -r -g tomcat tomcat && \
    chown -R tomcat:tomcat $CATALINA_HOME && \
    chmod -R u+x $CATALINA_HOME/bin

# Copy WAR file from builder stage
COPY --from=builder --chown=tomcat:tomcat /workspace/target/*.war $CATALINA_HOME/webapps/ROOT.war

# Set working directory
WORKDIR $CATALINA_HOME

# Switch to non-root user
USER tomcat

# Expose application port
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check is handled by Kubernetes probes - no HEALTHCHECK instruction needed

# Start Tomcat
CMD ["catalina.sh", "run"]
