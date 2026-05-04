# AWS Cloud Configuration Guide for ModResorts

## Overview
This application has been modernized for AWS cloud deployment with the following cloud-native patterns:

## AWS Services Used

### 1. Amazon S3 (Simple Storage Service)
- **Purpose**: Replaces local file system storage for persistent data
- **Configuration**:
  - Environment Variable: `S3_BUCKET_NAME` (default: `modresorts-data`)
  - Region: Configured via `AWS_REGION`
- **Usage**:
  - Configuration files (ops.json, reservations.json)
  - Exported reservation archives
  - Temporary file storage

### 2. AWS Secrets Manager
- **Purpose**: Secure storage for API keys and credentials
- **Configuration**:
  - Secret Name: `modresorts/weather-api-key`
  - Region: Configured via `AWS_REGION`
- **Usage**:
  - Weather API key storage
  - Database credentials (recommended)

### 3. Amazon ElastiCache for Redis
- **Purpose**: Distributed session management for horizontal scaling
- **Configuration**:
  - Environment Variables:
    - `REDIS_HOST`: Redis endpoint
    - `REDIS_PORT`: Redis port (default: 6379)
    - `REDIS_PASSWORD`: Redis authentication password
- **Usage**:
  - HTTP session state storage
  - Enables stateless application instances

### 4. Amazon RDS (Relational Database Service)
- **Purpose**: Managed PostgreSQL database with HikariCP connection pooling
- **Configuration**:
  - Environment Variables:
    - `DB_URL`: JDBC connection URL
    - `DB_USERNAME`: Database username
    - `DB_PASSWORD`: Database password
    - `DB_POOL_SIZE`: Maximum connection pool size (default: 10)

## Environment Variables

### Required
- `AWS_REGION`: AWS region (e.g., us-east-1, us-west-2)
- `S3_BUCKET_NAME`: S3 bucket for application data
- `REDIS_HOST`: ElastiCache Redis endpoint
- `DB_URL`: RDS database connection URL
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password

### Optional
- `SERVER_PORT`: Application port (default: 8080)
- `REDIS_PORT`: Redis port (default: 6379)
- `REDIS_PASSWORD`: Redis authentication password
- `DB_POOL_SIZE`: Connection pool size (default: 10)

## Deployment Options

### 1. Amazon ECS (Elastic Container Service)
- Package as Docker container
- Use ECS task definition with environment variables
- Configure IAM role for S3 and Secrets Manager access

### 2. Amazon EKS (Elastic Kubernetes Service)
- Deploy as Kubernetes pod
- Use ConfigMaps and Secrets for configuration
- Configure service account with IAM role

### 3. AWS Elastic Beanstalk
- Deploy as executable JAR
- Configure environment variables in Beanstalk console
- Attach IAM instance profile with required permissions

## IAM Permissions Required

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::modresorts-data",
        "arn:aws:s3:::modresorts-data/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:*:*:secret:modresorts/*"
      ]
    }
  ]
}
```

## Migration from WebSphere

### Changes Made
1. **EJB 2.x → Spring Components**: Replaced EJB annotations with Spring @Component
2. **WAR → Executable JAR**: Converted to Spring Boot with embedded Tomcat
3. **WebSphere Session → Spring Session**: Externalized session to Redis
4. **Local Files → S3**: Migrated file operations to cloud storage
5. **Hardcoded Secrets → Secrets Manager**: Externalized API keys
6. **java.util.Date → java.time API**: Modernized date handling with UTC
7. **Manual Resource Management → try-with-resources**: Prevented resource leaks

## Health Check Endpoint
- URL: `/actuator/health`
- Use for container health checks and load balancer configuration

## Logging
- Structured logging for CloudWatch integration
- UTC timestamps for consistency across regions
- Log level configurable via environment variable

## Next Steps
1. Create S3 bucket and upload configuration files
2. Store secrets in AWS Secrets Manager
3. Provision ElastiCache Redis cluster
4. Provision RDS PostgreSQL database
5. Configure IAM roles and policies
6. Deploy application to chosen AWS service
