# ModResorts - Deployment Guide

## Overview

This guide covers the complete deployment process for the **ModResorts** application (`com.acme.modres:modresorts:2.0.0`) — a Java EE 7 web application (WAR) migrated from IBM WebSphere to a container-native architecture targeting **AWS EKS (Elastic Kubernetes Service)**.

- **Application**: ModResorts Resort Booking & Weather Service
- **Technology**: Java 8, Java EE 7 (Servlet 3.1), Maven, Apache Tomcat 9
- **Package**: WAR deployed to embedded Tomcat
- **Context Root**: `/resorts`
- **Health Endpoint**: `GET /resorts/health`
- **Application Port**: `8080`

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [AWS EKS Prerequisites](#aws-eks-prerequisites)
6. [EKS Cluster Setup](#eks-cluster-setup)
7. [Kubernetes Deployment](#kubernetes-deployment)
8. [Environment Variables Reference](#environment-variables-reference)
9. [EKS Scaling and Management](#eks-scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)
12. [Java-Specific Notes](#java-specific-notes)

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20.10+ | Container build and run |
| Docker Compose | 2.x | Local multi-container orchestration |
| Java JDK | 8+ | Local build (optional) |
| Maven | 3.8+ | Local build (optional) |

### AWS EKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x | AWS authentication and ECR |
| kubectl | 1.27+ | Kubernetes cluster management |
| eksctl | 0.150+ | EKS cluster creation (optional) |

---

## Project Structure

```
Backendservice/
├── Dockerfile                    # Multi-stage build (Java 8 + Tomcat 9)
├── docker-compose.yml            # Local development stack
├── .dockerignore                 # Docker build exclusions
├── pom.xml                       # Maven build descriptor
├── src/
│   └── main/
│       ├── java/com/acme/modres/ # Application source code
│       └── resources/            # ops.json, reservations.json
├── WebContent/                   # Static web assets, WEB-INF/web.xml
├── kubernetes/
│   ├── namespace.yaml            # Kubernetes namespace
│   ├── deployment.yaml           # Application deployment (2 replicas)
│   ├── service.yaml              # ClusterIP service (port 80 → 8080)
│   └── ingress.yaml              # AWS ALB Ingress
├── scripts/
│   ├── build-push.sh             # Linux/macOS: build & push to ECR/DockerHub
│   ├── build-push.bat            # Windows: build & push to ECR/DockerHub
│   ├── deploy-image.sh           # Linux/macOS: deploy to AWS EKS
│   └── deploy-image.bat          # Windows: deploy to AWS EKS
└── docs/
    └── DEPLOYMENT.md             # This file
```

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root:

```bash
# Weather API (optional - uses default data if not set)
WEATHER_API_KEY=your_weather_api_key_here

# Server identity
SERVER_DISPLAY_NAME=modresorts-server
SERVER_FULL_NAME=modresorts-server/default

# JNDI (standard Java - not WebSphere)
JNDI_FACTORY=com.sun.jndi.rmi.registry.RegistryContextFactory
JNDI_PROVIDER_URL=rmi://localhost:1099

# Database (external - provide your connection details)
DB_HOST=your-db-host
DB_PORT=5432
DB_NAME=modresorts
DB_USERNAME=modresorts
DB_PASSWORD=your-db-password
```

### 2. Build and Start

```bash
# Build and start the application
docker-compose up --build

# Run in background
docker-compose up --build -d

# View logs
docker-compose logs -f modresorts

# Stop
docker-compose down
```

### 3. Verify Application

```bash
# Health check
curl http://localhost:8080/resorts/health

# Expected response:
# {"status":"UP","service":"modresorts"}

# Application home
open http://localhost:8080/resorts/
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run from repository root
./scripts/build-push.sh
```

The script will prompt for:
1. **Image tag** (default: `latest`)
2. **Registry type**: `1` for AWS ECR, `2` for Docker Hub
3. Registry-specific credentials

### Windows

```cmd
REM Run from repository root
scripts\build-push.bat
```

### Manual Build (Advanced)

```bash
# Build image
docker build -f Dockerfile -t modresorts:latest .

# Tag for ECR
docker tag modresorts:latest 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest

# Push to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

---

## AWS EKS Prerequisites

### 1. IAM Permissions

Ensure your AWS IAM user/role has the following permissions:
- `eks:DescribeCluster`
- `eks:UpdateKubeconfig`
- `ecr:GetAuthorizationToken`
- `ecr:BatchCheckLayerAvailability`
- `ecr:GetDownloadUrlForLayer`
- `ecr:BatchGetImage`
- `ecr:CreateRepository`
- `ecr:PutImage`

### 2. AWS Load Balancer Controller

The ingress uses the AWS Load Balancer Controller. Install it on your EKS cluster:

```bash
# Add the EKS chart repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<your-cluster-name> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### 3. Configure AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

---

## EKS Cluster Setup

### Create a New EKS Cluster (if needed)

```bash
eksctl create cluster \
  --name modresorts-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed
```

### Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name modresorts-cluster

# Verify connectivity
kubectl cluster-info
kubectl get nodes
```

---

## Kubernetes Deployment

### Automated Deployment (Recommended)

#### Linux / macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows

```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region
- EKS Cluster Name
- Docker image URI (full path with tag)
- Optional environment variable values

### Manual Deployment

```bash
# 1. Set your image URI
export IMAGE_URI="123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest"

# 2. Update deployment manifest
sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' kubernetes/deployment.yaml

# 3. Apply manifests in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Wait for rollout
kubectl rollout status deployment/modresorts -n modresorts

# 5. Verify
kubectl get pods,svc,ingress -n modresorts
```

### Kubernetes Manifest Descriptions

| File | Kind | Description |
|------|------|-------------|
| `namespace.yaml` | Namespace | Creates `modresorts` namespace |
| `deployment.yaml` | Deployment | 2 replicas, health probes, resource limits |
| `service.yaml` | Service | ClusterIP, port 80 → container 8080 |
| `ingress.yaml` | Ingress | AWS ALB, internet-facing, HTTPS redirect |

---

## Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `WEATHER_API_KEY` | No | (empty) | Weather Underground API key. If not set, uses static default data. |
| `SERVER_DISPLAY_NAME` | No | `modresorts-server` | Server display name (replaces WebSphere API) |
| `SERVER_FULL_NAME` | No | `modresorts-server/default` | Server full name (replaces WebSphere RMI API) |
| `JNDI_FACTORY` | No | `com.sun.jndi.rmi.registry.RegistryContextFactory` | JNDI context factory |
| `JNDI_PROVIDER_URL` | No | `rmi://localhost:1099` | JNDI provider URL |
| `DB_HOST` | No | `localhost` | Database host |
| `DB_PORT` | No | `5432` | Database port |
| `DB_NAME` | No | `modresorts` | Database name |
| `DB_USERNAME` | No | `modresorts` | Database username |
| `DB_PASSWORD` | No | (empty) | Database password |
| `JAVA_OPTS` | No | `-Xms256m -Xmx512m ...` | JVM options |
| `TZ` | No | `UTC` | Timezone |

### Using Kubernetes Secrets for Sensitive Values

```bash
# Create a secret for sensitive environment variables
kubectl create secret generic modresorts-secrets \
  --from-literal=WEATHER_API_KEY=your_api_key \
  --from-literal=DB_PASSWORD=your_db_password \
  -n modresorts

# Reference in deployment.yaml (update env section):
# - name: WEATHER_API_KEY
#   valueFrom:
#     secretKeyRef:
#       name: modresorts-secrets
#       key: WEATHER_API_KEY
```

---

## EKS Scaling and Management

### Horizontal Pod Autoscaling

```bash
# Create HPA (scale between 2-10 pods based on CPU)
kubectl autoscale deployment modresorts \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n modresorts

# Check HPA status
kubectl get hpa -n modresorts
```

### Rolling Updates

```bash
# Update image to new version
kubectl set image deployment/modresorts \
  modresorts=123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:v2.1.0 \
  -n modresorts

# Monitor rollout
kubectl rollout status deployment/modresorts -n modresorts
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/modresorts -n modresorts

# Rollback to specific revision
kubectl rollout history deployment/modresorts -n modresorts
kubectl rollout undo deployment/modresorts --to-revision=2 -n modresorts
```

### Scale Manually

```bash
# Scale to 4 replicas
kubectl scale deployment modresorts --replicas=4 -n modresorts
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl get pods -n modresorts

# Describe pod for events
kubectl describe pod <pod-name> -n modresorts

# Check logs
kubectl logs <pod-name> -n modresorts
kubectl logs -f deployment/modresorts -n modresorts
```

### Health Check Failures

The application exposes a health endpoint at `/resorts/health`:

```bash
# Port-forward to test locally
kubectl port-forward deployment/modresorts 8080:8080 -n modresorts

# Test health endpoint
curl http://localhost:8080/resorts/health
# Expected: {"status":"UP","service":"modresorts"}
```

**Common causes:**
- JVM startup time: Tomcat + Java EE app may take 60-90 seconds. The `initialDelaySeconds: 90` in liveness probe accounts for this.
- Memory issues: Increase `resources.limits.memory` if OOMKilled.
- Missing `WEATHER_API_KEY`: App works without it (uses default data).

### Ingress / ALB Issues

```bash
# Check ingress status
kubectl describe ingress modresorts-ingress -n modresorts

# Check AWS Load Balancer Controller logs
kubectl logs -n kube-system deployment/aws-load-balancer-controller
```

**Common causes:**
- AWS Load Balancer Controller not installed
- Missing IAM permissions for ALB creation
- Subnet tags missing: `kubernetes.io/role/elb: 1`

### Image Pull Errors

```bash
# Check if ECR credentials are valid
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

# Verify image exists
aws ecr describe-images --repository-name modresorts --region us-east-1
```

### OOMKilled (Out of Memory)

```bash
# Check resource usage
kubectl top pods -n modresorts

# Increase memory limit in deployment.yaml:
# resources:
#   limits:
#     memory: "2Gi"
```

---

## Security Considerations

1. **Non-root user**: The container runs as `appuser` (UID 1000), not root.
2. **Secrets management**: Use Kubernetes Secrets or AWS Secrets Manager for sensitive values (API keys, DB passwords). Never hardcode in manifests.
3. **Network policies**: Consider adding Kubernetes NetworkPolicy to restrict pod-to-pod communication.
4. **Image scanning**: Enable ECR image scanning to detect vulnerabilities.
5. **HTTPS**: The ingress is configured to redirect HTTP to HTTPS. Ensure an ACM certificate is configured.
6. **Security groups**: Restrict ALB security groups to known CIDR ranges.
7. **Application security**: The `web.xml` has security constraints commented out for demo purposes. Enable them in production.

```bash
# Enable ECR image scanning
aws ecr put-image-scanning-configuration \
  --repository-name modresorts \
  --image-scanning-configuration scanOnPush=true \
  --region us-east-1
```

---

## Java-Specific Notes

### JVM Configuration

The container uses the following JVM flags (set via `JAVA_OPTS`):

```
-Xms256m                        # Initial heap size
-Xmx512m                        # Maximum heap size
-XX:+UseContainerSupport        # Enable container-aware JVM (Java 8u191+)
-XX:MaxRAMPercentage=75.0       # Use 75% of container memory for heap
-XX:+UnlockExperimentalVMOptions
```

To override, set `JAVA_OPTS` environment variable in the deployment.

### Tomcat Context Root

The WAR is deployed as `resorts.war`, making the context root `/resorts`. All application URLs are prefixed with `/resorts`:
- Home: `http://<host>/resorts/`
- Health: `http://<host>/resorts/health`
- Weather: `http://<host>/resorts/weather?selectedCity=Paris`
- Availability: `http://<host>/resorts/availability?date=04/20/2024`

### WebSphere Migration Notes

This application was migrated from IBM WebSphere Application Server. The following changes were made:
- Replaced `com.ibm.websphere.appserver:was_public` with standard `javax:javaee-api:7.0`
- Replaced `com.ibm.websphere.runtime.ServerName` APIs with environment variables
- Replaced WebSphere `WsnInitialContextFactory` with standard `RegistryContextFactory`
- Replaced `@Singleton @Startup` EJB with `@ApplicationScoped` CDI bean
- Added `HealthCheckServlet` at `/health` for container health probes

### Startup Time

Java EE applications on Tomcat typically take 30-90 seconds to start. The Kubernetes probes are configured with:
- `initialDelaySeconds: 90` (liveness) / `60` (readiness)
- `periodSeconds: 30` / `15`

Adjust these values based on observed startup times in your environment.
