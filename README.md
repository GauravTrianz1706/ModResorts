# ModResorts - Containerized Application

## Overview
ModResorts application has been migrated from WebSphere to Spring Boot for containerized deployment on AWS ECS/EKS.

## Containerization Blockers Fixed

### Critical Blockers (WebSphere Specific Features)
1. **blocker-1**: Replaced `WSSecurityHelper.revokeSSOCookies` with standard servlet session invalidation
2. **blocker-2**: Replaced `ResponseUtils.encodeDataString` with Spring's `HtmlUtils.htmlEscape`
3. **blocker-3**: Replaced WebSphere `ServerName` APIs with environment variables

### High Priority Blockers
4. **blocker-4**: Migrated singleton state storage to Spring Cache with Redis (Amazon ElastiCache)
5. **blocker-5**: Replaced RMI/IIOP JNDI lookups with REST-based service discovery
6. **blocker-6-10**: Removed all WebSphere server-specific dependencies

## Architecture Changes

### From WebSphere to Spring Boot
- **Application Server**: WebSphere → Spring Boot embedded Tomcat
- **Dependency Injection**: EJB @Singleton → Spring @Service
- **Caching**: In-memory singleton → Distributed Redis cache
- **Service Discovery**: RMI/IIOP → REST with AWS Cloud Map
- **Metrics**: WebSphere PMI → Micrometer with Prometheus
- **Health Checks**: Custom → Spring Boot Actuator

## Configuration

### Environment Variables
All configuration is externalized via environment variables:

#### Database Configuration
- `DB_URL`: Database JDBC URL
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password
- `DB_DRIVER`: JDBC driver class name

#### Redis Configuration (Amazon ElastiCache)
- `REDIS_HOST`: Redis host address
- `REDIS_PORT`: Redis port (default: 6379)
- `REDIS_PASSWORD`: Redis password

#### Service Discovery (AWS Cloud Map)
- `SERVICE_REGISTRY_URL`: Service registry endpoint
- `AWS_CLOUDMAP_NAMESPACE`: AWS Cloud Map namespace

#### Server Configuration
- `SERVER_PORT`: Application port (default: 8080)
- `SERVER_NAME`: Server display name
- `SERVER_FULL_NAME`: Server full name

#### Weather API
- `WEATHER_API_KEY`: Weather Underground API key

## Building and Running

### Local Development
```bash
# Build the application
mvn clean package

# Run locally
java -jar target/modresorts-2.0.0.jar

# Or use Maven
mvn spring-boot:run
```

### Docker Build
```bash
# Build Docker image
docker build -t modresorts:2.0.0 .

# Run container
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db-host:5432/modresorts \
  -e DB_USERNAME=dbuser \
  -e DB_PASSWORD=dbpass \
  -e REDIS_HOST=redis-host \
  -e REDIS_PORT=6379 \
  modresorts:2.0.0
```

### AWS ECS Deployment
```bash
# Tag for ECR
docker tag modresorts:2.0.0 <account-id>.dkr.ecr.<region>.amazonaws.com/modresorts:2.0.0

# Push to ECR
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/modresorts:2.0.0

# Deploy to ECS using task definition with environment variables
```

## Health Check Endpoint

The application includes Spring Boot Actuator for health monitoring:

- **Health Check**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **Prometheus**: `GET /actuator/prometheus`

Example health check response:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

## AWS Infrastructure Requirements

### Amazon ElastiCache (Redis)
- Create Redis cluster for distributed caching
- Configure security groups for container access
- Set REDIS_HOST and REDIS_PORT environment variables

### AWS Cloud Map (Optional)
- Create service discovery namespace
- Register services for REST-based discovery
- Set AWS_CLOUDMAP_NAMESPACE environment variable

### Amazon RDS (Database)
- Create RDS instance (PostgreSQL/MySQL)
- Configure security groups
- Set DB_URL, DB_USERNAME, DB_PASSWORD environment variables

## Migration Notes

### Removed Dependencies
- `com.ibm.websphere.appserver:was_public` - WebSphere APIs
- `javax:javaee-api` (provided scope) - Replaced with Spring Boot starters

### Added Dependencies
- `spring-boot-starter-web` - Web framework
- `spring-boot-starter-actuator` - Health checks and metrics
- `spring-boot-starter-data-redis` - Distributed caching
- `spring-boot-starter-cache` - Cache abstraction
- `micrometer-registry-prometheus` - Metrics export

### Code Changes
- **LogoutServlet.java**: Standard session invalidation
- **UpperServlet.java**: Standard HTML encoding
- **WeatherServlet.java**: Environment-based configuration, REST service discovery
- **ModResortsCustomerInformation.java**: Spring Service with distributed caching

## Testing

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Application Endpoints
```bash
# Logout
curl http://localhost:8080/logout

# Upper case conversion
curl http://localhost:8080/resorts/upper?input=hello

# Weather information
curl http://localhost:8080/resorts/weather?selectedCity=Paris
```

## Monitoring

### Prometheus Metrics
Metrics are exposed at `/actuator/prometheus` for Prometheus scraping.

### CloudWatch Integration
Configure CloudWatch agent to collect logs and metrics from containers.

## Troubleshooting

### Redis Connection Issues
- Verify REDIS_HOST and REDIS_PORT are correct
- Check security group rules allow container access
- Verify Redis cluster is running

### Database Connection Issues
- Verify DB_URL, DB_USERNAME, DB_PASSWORD are correct
- Check security group rules
- Verify RDS instance is accessible from container

### Service Discovery Issues
- Verify SERVICE_REGISTRY_URL is accessible
- Check AWS Cloud Map namespace configuration
- Verify IAM roles have necessary permissions
