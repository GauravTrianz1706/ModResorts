# ModResorts - AWS ECS Fargate Deployment Guide

## Overview

This guide covers the complete deployment of the **ModResorts** Java EE web application to **AWS ECS Fargate**. ModResorts is a Java 8 servlet-based web application packaged as a WAR file and deployed on Open Liberty application server.

- **Application**: ModResorts (modresorts.war)
- **Framework**: Java EE 7 / Servlet 3.1
- **Runtime**: Open Liberty
- **Context Root**: `/resorts`
- **Application Port**: `9080` (HTTP), `9443` (HTTPS)
- **Health Endpoint**: `GET /resorts/health`
- **Build Tool**: Maven
- **Java Version**: 8
- **Target Platform**: AWS ECS Fargate

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development with Docker Compose](#local-development-with-docker-compose)
3. [Build and Push Docker Image](#build-and-push-docker-image)
4. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
5. [ECS Task Definition Explained](#ecs-task-definition-explained)
6. [ECS Service Configuration](#ecs-service-configuration)
7. [ECS Fargate Deployment Walkthrough](#ecs-fargate-deployment-walkthrough)
8. [ECS-Specific Troubleshooting](#ecs-specific-troubleshooting)
9. [ECS Fargate Scaling and Management](#ecs-fargate-scaling-and-management)
10. [Configuration Management](#configuration-management)
11. [Security Considerations](#security-considerations)
12. [Java-Specific Notes](#java-specific-notes)

---

## Prerequisites

### Local Development Requirements

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20.10+ | Container build and run |
| Docker Compose | 2.0+ | Local multi-container orchestration |
| Java JDK | 8+ | Local development |
| Maven | 3.6+ | Build tool |
| AWS CLI | 2.x | AWS resource management |

### Install AWS CLI

```bash
# macOS
brew install awscli

# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip && sudo ./aws/install

# Windows
# Download from: https://aws.amazon.com/cli/
```

### Configure AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

---

## Local Development with Docker Compose

### Quick Start

```bash
# Clone and navigate to project root
cd "Component 1"

# Build and start the application
docker-compose up --build

# Access the application
open http://localhost:9080/resorts/

# Health check
curl http://localhost:9080/resorts/health

# Stop the application
docker-compose down
```

### Environment Variables for Local Development

Create a `.env` file in the project root (never commit this file):

```env
DB_HOST=your-database-host
DB_PORT=5432
DB_NAME=modresorts
DB_USER=modresorts
DB_PASSWORD=your-secure-password
WEATHER_API_KEY=your-weather-api-key
```

### View Application Logs

```bash
# Follow logs
docker-compose logs -f modresorts

# View last 100 lines
docker-compose logs --tail=100 modresorts
```

---

## Build and Push Docker Image

### Linux/macOS

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run build and push script
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

### Script Prompts

The script will interactively prompt for:

1. **Image tag** (default: `latest`)
2. **Registry type**:
   - `1` → AWS ECR
   - `2` → Docker Hub

**For AWS ECR:**
- AWS Region (e.g., `us-east-1`)
- ECR repository name (default: `modresorts`)
- The script auto-creates the ECR repository if it doesn't exist

**For Docker Hub:**
- Docker Hub username
- Docker Hub password/access token
- Docker Hub namespace/organization

### Manual Build

```bash
# Build image
docker build -t modresorts:latest .

# Tag for ECR
docker tag modresorts:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest

# Push to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. IAM Roles

#### ECS Task Execution Role

This role allows ECS to pull images from ECR and write logs to CloudWatch.

```bash
# Create the execution role
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach the managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### ECS Task Role (Optional)

This role grants the application container permissions to access AWS services.

```bash
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'
```

### 2. VPC and Networking

Fargate requires **awsvpc** network mode. You need:

- A VPC with at least 2 subnets in different Availability Zones
- A security group allowing inbound traffic on port `9080`

```bash
# Create security group
aws ec2 create-security-group \
  --group-name modresorts-sg \
  --description "ModResorts ECS Security Group" \
  --vpc-id vpc-xxxxxxxx

# Allow inbound HTTP on port 9080
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxx \
  --protocol tcp \
  --port 9080 \
  --cidr 0.0.0.0/0

# Allow inbound HTTPS on port 9443
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxx \
  --protocol tcp \
  --port 9443 \
  --cidr 0.0.0.0/0
```

### 3. CloudWatch Log Group

```bash
aws logs create-log-group \
  --log-group-name /ecs/modresorts \
  --region us-east-1

# Set retention policy (optional)
aws logs put-retention-policy \
  --log-group-name /ecs/modresorts \
  --retention-in-days 30
```

### 4. ECR Repository

```bash
aws ecr create-repository \
  --repository-name modresorts \
  --region us-east-1
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) configures how the container runs on Fargate.

### Key Configuration

```json
{
  "family": "modresorts-task",
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc",
  "cpu": "512",
  "memory": "1024"
}
```

### Valid Fargate CPU/Memory Combinations

| CPU (vCPU) | Valid Memory (MB) |
|------------|-------------------|
| 256 (.25)  | 512, 1024, 2048   |
| **512 (.5)** | **1024**, 2048, 3072, 4096 |
| 1024 (1)   | 2048–8192         |
| 2048 (2)   | 4096–16384        |
| 4096 (4)   | 8192–30720        |

> **ModResorts uses**: CPU `512`, Memory `1024` MB — suitable for a Java EE web application.

### Container Definition

- **Port**: `9080` (HTTP) — the Open Liberty HTTP port
- **Environment Variables**: JVM options, timezone, external service connections
- **Log Configuration**: CloudWatch Logs via `awslogs` driver

### JVM Memory Settings

```
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

- `-Xmx512m`: Maximum heap size (512 MB)
- `-Xms256m`: Initial heap size (256 MB)
- `-XX:+UseContainerSupport`: Enables JVM container awareness
- `-XX:MaxRAMPercentage=75.0`: Use up to 75% of container memory for JVM

---

## ECS Service Configuration

The service definition (`ecs/service-definition.json`) manages how tasks are scheduled and maintained.

### Key Settings

```json
{
  "serviceName": "modresorts-service",
  "launchType": "FARGATE",
  "desiredCount": 2,
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "assignPublicIp": "ENABLED"
    }
  }
}
```

- **desiredCount: 2** — Runs 2 task instances for high availability
- **assignPublicIp: ENABLED** — Required for tasks in public subnets to pull images from ECR
- **maximumPercent: 200** — Allows up to 4 tasks during rolling deployments
- **minimumHealthyPercent: 50** — Keeps at least 1 task running during deployments

---

## ECS Fargate Deployment Walkthrough

### Step 1: Build and Push Image

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
# Note the full image URI output (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest)
```

### Step 2: Run Deployment Script

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:
- AWS Region
- ECS Cluster name
- ECR Image URI (from Step 1)
- VPC ID
- Subnet IDs (comma-separated)
- Security Group ID
- Whether to create an Application Load Balancer

### Step 3: Verify Deployment

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

# View application logs
aws logs tail /ecs/modresorts --follow --region us-east-1
```

### Step 4: Access the Application

If using a Load Balancer:
```
http://<ALB-DNS-NAME>/resorts/
http://<ALB-DNS-NAME>/resorts/health
```

If accessing directly (public IP):
```
http://<TASK-PUBLIC-IP>:9080/resorts/
http://<TASK-PUBLIC-IP>:9080/resorts/health
```

### Step 5: Update Deployment

To deploy a new image version:

```bash
# Build and push new image with version tag
./scripts/build-push.sh
# Enter tag: v2.0.1

# Re-run deployment with new image URI
./scripts/deploy-image.sh
```

---

## ECS-Specific Troubleshooting

### Task Fails to Start

```bash
# Describe stopped tasks
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-arn> \
  --region us-east-1 \
  --query "tasks[0].{Status:lastStatus,StopCode:stopCode,StopReason:stoppedReason,Containers:containers[*].{Name:name,Reason:reason,ExitCode:exitCode}}"
```

**Common causes:**
- `CannotPullContainerError`: ECR permissions issue or image not found
  - Verify `ecsTaskExecutionRole` has ECR pull permissions
  - Confirm image URI is correct
- `OutOfMemoryError`: Increase task memory in task definition
- `PortBindingError`: Port conflict — ensure no other service uses port 9080

### Network Issues

```bash
# Check security group rules
aws ec2 describe-security-groups \
  --group-ids sg-xxxxxxxx \
  --query "SecurityGroups[0].IpPermissions"

# Verify subnets have internet access (for ECR pull)
aws ec2 describe-route-tables \
  --filters "Name=association.subnet-id,Values=subnet-xxxxxxxx"
```

**Common causes:**
- Tasks in private subnets without NAT Gateway cannot pull images
- Security group missing inbound rule on port 9080

### CPU/Memory Errors

```
InvalidParameterException: Invalid CPU or memory value specified
```

**Fix**: Use valid Fargate combinations. For ModResorts: `cpu: "512"`, `memory: "1024"`

### Service Not Stabilizing

```bash
# Check service events
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1 \
  --query "services[0].events[:10]"
```

### Application Logs

```bash
# Stream logs in real-time
aws logs tail /ecs/modresorts --follow --region us-east-1

# Filter for errors
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --filter-pattern "ERROR" \
  --region us-east-1
```

### Liberty Server Issues

```bash
# Check Liberty startup logs
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --filter-pattern "CWWKF" \
  --region us-east-1
```

---

## ECS Fargate Scaling and Management

### Manual Scaling

```bash
# Scale to 4 instances
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --desired-count 4 \
  --region us-east-1
```

### Auto Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# Create CPU-based scaling policy
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name modresorts-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }'
```

### Blue/Green Deployments

For zero-downtime deployments with CodeDeploy:

```bash
# Update service to use CODE_DEPLOY deployment controller
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --deployment-controller '{"type": "CODE_DEPLOY"}' \
  --region us-east-1
```

### Rolling Updates

The default deployment configuration supports rolling updates:
- `maximumPercent: 200` — Allows double capacity during deployment
- `minimumHealthyPercent: 50` — Keeps 50% healthy during rollout

---

## Configuration Management

### Environment Variables

Update environment variables in `ecs/task-definition.json` under `containerDefinitions[0].environment`:

```json
{
  "name": "DB_HOST",
  "value": "your-rds-endpoint.amazonaws.com"
}
```

### AWS Secrets Manager (Recommended for Secrets)

```bash
# Store database password as secret
aws secretsmanager create-secret \
  --name modresorts/db-password \
  --secret-string "your-secure-password"
```

Reference in task definition:
```json
"secrets": [
  {
    "name": "DB_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:modresorts/db-password"
  }
]
```

### Parameter Store

```bash
# Store configuration
aws ssm put-parameter \
  --name "/modresorts/weather-api-key" \
  --value "your-api-key" \
  --type SecureString
```

---

## Security Considerations

### Container Security

1. **Non-root user**: The container runs as `appuser` (non-root) for security
2. **Read-only filesystem**: Consider adding `"readonlyRootFilesystem": true` for immutable containers
3. **No privileged mode**: Never run containers with `"privileged": true`

### Network Security

1. **Security Groups**: Restrict inbound traffic to only required ports (9080, 9443)
2. **Private Subnets**: For production, place tasks in private subnets with NAT Gateway
3. **VPC Endpoints**: Use VPC endpoints for ECR and CloudWatch to avoid internet traffic

### IAM Security

1. **Least Privilege**: Grant only required permissions to task roles
2. **No hardcoded credentials**: Use IAM roles, Secrets Manager, or Parameter Store
3. **Rotate credentials**: Regularly rotate any API keys or passwords

### Image Security

```bash
# Scan ECR image for vulnerabilities
aws ecr start-image-scan \
  --repository-name modresorts \
  --image-id imageTag=latest \
  --region us-east-1

# View scan results
aws ecr describe-image-scan-findings \
  --repository-name modresorts \
  --image-id imageTag=latest \
  --region us-east-1
```

---

## Java-Specific Notes

### Open Liberty Configuration

The Liberty server configuration is in `docker/server.xml`. Key settings:

- **Features**: `servlet-3.1`, `jsp-2.3`, `jndi-1.0`
- **HTTP Port**: `9080`
- **Context Root**: `/resorts`
- **Session Timeout**: 30 minutes

### JVM Tuning for Containers

```
-Xmx512m              # Max heap: 512 MB (50% of 1024 MB container memory)
-Xms256m              # Initial heap: 256 MB
-XX:+UseContainerSupport    # JVM container awareness (Java 8u191+)
-XX:MaxRAMPercentage=75.0   # Use 75% of container RAM for JVM
```

### Java 8 Container Support

Java 8 update 191+ includes container support (`-XX:+UseContainerSupport`). This ensures the JVM correctly reads container memory limits rather than host memory.

### Garbage Collection

For Java 8 with 512 MB heap, the default G1GC is appropriate. For lower latency:

```
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
```

### Startup Time

Open Liberty with Java EE features typically starts in 5-15 seconds. The ECS health check grace period is set to accommodate this:
- `healthCheckGracePeriodSeconds: 300` (when using ALB)
- Docker Compose `start_period: 90s`

### Monitoring

Enable JMX for monitoring (add to JAVA_OPTS):
```
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

### Application Endpoints

| Endpoint | URL | Description |
|----------|-----|-------------|
| Home | `GET /resorts/` | Main application page |
| Health | `GET /resorts/health` | Health check (returns `{"status":"UP"}`) |
| Weather | `GET /resorts/weather` | Weather data |
| Availability | `GET /resorts/availability` | Resort availability |
| Welcome | `GET /resorts/welcome` | Welcome page |

---

## Quick Reference

```bash
# Local development
docker-compose up --build
curl http://localhost:9080/resorts/health

# Build and push
./scripts/build-push.sh

# Deploy to ECS
./scripts/deploy-image.sh

# View logs
aws logs tail /ecs/modresorts --follow --region us-east-1

# Scale service
aws ecs update-service --cluster modresorts-cluster --service modresorts-service --desired-count 3 --region us-east-1

# Stop service
aws ecs update-service --cluster modresorts-cluster --service modresorts-service --desired-count 0 --region us-east-1
```
