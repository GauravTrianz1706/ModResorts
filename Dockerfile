# Multi-stage Dockerfile for ModResorts Java WAR Application
# Stage 1: Build stage using Maven and Eclipse Temurin JDK 8
FROM maven:3.9.4-eclipse-temurin-8 AS builder

# Set working directory
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy the entire project source code
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR file
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage using Tomcat with Eclipse Temurin JRE 8
FROM eclipse-temurin:8-jdk

# Install Tomcat 9
ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV TOMCAT_VERSION=9.0.89

RUN mkdir -p "$CATALINA_HOME" && \
    apt-get update && \
    apt-get install -y wget && \
    wget -O /tmp/tomcat.tar.gz "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" && \
    tar -xzf /tmp/tomcat.tar.gz -C "$CATALINA_HOME" --strip-components=1 && \
    rm /tmp/tomcat.tar.gz && \
    rm -rf "$CATALINA_HOME/webapps/*" && \
    apt-get remove -y wget && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r tomcat && useradd -r -g tomcat tomcat && \
    chown -R tomcat:tomcat "$CATALINA_HOME"

# Copy WAR file from builder stage
COPY --from=builder --chown=tomcat:tomcat /workspace/target/*.war $CATALINA_HOME/webapps/ROOT.war

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER tomcat

# Set working directory
WORKDIR $CATALINA_HOME

# Start Tomcat
CMD ["catalina.sh", "run"]
