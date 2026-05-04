# ModResorts - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Docker Containerization](#docker-containerization)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Fargate Setup](#ecs-fargate-setup)
7. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
8. [ECS Task Definition Explained](#ecs-task-definition-explained)
9. [ECS Service Configuration](#ecs-service-configuration)
10. [Deployment Walkthrough](#deployment-walkthrough)
11. [Monitoring and Logging](#monitoring-and-logging)
12. [Troubleshooting](#troubleshooting)
13. [Scaling and Management](#scaling-and-management)
14. [Security Considerations](#security-considerations)

---

## Overview

ModResorts is a Java web application (WAR) that has been containerized for deployment on AWS ECS Fargate. This guide provides comprehensive instructions for building, deploying, and managing the application in a production environment.

**Application Details:**
- **Name:** ModResorts
- **Version:** 2.0.0
- **Technology:** Java 8, Maven, Servlet API
- **Package Type:** WAR (deployed on Tomcat 9)
- **Application Port:** 8080
- **Health Check Endpoint:** `/health` or `/actuator/health`

---

## Prerequisites

### Required Software
- **Docker:** Version 20.10 or higher
- **Docker Compose:** Version 1.29 or higher (for local development)
- **AWS CLI:** Version 2.x
- **Java:** JDK 8 or higher (for local development)
- **Maven:** Version 3.6 or higher (for local builds)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECS (create/update clusters, services, task definitions)
  - ECR (create repositories, push images)
  - CloudWatch Logs (create log groups)
  - IAM (create/manage roles)
  - VPC (manage networking resources)
  - Elastic Load Balancing (create/manage ALBs)

### AWS CLI Configuration
```bash
# Configure AWS CLI with your credentials
aws configure

# Verify configuration
aws sts get-caller-identity
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd ResortsComp
```

### 2. Build the Application Locally
```bash
# Using Maven
mvn clean package

# The WAR file will be generated in target/modresorts-2.0.0.war
```

### 3. Run with Docker Compose
```bash
# Build and start the application
docker-compose up --build

# Access the application
# http://localhost:8080

# Stop the application
docker-compose down
```

### 4. Test Health Endpoint
```bash
curl http://localhost:8080/health
# Expected response: {"status":"UP","application":"ModResorts","version":"2.0.0"}
```

---

## Docker Containerization

### Dockerfile Architecture

The application uses a **multi-stage Docker build**:

**Stage 1: Builder**
- Base Image: `maven:3.9.4-eclipse-temurin-8`
- Purpose: Build the WAR file
- Optimizations: Dependency caching layer

**Stage 2: Runtime**
- Base Image: `eclipse-temurin:8-jdk` with Tomcat 9
- Purpose: Run the application
- Security: Non-root user (tomcat)
- JVM Settings: Optimized for containers

### Key Features
- **Multi-stage build** reduces final image size
- **Dependency caching** speeds up rebuilds
- **Non-root user** enhances security
- **JVM container awareness** for proper memory management
- **Health check support** via `/health` endpoint

---

## AWS ECS Fargate Prerequisites

### 1. VPC and Networking Setup

You need a VPC with the following components:

```bash
# Create VPC (if not exists)
aws ec2 create-vpc --cidr-block 10.0.0.0/16 --region us-east-1

# Create public subnets (at least 2 for high availability)
aws ec2 create-subnet --vpc-id <vpc-id> --cidr-block 10.0.1.0/24 --availability-zone us-east-1a
aws ec2 create-subnet --vpc-id <vpc-id> --cidr-block 10.0.2.0/24 --availability-zone us-east-1b

# Create Internet Gateway
aws ec2 create-internet-gateway
aws ec2 attach-internet-gateway --vpc-id <vpc-id> --internet-gateway-id <igw-id>

# Update route table
aws ec2 create-route --route-table-id <rt-id> --destination-cidr-block 0.0.0.0/0 --gateway-id <igw-id>
```

### 2. Security Group Configuration

Create a security group that allows:
- **Inbound:** Port 8080 (application) from ALB or 0.0.0.0/0
- **Outbound:** All traffic (for pulling images, external APIs)

```bash
# Create security group
aws ec2 create-security-group \
  --group-name modresorts-sg \
  --description "Security group for ModResorts ECS tasks" \
  --vpc-id <vpc-id>

# Add inbound rule for application port
aws ec2 authorize-security-group-ingress \
  --group-id <sg-id> \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Add inbound rule for ALB health checks (if using ALB)
aws ec2 authorize-security-group-ingress \
  --group-id <sg-id> \
  --protocol tcp \
  --port 8080 \
  --source-group <alb-sg-id>
```

### 3. IAM Roles

#### ECS Task Execution Role
This role allows ECS to pull images from ECR and write logs to CloudWatch.

```bash
# Create trust policy file: ecs-task-execution-trust-policy.json
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

# Create the role
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach AWS managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### ECS Task Role (Optional)
This role grants permissions to the application itself (e.g., access to S3, DynamoDB).

```bash
# Create the role
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach custom policies as needed
aws iam attach-role-policy \
  --role-name ecsTaskRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess
```

### 4. CloudWatch Log Group

```bash
# Create log group for application logs
aws logs create-log-group --log-group-name /ecs/modresorts --region us-east-1

# Set retention policy (optional)
aws logs put-retention-policy \
  --log-group-name /ecs/modresorts \
  --retention-in-days 7
```

---

## ECS Fargate Setup

### Understanding Fargate

AWS Fargate is a serverless compute engine for containers that:
- Eliminates the need to manage EC2 instances
- Automatically scales infrastructure
- Charges only for resources used
- Provides built-in security and isolation

### Valid CPU and Memory Combinations

Fargate requires specific CPU/memory combinations:

| CPU (vCPU) | Memory (MB) |
|------------|-------------|
| 256 (.25)  | 512, 1024, 2048 |
| 512 (.5)   | 1024, 2048, 3072, 4096 |
| 1024 (1)   | 2048-8192 (increments of 1024) |
| 2048 (2)   | 4096-16384 (increments of 1024) |
| 4096 (4)   | 8192-30720 (increments of 1024) |

**Default for ModResorts:** CPU: 512, Memory: 1024

---

## Building and Pushing Docker Images

### Option 1: Using build-push.sh (Linux/macOS)

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh

# Follow the prompts:
# 1. Select registry (AWS ECR or Docker Hub)
# 2. Enter registry credentials
# 3. Enter image tag (default: latest)
```

### Option 2: Using build-push.bat (Windows)

```cmd
# Run the script
scripts\build-push.bat

# Follow the prompts
```

### Manual Build and Push (AWS ECR)

```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO=modresorts
IMAGE_TAG=latest

# Authenticate with ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Create ECR repository (if not exists)
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION

# Build image
docker build -t $ECR_REPO:$IMAGE_TAG .

# Tag image
docker tag $ECR_REPO:$IMAGE_TAG \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:$IMAGE_TAG

# Push image
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:$IMAGE_TAG
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) defines how your container runs:

### Key Components

```json
{
  "family": "modresorts-task",
  "networkMode": "awsvpc",              // Required for Fargate
  "requiresCompatibilities": ["FARGATE"], // Launch type
  "cpu": "512",                          // Task-level CPU
  "memory": "1024",                      // Task-level memory
  "executionRoleArn": "...",             // Role for ECS agent
  "taskRoleArn": "...",                  // Role for application
  "containerDefinitions": [...]          // Container configuration
}
```

### Container Definition

```json
{
  "name": "modresorts",
  "image": "{{IMAGE_URI}}",              // Replaced during deployment
  "essential": true,                     // Task fails if container fails
  "portMappings": [
    {
      "containerPort": 8080,             // Application port
      "protocol": "tcp"
    }
  ],
  "environment": [                       // Environment variables
    {
      "name": "JAVA_OPTS",
      "value": "-Xmx512m -Xms256m ..."
    }
  ],
  "logConfiguration": {                  // CloudWatch Logs
    "logDriver": "awslogs",
    "options": {
      "awslogs-group": "/ecs/modresorts",
      "awslogs-region": "us-east-1",
      "awslogs-stream-prefix": "ecs"
    }
  }
}
```

### Registering Task Definition

```bash
# Register task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1
```

---

## ECS Service Configuration

The service definition (`ecs/service-definition.json`) manages running tasks:

### Key Components

```json
{
  "serviceName": "modresorts-service",
  "cluster": "{{CLUSTER_NAME}}",
  "taskDefinition": "modresorts-task",
  "desiredCount": 2,                     // Number of tasks
  "launchType": "FARGATE",
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["subnet-1", "subnet-2"],
      "securityGroups": ["sg-xxx"],
      "assignPublicIp": "ENABLED"        // Required for public access
    }
  },
  "loadBalancers": [                     // Optional
    {
      "targetGroupArn": "...",
      "containerName": "modresorts",
      "containerPort": 8080
    }
  ]
}
```

### Creating/Updating Service

```bash
# Create service
aws ecs create-service \
  --cli-input-json file://ecs/service-definition.json \
  --region us-east-1

# Update service (new deployment)
aws ecs update-service \
  --cluster my-cluster \
  --service modresorts-service \
  --task-definition modresorts-task:2 \
  --force-new-deployment \
  --region us-east-1
```

---

## Deployment Walkthrough

### Step 1: Build and Push Image

```bash
# Run build-push script
./scripts/build-push.sh

# Select AWS ECR
# Enter region: us-east-1
# Enter account ID: 123456789012
# Enter repository: modresorts
# Enter tag: v1.0.0
```

### Step 2: Deploy to ECS

```bash
# Run deployment script
./scripts/deploy-image.sh

# Provide the following information:
# - AWS Region: us-east-1
# - ECS Cluster Name: modresorts-cluster
# - Image URI: 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:v1.0.0
# - VPC ID: vpc-xxxxx
# - Subnet IDs: subnet-xxxxx,subnet-yyyyy
# - Security Group ID: sg-xxxxx
# - Load Balancer: y (if needed)
```

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

# Get task details
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --region us-east-1
```

### Step 4: Test Application

```bash
# If using ALB
curl http://<alb-dns-name>/health

# If using public IP (get from task details)
curl http://<task-public-ip>:8080/health

# Expected response
{"status":"UP","application":"ModResorts","version":"2.0.0"}
```

---

## Monitoring and Logging

### CloudWatch Logs

```bash
# View logs in real-time
aws logs tail /ecs/modresorts --follow --region us-east-1

# View logs for specific time range
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --start-time $(date -d '1 hour ago' +%s)000 \
  --region us-east-1

# Search logs
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --filter-pattern "ERROR" \
  --region us-east-1
```

### CloudWatch Metrics

Key metrics to monitor:
- **CPUUtilization:** Task CPU usage
- **MemoryUtilization:** Task memory usage
- **TargetResponseTime:** ALB response time
- **HealthyHostCount:** Number of healthy tasks
- **UnHealthyHostCount:** Number of unhealthy tasks

```bash
# Get CPU utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=modresorts-service Name=ClusterName,Value=modresorts-cluster \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average \
  --region us-east-1
```

### Application Health Checks

The application provides health endpoints:
- **Primary:** `/health`
- **Alternative:** `/actuator/health`

Health check configuration in ALB:
- **Protocol:** HTTP
- **Path:** `/health`
- **Interval:** 30 seconds
- **Timeout:** 5 seconds
- **Healthy threshold:** 2
- **Unhealthy threshold:** 3

---

## Troubleshooting

### Common Issues

#### 1. Task Fails to Start

**Symptoms:**
- Tasks transition from PENDING to STOPPED
- No logs in CloudWatch

**Possible Causes:**
- Invalid CPU/memory combination
- Image pull failure (ECR permissions)
- Missing execution role

**Solutions:**
```bash
# Check task stopped reason
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --region us-east-1 \
  --query 'tasks[0].stoppedReason'

# Verify execution role permissions
aws iam get-role --role-name ecsTaskExecutionRole

# Test ECR access
aws ecr describe-images \
  --repository-name modresorts \
  --region us-east-1
```

#### 2. Health Check Failures

**Symptoms:**
- Tasks marked as unhealthy
- Continuous task replacement

**Possible Causes:**
- Application not listening on port 8080
- Health endpoint not responding
- Security group blocking traffic

**Solutions:**
```bash
# Check application logs
aws logs tail /ecs/modresorts --follow

# Test health endpoint from task
aws ecs execute-command \
  --cluster modresorts-cluster \
  --task <task-id> \
  --container modresorts \
  --interactive \
  --command "curl localhost:8080/health"

# Verify security group rules
aws ec2 describe-security-groups --group-ids <sg-id>
```

#### 3. Network Connectivity Issues

**Symptoms:**
- Cannot access application
- Tasks cannot pull images

**Possible Causes:**
- Missing Internet Gateway
- Incorrect route table
- Security group blocking traffic

**Solutions:**
```bash
# Verify subnet has route to IGW
aws ec2 describe-route-tables \
  --filters "Name=association.subnet-id,Values=<subnet-id>"

# Check security group rules
aws ec2 describe-security-groups --group-ids <sg-id>

# Verify NAT Gateway (if using private subnets)
aws ec2 describe-nat-gateways
```

#### 4. Out of Memory Errors

**Symptoms:**
- Tasks killed with exit code 137
- OOMKilled in task stopped reason

**Solutions:**
```bash
# Increase task memory in task definition
# Update JAVA_OPTS to use less heap
# Example: -Xmx384m instead of -Xmx512m

# Monitor memory usage
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ServiceName,Value=modresorts-service \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 \
  --statistics Maximum \
  --region us-east-1
```

#### 5. Deployment Stuck

**Symptoms:**
- Service update in progress for extended time
- Old tasks not draining

**Solutions:**
```bash
# Check deployment status
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1 \
  --query 'services[0].deployments'

# Force new deployment
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --force-new-deployment \
  --region us-east-1

# If stuck, delete and recreate service
aws ecs delete-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --force \
  --region us-east-1
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale up to 5 tasks
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --desired-count 5 \
  --region us-east-1

# Scale down to 1 task
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --desired-count 1 \
  --region us-east-1
```

### Auto Scaling

#### Target Tracking Scaling Policy

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10 \
  --region us-east-1

# Create scaling policy (CPU-based)
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name cpu-scaling-policy \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-policy.json \
  --region us-east-1
```

**scaling-policy.json:**
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleOutCooldown": 60,
  "ScaleInCooldown": 300
}
```

### Blue/Green Deployments

For zero-downtime deployments, use AWS CodeDeploy with ECS:

```bash
# Create CodeDeploy application
aws deploy create-application \
  --application-name modresorts-app \
  --compute-platform ECS \
  --region us-east-1

# Create deployment group
aws deploy create-deployment-group \
  --application-name modresorts-app \
  --deployment-group-name modresorts-dg \
  --service-role-arn arn:aws:iam::123456789012:role/CodeDeployServiceRole \
  --ecs-services clusterName=modresorts-cluster,serviceName=modresorts-service \
  --load-balancer-info targetGroupPairInfoList=[...] \
  --deployment-config-name CodeDeployDefault.ECSAllAtOnce \
  --region us-east-1
```

### Rolling Updates

ECS supports rolling updates with configurable deployment parameters:

```json
{
  "deploymentConfiguration": {
    "maximumPercent": 200,        // Max tasks during deployment
    "minimumHealthyPercent": 50   // Min healthy tasks during deployment
  }
}
```

**Example scenarios:**
- **Fast rollout:** maximumPercent=200, minimumHealthyPercent=50
- **Conservative:** maximumPercent=150, minimumHealthyPercent=100
- **One-at-a-time:** maximumPercent=100, minimumHealthyPercent=0

---

## Security Considerations

### 1. Container Security

- **Non-root user:** Application runs as `tomcat` user (UID 1000)
- **Read-only root filesystem:** Consider adding `readonlyRootFilesystem: true`
- **No privileged mode:** Never use `privileged: true`
- **Minimal base image:** Using Eclipse Temurin (official OpenJDK distribution)

### 2. Network Security

- **Security groups:** Restrict inbound traffic to necessary ports only
- **Private subnets:** Use private subnets with NAT Gateway for production
- **VPC endpoints:** Use VPC endpoints for ECR and CloudWatch to avoid internet traffic

```bash
# Create VPC endpoint for ECR
aws ec2 create-vpc-endpoint \
  --vpc-id <vpc-id> \
  --service-name com.amazonaws.us-east-1.ecr.dkr \
  --route-table-ids <rt-id> \
  --region us-east-1
```

### 3. Secrets Management

Use AWS Secrets Manager or Systems Manager Parameter Store for sensitive data:

```json
{
  "secrets": [
    {
      "name": "DB_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:db-password"
    }
  ]
}
```

### 4. IAM Best Practices

- **Least privilege:** Grant only necessary permissions
- **Task role:** Use task role for application permissions (not execution role)
- **Rotate credentials:** Regularly rotate IAM access keys
- **MFA:** Enable MFA for IAM users

### 5. Image Security

- **Scan images:** Use ECR image scanning
- **Update base images:** Regularly update to latest security patches
- **Verify signatures:** Use Docker Content Trust

```bash
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

### 6. Logging and Auditing

- **CloudTrail:** Enable CloudTrail for API audit logs
- **VPC Flow Logs:** Enable VPC Flow Logs for network traffic analysis
- **Container logs:** Send all application logs to CloudWatch

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [ECS Service Definition](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html)

### Best Practices
- [ECS Best Practices Guide](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/intro.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Java in Containers](https://developers.redhat.com/blog/2017/03/14/java-inside-docker)

### Support
For issues or questions:
1. Check CloudWatch Logs: `/ecs/modresorts`
2. Review ECS service events
3. Consult AWS Support
4. Review application logs

---

## Appendix

### A. Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| JAVA_OPTS | JVM options | -Xmx512m -Xms256m |
| TZ | Timezone | UTC |
| CATALINA_HOME | Tomcat home directory | /usr/local/tomcat |

### B. Port Mappings

| Port | Protocol | Description |
|------|----------|-------------|
| 8080 | TCP | Application HTTP port |

### C. Health Check Endpoints

| Endpoint | Method | Response |
|----------|--------|----------|
| /health | GET | {"status":"UP",...} |
| /actuator/health | GET | {"status":"UP",...} |

### D. Resource Requirements

| Environment | CPU | Memory | Tasks |
|-------------|-----|--------|-------|
| Development | 256 | 512 | 1 |
| Staging | 512 | 1024 | 2 |
| Production | 1024 | 2048 | 4+ |

### E. Cost Estimation

Fargate pricing (us-east-1, as of 2024):
- **CPU:** $0.04048 per vCPU per hour
- **Memory:** $0.004445 per GB per hour

**Example (2 tasks, 512 CPU, 1024 MB):**
- CPU: 2 × 0.5 × $0.04048 × 730 hours = $29.55/month
- Memory: 2 × 1 × $0.004445 × 730 hours = $6.49/month
- **Total:** ~$36/month (excluding data transfer and ALB costs)

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Maintained By:** DevOps Team
