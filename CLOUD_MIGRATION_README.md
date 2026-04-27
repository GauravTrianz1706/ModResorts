# ModResorts - Cloud-Native Migration

## Overview
This application has been migrated from a traditional Java EE WAR deployment to a cloud-native Spring Boot application optimized for Google Cloud Platform (GCP).

## Cloud Readiness Fixes Applied

### 1. File System & Storage (Critical)
- **Replaced local file system operations with Google Cloud Storage (GCS)**
  - `IOUtils.java`: Now uses GCS SDK to read/write files instead of local file system
  - `AvailabilityCheckerServlet.java`: Exports to GCS instead of local `/tmp` directory
  - Configuration files loaded from GCS with classpath fallback

### 2. Secret Management (Critical)
- **Migrated to Google Secret Manager**
  - `WeatherServlet.java`: API keys retrieved from Secret Manager instead of environment variables
  - Centralized secret lifecycle management with access control and audit logging

### 3. Database Connectivity (High)
- **Migrated from EJB 2.x to Spring Boot with HikariCP**
  - `ModResortsCustomerInformation.java`: Converted from EJB Singleton to Spring Service
  - HikariCP connection pooling for Cloud SQL connectivity
  - Try-with-resources for automatic connection management (prevents resource leaks)

### 4. Session Management (High)
- **Externalized session state to GCP Memorystore for Redis**
  - `LogoutServlet.java`: Removed WebSphere-specific clustering dependencies
  - Spring Session Data Redis for distributed session management
  - Enables horizontal scaling without session affinity

### 5. Time Dependencies (High)
- **Standardized on UTC timestamps**
  - `DateChecker.java`: Uses `ZonedDateTime` with UTC timezone
  - `ReservationCheckerData.java`: Parses dates in UTC
  - `AvailabilityCheckerServlet.java`: Time comparisons use UTC
  - Replaced `java.util.Timer` with `ScheduledExecutorService`

### 6. Packaging (Low)
- **Converted from WAR to executable JAR**
  - `pom.xml`: Changed packaging from `war` to `jar`
  - Added Spring Boot Maven Plugin for executable JAR generation
  - Embedded Tomcat eliminates need for external application server

### 7. Resource Management
- **Implemented try-with-resources throughout**
  - Automatic closure of database connections, file streams, HTTP connections
  - Prevents resource leaks in containerized environments

### 8. Middleware Dependencies
- **Removed WebSphere-specific dependencies**
  - `LogoutServlet.java`: Replaced `WSSecurityHelper` with standard servlet session
  - `UpperServlet.java`: Replaced `ResponseUtils` with standard `URLEncoder`
  - `WeatherServlet.java`: Removed WebSphere naming context initialization

## Environment Variables

### Required
- `GCP_PROJECT_ID`: Google Cloud project ID
- `DB_URL`: Cloud SQL connection string (e.g., `jdbc:postgresql://localhost:5432/modresorts`)
- `DB_USER`: Database username
- `DB_PASSWORD`: Database password
- `REDIS_HOST`: Memorystore Redis host
- `REDIS_PORT`: Memorystore Redis port (default: 6379)

### Optional
- `PORT`: Application port (default: 8080)
- `GCS_BUCKET_NAME`: GCS bucket for configuration files (default: `modresorts-config`)
- `WEATHER_API_KEY_SECRET_NAME`: Secret Manager secret name (default: `weather-api-key`)
- `WEATHER_API_KEY_SECRET_VERSION`: Secret version (default: `latest`)
- `REDIS_PASSWORD`: Redis password (if authentication enabled)

## Building the Application

```bash
mvn clean package
```

This generates an executable JAR: `target/modresorts-2.0.0.jar`

## Running Locally

```bash
export GCP_PROJECT_ID=your-project-id
export DB_URL=jdbc:postgresql://localhost:5432/modresorts
export DB_USER=postgres
export DB_PASSWORD=your-password
export REDIS_HOST=localhost
export REDIS_PORT=6379

java -jar target/modresorts-2.0.0.jar
```

## Deploying to GCP

### Google Kubernetes Engine (GKE)
1. Build container image
2. Push to Google Container Registry
3. Deploy to GKE with ConfigMap/Secret for environment variables

### Cloud Run
1. Build container image
2. Deploy to Cloud Run with environment variables

### App Engine Flexible
1. Create `app.yaml` with environment variables
2. Deploy with `gcloud app deploy`

## GCP Services Used

- **Cloud Storage**: Configuration file storage
- **Secret Manager**: API key and credential management
- **Cloud SQL**: PostgreSQL database with HikariCP connection pooling
- **Memorystore for Redis**: Distributed session management
- **Cloud Pub/Sub**: (Ready for async messaging if needed)
- **Cloud Scheduler**: (Ready for scheduled tasks if needed)

## Architecture Changes

### Before (Java EE)
- WAR deployment to WebSphere Application Server
- Local file system for configuration
- EJB 2.x for business logic
- WebSphere clustering for session management
- Hardcoded credentials in code/properties

### After (Cloud-Native)
- Executable JAR with embedded Tomcat
- Google Cloud Storage for configuration
- Spring Boot services with dependency injection
- Redis-based distributed session management
- Secret Manager for credential management
- UTC timestamps for timezone consistency
- HikariCP connection pooling for database

## Health Checks

Spring Boot Actuator endpoints available:
- `/actuator/health` - Application health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

## Monitoring & Logging

- Structured logging compatible with Cloud Logging
- Metrics exposed via Spring Boot Actuator
- Ready for Cloud Monitoring integration

## Next Steps

1. Configure Cloud SQL instance and connection
2. Set up Memorystore for Redis instance
3. Create GCS bucket for configuration files
4. Store API keys in Secret Manager
5. Configure IAM roles for service account
6. Deploy to target GCP environment
