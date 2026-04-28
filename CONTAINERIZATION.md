# ModResorts - Containerization Configuration

## Overview
This application has been containerized and migrated from WebSphere to a container-native architecture suitable for AWS ECS/EKS deployment.

## Environment Variables

The following environment variables must be configured for the containerized application:

### Redis/ElastiCache Configuration (Required for Distributed Caching)
- `REDIS_HOST`: Amazon ElastiCache Redis endpoint (e.g., `my-cluster.abc123.0001.use1.cache.amazonaws.com`)
- `REDIS_PORT`: Redis port (default: `6379`)
- `REDIS_PASSWORD`: Redis authentication password (optional, if AUTH is enabled)

### Server Identification (Optional)
- `SERVER_NAME`: Server/service name for identification
- `POD_NAME`: Kubernetes pod name (auto-injected in EKS)
- `HOSTNAME`: Container hostname (auto-injected)

### Weather API Configuration (Optional)
- `WEATHER_API_KEY`: Weather Underground API key for real-time weather data

### Database Configuration (When enabled)
- `DB_HOST`: Database host endpoint
- `DB_PORT`: Database port
- `DB_NAME`: Database name
- `DB_USER`: Database username
- `DB_PASSWORD`: Database password

## Health Check Endpoints

The application exposes health check endpoints for container orchestration:

- `/health` - Primary health check endpoint
- `/actuator/health` - Spring Boot compatible health check endpoint

Both endpoints return:
- HTTP 200 with `{"status":"UP"}` when healthy
- HTTP 503 with `{"status":"DOWN"}` when unhealthy

## Containerization Changes

### Blockers Fixed

1. **WebSphere Specific Features (cz-java-0075)**
   - Replaced `WSSecurityHelper` with standard servlet session management
   - Replaced `ResponseUtils.encodeDataString()` with Spring's `HtmlUtils.htmlEscape()`
   - Removed WebSphere-specific server name APIs

2. **Singleton State Storage (cz-java-0064)**
   - Replaced `@Singleton` with Spring `@Component`
   - Added `@Cacheable` annotation for distributed caching
   - Configured Amazon ElastiCache (Redis) for state management

3. **RMI Resource Lookups (cz-java-0080)**
   - Removed RMI/IIOP InitialContext configuration
   - Removed CORBA-based naming service lookups
   - Ready for REST/HTTP-based service discovery

4. **Server Dependencies (cz-java-0081)**
   - Removed WebSphere `was_public` dependency
   - Added Spring Framework dependencies
   - Configured for embedded servlet container deployment

### Architecture Changes

- **From**: WebSphere Application Server with singleton state
- **To**: Container-native servlet application with distributed caching
- **Caching**: Amazon ElastiCache (Redis) via Spring Data Redis
- **Session Management**: Standard servlet API
- **Service Discovery**: Environment variable-based configuration

## Deployment Notes

### AWS ECS Deployment
```yaml
environment:
  - name: REDIS_HOST
    value: my-elasticache.abc123.0001.use1.cache.amazonaws.com
  - name: REDIS_PORT
    value: "6379"
  - name: SERVER_NAME
    value: modresorts-service
```

### AWS EKS Deployment
```yaml
env:
  - name: REDIS_HOST
    value: my-elasticache.abc123.0001.use1.cache.amazonaws.com
  - name: REDIS_PORT
    value: "6379"
  - name: POD_NAME
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
  - name: HOSTNAME
    valueFrom:
      fieldRef:
        fieldPath: spec.nodeName
```

### Health Check Configuration

**ECS Task Definition:**
```json
"healthCheck": {
  "command": ["CMD-SHELL", "curl -f http://localhost:8080/health || exit 1"],
  "interval": 30,
  "timeout": 5,
  "retries": 3,
  "startPeriod": 60
}
```

**EKS Deployment:**
```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

## Building and Running

### Build
```bash
mvn clean package
```

### Run Locally (with Docker)
```bash
docker build -t modresorts:2.0.0 .
docker run -p 8080:8080 \
  -e REDIS_HOST=localhost \
  -e REDIS_PORT=6379 \
  modresorts:2.0.0
```

## Migration Summary

- ✅ Removed WebSphere-specific APIs
- ✅ Implemented distributed caching with Redis
- ✅ Added health check endpoints
- ✅ Externalized configuration via environment variables
- ✅ Ready for horizontal scaling in container orchestration platforms
