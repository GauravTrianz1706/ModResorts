# ModResorts Application - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development](#local-development)
5. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
6. [AWS ECS Fargate Deployment](#aws-ecs-fargate-deployment)
7. [Configuration Management](#configuration-management)
8. [Monitoring and Logging](#monitoring-and-logging)
9. [Troubleshooting](#troubleshooting)
10. [Security Considerations](#security-considerations)
11. [Scaling and Performance](#scaling-and-performance)

---

## Overview

ModResorts is a Java-based web application built with:
- **Java Version**: 8 (Amazon Corretto)
- **Build Tool**: Maven 3.9.4
- **Application Server**: Apache Tomcat 9.0.82
- **Framework**: Spring MVC 5.3.20
- **Package Type**: WAR (Web Application Archive)
- **Target Platform**: AWS ECS Fargate

This guide provides comprehensive instructions for containerizing and deploying the ModResorts application to AWS ECS Fargate.

---

## Prerequisites

### Required Software
- **Docker**: Version 20.10 or higher
  - [Install Docker Desktop](https://www.docker.com/products/docker-desktop)
- **AWS CLI**: Version 2.x
  - [Install AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
- **Git**: For version control
- **Maven**: 3.6+ (for local builds)
- **Java JDK**: 8 or higher (for local development)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with the following permissions:
  - ECS full access
  - ECR full access
  - CloudWatch Logs full access
  - VPC and networking permissions
  - IAM role creation (for ECS task execution)
  - Application Load Balancer permissions

### AWS Infrastructure Prerequisites
1. **VPC Configuration**
   - VPC with at least 2 subnets in different availability zones
   - Internet Gateway attached to VPC
   - Route tables configured for internet access

2. **Security Groups**
   - Security group allowing inbound traffic on port 8080 (application)
   - Security group allowing inbound traffic on port 80 (load balancer)
   - Outbound rules allowing all traffic

3. **IAM Roles**
   - **ecsTaskExecutionRole**: Allows ECS to pull images and write logs
   - **ecsTaskRole**: Allows tasks to access AWS services (optional)

---

## Project Structure

```
AppTesTMR/
├── Dockerfile                      # Multi-stage Docker build file
├── docker-compose.yml              # Local development orchestration
├── .dockerignore                   # Files to exclude from Docker context
├── pom.xml                         # Maven project configuration
├── src/                            # Java source code
│   └── main/
│       ├── java/                   # Application code
│       └── resources/              # Configuration files
├── WebContent/                     # Web resources (JSP, HTML, CSS)
├── scripts/                        # Build and deployment scripts
│   ├── build-push.sh              # Linux/Mac build and push script
│   ├── build-push.bat             # Windows build and push script
│   ├── deploy-image.sh            # Linux/Mac ECS deployment script
│   └── deploy-image.bat           # Windows ECS deployment script
├── ecs/                           # ECS configuration files
│   ├── task-definition.json       # ECS task definition
│   └── service-definition.json    # ECS service definition
└── docs/
    └── DEPLOYMENT.md              # This file
```

---

## Local Development

### Building Locally with Maven

```bash
# Navigate to project directory
cd AppTesTMR

# Clean and build the WAR file
mvn clean package

# The WAR file will be created at: target/modresorts-2.0.0.war
```

### Running with Docker Compose

```bash
# Build and start the application
docker-compose up --build

# Access the application
# Application: http://localhost:8080
# Health Check: http://localhost:8080/health

# Stop the application
docker-compose down
```

### Testing the Application

```bash
# Health check endpoint
curl http://localhost:8080/health

# Expected response:
# {"status":"UP","application":"ModResorts","version":"2.0.0"}

# Test welcome endpoint
curl http://localhost:8080/welcome

# Test weather endpoint
curl http://localhost:8080/weather
```

---

## Building and Pushing Docker Images

### Option 1: Using AWS ECR (Recommended for ECS)

#### Linux/macOS

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run the build and push script
./scripts/build-push.sh

# Follow the prompts:
# 1. Select registry type: 1 (AWS ECR)
# 2. Enter AWS Region: us-east-1
# 3. Enter AWS Account ID: 123456789012
# 4. Enter ECR Repository Name: modresorts
# 5. Enter image tag: latest (or version number)
```

#### Windows

```cmd
# Run the build and push script
scripts\build-push.bat

# Follow the same prompts as above
```

### Option 2: Using Docker Hub

```bash
# Run the build and push script
./scripts/build-push.sh  # Linux/macOS
# OR
scripts\build-push.bat   # Windows

# Follow the prompts:
# 1. Select registry type: 2 (Docker Hub)
# 2. Enter Docker Hub username
# 3. Enter Docker Hub password or access token
# 4. Enter image tag
```

### Manual Build and Push

```bash
# Build the Docker image
docker build -t modresorts:latest .

# Tag for ECR
docker tag modresorts:latest 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com

# Push to ECR
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

---

## AWS ECS Fargate Deployment

### Step 1: Configure AWS CLI

```bash
# Configure AWS credentials
aws configure

# Enter:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region (e.g., us-east-1)
# - Default output format (json)

# Verify configuration
aws sts get-caller-identity
```

### Step 2: Create Required IAM Roles

#### Create ECS Task Execution Role

```bash
# Create trust policy file
cat > ecs-task-execution-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create the role
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach AWS managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### Create ECS Task Role (Optional)

```bash
# Create the role
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach policies as needed for your application
# Example: S3 access, DynamoDB access, etc.
```

### Step 3: Set Up VPC and Networking

```bash
# Create VPC (if not exists)
aws ec2 create-vpc --cidr-block 10.0.0.0/16

# Create subnets in different availability zones
aws ec2 create-subnet --vpc-id vpc-xxxxx --cidr-block 10.0.1.0/24 --availability-zone us-east-1a
aws ec2 create-subnet --vpc-id vpc-xxxxx --cidr-block 10.0.2.0/24 --availability-zone us-east-1b

# Create Internet Gateway
aws ec2 create-internet-gateway

# Attach Internet Gateway to VPC
aws ec2 attach-internet-gateway --vpc-id vpc-xxxxx --internet-gateway-id igw-xxxxx

# Create security group
aws ec2 create-security-group \
  --group-name modresorts-sg \
  --description "Security group for ModResorts application" \
  --vpc-id vpc-xxxxx

# Add inbound rules
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0
```

### Step 4: Deploy to ECS Fargate

#### Linux/macOS

```bash
# Make script executable
chmod +x scripts/deploy-image.sh

# Run the deployment script
./scripts/deploy-image.sh

# Follow the prompts:
# 1. Enter AWS Region: us-east-1
# 2. Enter ECS Cluster Name: modresorts-cluster
# 3. Enter VPC ID: vpc-xxxxx
# 4. Enter Subnet IDs: subnet-xxxxx,subnet-yyyyy
# 5. Enter Security Group ID: sg-xxxxx
# 6. Enter Docker Image URI: 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
# 7. Need load balancer? y
```

#### Windows

```cmd
# Run the deployment script
scripts\deploy-image.bat

# Follow the same prompts as above
```

### Step 5: Verify Deployment

```bash
# Check service status
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1

# List running tasks
aws ecs list-tasks \
  --cluster modresorts-cluster \
  --service-name modresorts-service \
  --region us-east-1

# Get task details
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-arn> \
  --region us-east-1
```

### Step 6: Access the Application

```bash
# Get load balancer DNS name
aws elbv2 describe-load-balancers \
  --names modresorts-alb \
  --region us-east-1 \
  --query 'LoadBalancers[0].DNSName' \
  --output text

# Access the application
# http://<load-balancer-dns>
# http://<load-balancer-dns>/health
```

---

## Configuration Management

### Environment Variables

The application supports the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m -XX:+UseContainerSupport` |
| `CATALINA_OPTS` | Tomcat options | `-Duser.timezone=UTC` |
| `APP_ENV` | Application environment | `production` |
| `LOG_LEVEL` | Logging level | `INFO` |

### Updating Task Definition

```bash
# Edit ecs/task-definition.json
# Update environment variables, CPU, memory, etc.

# Register new task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1

# Update service with new task definition
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --task-definition modresorts-task:2 \
  --region us-east-1
```

### Valid Fargate CPU/Memory Combinations

| CPU (vCPU) | Memory (MB) |
|------------|-------------|
| 256 (.25)  | 512, 1024, 2048 |
| 512 (.5)   | 1024, 2048, 3072, 4096 |
| 1024 (1)   | 2048, 3072, 4096, 5120, 6144, 7168, 8192 |
| 2048 (2)   | 4096-16384 (increments of 1024) |
| 4096 (4)   | 8192-30720 (increments of 1024) |

---

## Monitoring and Logging

### CloudWatch Logs

```bash
# View logs in real-time
aws logs tail /ecs/modresorts --follow --region us-east-1

# View logs for specific time range
aws logs tail /ecs/modresorts \
  --since 1h \
  --region us-east-1

# Search logs
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --filter-pattern "ERROR" \
  --region us-east-1
```

### CloudWatch Metrics

Key metrics to monitor:
- **CPUUtilization**: CPU usage percentage
- **MemoryUtilization**: Memory usage percentage
- **TargetResponseTime**: Application response time
- **HealthyHostCount**: Number of healthy targets
- **UnHealthyHostCount**: Number of unhealthy targets

### Setting Up CloudWatch Alarms

```bash
# CPU utilization alarm
aws cloudwatch put-metric-alarm \
  --alarm-name modresorts-high-cpu \
  --alarm-description "Alert when CPU exceeds 80%" \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=modresorts-service Name=ClusterName,Value=modresorts-cluster

# Memory utilization alarm
aws cloudwatch put-metric-alarm \
  --alarm-name modresorts-high-memory \
  --alarm-description "Alert when memory exceeds 80%" \
  --metric-name MemoryUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=modresorts-service Name=ClusterName,Value=modresorts-cluster
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Task Fails to Start

**Symptoms**: Tasks transition from PENDING to STOPPED immediately

**Possible Causes**:
- Invalid CPU/memory combination
- Image pull errors
- Insufficient IAM permissions
- Network configuration issues

**Solutions**:
```bash
# Check stopped task reason
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-arn> \
  --region us-east-1 \
  --query 'tasks[0].stoppedReason'

# Check CloudWatch logs for errors
aws logs tail /ecs/modresorts --since 30m --region us-east-1

# Verify IAM role permissions
aws iam get-role --role-name ecsTaskExecutionRole

# Verify ECR image exists
aws ecr describe-images \
  --repository-name modresorts \
  --region us-east-1
```

#### 2. Health Check Failures

**Symptoms**: Tasks marked as unhealthy, frequent restarts

**Solutions**:
```bash
# Check health check configuration in target group
aws elbv2 describe-target-health \
  --target-group-arn <target-group-arn> \
  --region us-east-1

# Test health endpoint directly
curl http://<task-ip>:8080/health

# Increase health check grace period
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --health-check-grace-period-seconds 300 \
  --region us-east-1
```

#### 3. Application Not Accessible

**Symptoms**: Cannot access application through load balancer

**Solutions**:
```bash
# Verify security group rules
aws ec2 describe-security-groups \
  --group-ids sg-xxxxx \
  --region us-east-1

# Check target group health
aws elbv2 describe-target-health \
  --target-group-arn <target-group-arn> \
  --region us-east-1

# Verify load balancer configuration
aws elbv2 describe-load-balancers \
  --names modresorts-alb \
  --region us-east-1

# Check listener rules
aws elbv2 describe-listeners \
  --load-balancer-arn <alb-arn> \
  --region us-east-1
```

#### 4. Out of Memory Errors

**Symptoms**: Tasks crash with OOM errors in logs

**Solutions**:
```bash
# Increase task memory in task definition
# Edit ecs/task-definition.json
# Change "memory": "1024" to "memory": "2048"

# Adjust JVM heap size
# Update JAVA_OPTS environment variable:
# -Xmx1536m -Xms768m

# Register and deploy new task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1

aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --task-definition modresorts-task:3 \
  --force-new-deployment \
  --region us-east-1
```

#### 5. Slow Application Startup

**Symptoms**: Tasks take long time to become healthy

**Solutions**:
- Increase `startPeriod` in health check configuration
- Optimize application startup time
- Use Spring Boot lazy initialization
- Increase health check grace period

```bash
# Update service with longer grace period
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --health-check-grace-period-seconds 600 \
  --region us-east-1
```

---

## Security Considerations

### 1. Container Security

- **Non-root User**: Application runs as `tomcat` user (UID 1000)
- **Read-only Filesystem**: Consider mounting volumes as read-only
- **Minimal Base Image**: Uses Amazon Corretto 8 (official AWS image)
- **No Unnecessary Tools**: Runtime image doesn't include curl, wget, etc.

### 2. Network Security

- **Security Groups**: Restrict inbound traffic to necessary ports only
- **Private Subnets**: Consider deploying tasks in private subnets with NAT Gateway
- **VPC Endpoints**: Use VPC endpoints for AWS services (ECR, CloudWatch, etc.)

```bash
# Create VPC endpoint for ECR
aws ec2 create-vpc-endpoint \
  --vpc-id vpc-xxxxx \
  --service-name com.amazonaws.us-east-1.ecr.dkr \
  --route-table-ids rtb-xxxxx \
  --region us-east-1

# Create VPC endpoint for CloudWatch Logs
aws ec2 create-vpc-endpoint \
  --vpc-id vpc-xxxxx \
  --service-name com.amazonaws.us-east-1.logs \
  --route-table-ids rtb-xxxxx \
  --region us-east-1
```

### 3. Secrets Management

Use AWS Secrets Manager or Parameter Store for sensitive data:

```bash
# Store database password in Secrets Manager
aws secretsmanager create-secret \
  --name modresorts/db/password \
  --secret-string "your-secure-password" \
  --region us-east-1

# Reference in task definition
# "secrets": [
#   {
#     "name": "DB_PASSWORD",
#     "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:modresorts/db/password"
#   }
# ]
```

### 4. IAM Best Practices

- Use least privilege principle for IAM roles
- Separate execution role from task role
- Enable CloudTrail for audit logging
- Rotate credentials regularly

### 5. Image Security

```bash
# Scan Docker image for vulnerabilities
docker scan modresorts:latest

# Enable ECR image scanning
aws ecr put-image-scanning-configuration \
  --repository-name modresorts \
  --image-scanning-configuration scanOnPush=true \
  --region us-east-1

# View scan results
aws ecr describe-image-scan-findings \
  --repository-name modresorts \
  --image-id imageTag=latest \
  --region us-east-1
```

---

## Scaling and Performance

### Auto Scaling Configuration

#### Target Tracking Scaling

```bash
# Create scaling policy for CPU utilization
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10 \
  --region us-east-1

aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name modresorts-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-policy.json \
  --region us-east-1
```

**scaling-policy.json**:
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleInCooldown": 300,
  "ScaleOutCooldown": 60
}
```

### Performance Tuning

#### JVM Tuning

Optimize JVM settings in task definition:

```json
{
  "name": "JAVA_OPTS",
  "value": "-Xmx768m -Xms384m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"
}
```

#### Tomcat Tuning

```json
{
  "name": "CATALINA_OPTS",
  "value": "-Duser.timezone=UTC -Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true -Dorg.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH=true"
}
```

### Blue/Green Deployment

```bash
# Create new task definition version
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1

# Update service with deployment configuration
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --task-definition modresorts-task:4 \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=100" \
  --region us-east-1
```

### Rolling Updates

```bash
# Force new deployment with current task definition
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --force-new-deployment \
  --region us-east-1
```

---

## Additional Resources

### AWS Documentation
- [Amazon ECS Developer Guide](https://docs.aws.amazon.com/ecs/)
- [AWS Fargate User Guide](https://docs.aws.amazon.com/AmazonECS/latest/userguide/what-is-fargate.html)
- [Amazon ECR User Guide](https://docs.aws.amazon.com/ecr/)
- [CloudWatch Logs User Guide](https://docs.aws.amazon.com/cloudwatch/)

### Java and Tomcat
- [Apache Tomcat Documentation](https://tomcat.apache.org/tomcat-9.0-doc/)
- [Spring Framework Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/)
- [Java Performance Tuning](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)

### Docker Best Practices
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Multi-stage Builds](https://docs.docker.com/develop/develop-images/multistage-build/)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update Base Images**: Regularly update to latest Amazon Corretto and Tomcat versions
2. **Security Patches**: Apply security patches promptly
3. **Log Rotation**: Configure CloudWatch log retention policies
4. **Cost Optimization**: Review and optimize resource allocation
5. **Backup Strategy**: Implement backup for configuration and data

### Monitoring Checklist

- [ ] CloudWatch alarms configured
- [ ] Log aggregation working
- [ ] Health checks passing
- [ ] Auto-scaling policies tested
- [ ] Backup and recovery tested
- [ ] Security scanning enabled
- [ ] Cost monitoring active

---

## Conclusion

This deployment guide provides comprehensive instructions for containerizing and deploying the ModResorts application to AWS ECS Fargate. Follow the steps carefully, and refer to the troubleshooting section for common issues.

For questions or issues, consult the AWS documentation or contact your DevOps team.

**Happy Deploying! 🚀**
