# Multi-stage build for ModResorts Java WAR application
# Stage 1: Build the application
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR file
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM openjdk:8-jdk

# Install Tomcat
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH
ENV TOMCAT_VERSION 9.0.80

RUN mkdir -p "$CATALINA_HOME" && \
    apt-get update && \
    apt-get install -y wget && \
    wget -O /tmp/tomcat.tar.gz https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz && \
    tar -xzf /tmp/tomcat.tar.gz -C /tmp && \
    mv /tmp/apache-tomcat-${TOMCAT_VERSION}/* $CATALINA_HOME && \
    rm -rf /tmp/tomcat.tar.gz /tmp/apache-tomcat-${TOMCAT_VERSION} && \
    rm -rf $CATALINA_HOME/webapps/* && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r tomcat && useradd -r -g tomcat tomcat && \
    chown -R tomcat:tomcat $CATALINA_HOME

# Copy WAR file from builder
COPY --from=builder /workspace/target/*.war $CATALINA_HOME/webapps/ROOT.war

# Set ownership
RUN chown -R tomcat:tomcat $CATALINA_HOME/webapps

# Switch to non-root user
USER tomcat

# Expose application port
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Set timezone
ENV TZ=UTC

# Start Tomcat
CMD ["catalina.sh", "run"]
