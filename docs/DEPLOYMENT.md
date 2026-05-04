# ModResorts - AWS EKS Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building Docker Image](#building-docker-image)
5. [AWS EKS Prerequisites](#aws-eks-prerequisites)
6. [EKS Cluster Setup](#eks-cluster-setup)
7. [Deploying to AWS EKS](#deploying-to-aws-eks)
8. [Configuration Management](#configuration-management)
9. [Monitoring and Health Checks](#monitoring-and-health-checks)
10. [Scaling and Management](#scaling-and-management)
11. [Troubleshooting](#troubleshooting)
12. [Security Considerations](#security-considerations)

---

## Overview

ModResorts is a Spring Boot 2.7.18 application containerized for deployment on AWS EKS (Elastic Kubernetes Service). This guide provides comprehensive instructions for building, deploying, and managing the application in a Kubernetes environment.

**Technology Stack:**
- Java 8
- Spring Boot 2.7.18
- Maven 3.9.4
- Docker
- Kubernetes (AWS EKS)
- Spring Boot Actuator for health checks
- Redis for distributed caching
- PostgreSQL database

**Application Details:**
- Application Port: 8080
- Health Check Endpoint: `/actuator/health`
- Metrics Endpoint: `/actuator/metrics`
- Prometheus Endpoint: `/actuator/prometheus`

---

## Prerequisites

### Required Software
- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 2.0 or higher
- **AWS CLI**: Version 2.x
- **kubectl**: Version 1.24 or higher
- **eksctl**: Version 0.140 or higher (optional, for cluster creation)
- **Java**: JDK 8 or higher (for local development)
- **Maven**: Version 3.6 or higher (for local builds)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with the following permissions:
  - EKS cluster management
  - ECR repository access
  - VPC and networking permissions
  - IAM role creation
  - CloudWatch logs access

### Installation Instructions

#### Install Docker
```bash
# Linux (Ubuntu/Debian)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# macOS
brew install docker

# Windows
# Download Docker Desktop from https://www.docker.com/products/docker-desktop
```

#### Install AWS CLI
```bash
# Linux/macOS
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows
# Download installer from https://aws.amazon.com/cli/
```

#### Install kubectl
```bash
# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# macOS
brew install kubectl

# Windows
choco install kubernetes-cli
```

#### Install eksctl
```bash
# Linux/macOS
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# Windows
choco install eksctl
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd MRCompTest
```

### 2. Configure Application Properties
Edit `src/main/resources/application.properties` for local development:

```properties
# Local Development Configuration
server.port=8080

# H2 In-Memory Database (for local testing)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.username=sa
spring.datasource.password=

# Local Redis (optional)
spring.redis.host=localhost
spring.redis.port=6379
```

### 3. Build and Run Locally
```bash
# Build with Maven
mvn clean package -DskipTests

# Run the application
java -jar target/modresorts-2.0.0.jar

# Or use Maven Spring Boot plugin
mvn spring-boot:run
```

### 4. Verify Local Deployment
```bash
# Health check
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info
```

### 5. Run with Docker Compose (Local Testing)
```bash
# Build and start
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f

# Stop and remove containers
docker-compose down
```

---

## Building Docker Image

### Using build-push.sh (Linux/macOS)

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh
```

**Script Workflow:**
1. Prompts for registry selection (AWS ECR or Docker Hub)
2. Collects registry credentials
3. Authenticates with selected registry
4. Builds Docker image using multi-stage Dockerfile
5. Tags image appropriately
6. Pushes image to registry

### Using build-push.bat (Windows)

```cmd
# Run the script
scripts\build-push.bat
```

### Manual Docker Build

```bash
# Build image
docker build -t modresorts:latest .

# Tag for ECR
docker tag modresorts:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest

# Push to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

### Dockerfile Optimization Features

The Dockerfile includes several optimizations:
- **Multi-stage build**: Separates build and runtime environments
- **Layer caching**: Dependencies downloaded separately from source code
- **Non-root user**: Runs application as `appuser` for security
- **JVM tuning**: Optimized for containerized environments
- **Minimal runtime image**: Uses Amazon Corretto 8 base image

---

## AWS EKS Prerequisites

### 1. Configure AWS CLI
```bash
# Configure AWS credentials
aws configure

# Verify configuration
aws sts get-caller-identity
```

### 2. Create ECR Repository
```bash
# Create repository
aws ecr create-repository \
    --repository-name modresorts \
    --region us-east-1

# Get repository URI
aws ecr describe-repositories \
    --repository-names modresorts \
    --region us-east-1 \
    --query 'repositories[0].repositoryUri' \
    --output text
```

### 3. Set Up IAM Roles

**EKS Cluster Role:**
```bash
# Create trust policy
cat > eks-cluster-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "eks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create role
aws iam create-role \
    --role-name ModResortsEKSClusterRole \
    --assume-role-policy-document file://eks-cluster-trust-policy.json

# Attach policies
aws iam attach-role-policy \
    --role-name ModResortsEKSClusterRole \
    --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
```

**EKS Node Group Role:**
```bash
# Create node group role
aws iam create-role \
    --role-name ModResortsEKSNodeRole \
    --assume-role-policy-document file://node-trust-policy.json

# Attach required policies
aws iam attach-role-policy \
    --role-name ModResortsEKSNodeRole \
    --policy-arn arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy

aws iam attach-role-policy \
    --role-name ModResortsEKSNodeRole \
    --policy-arn arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy

aws iam attach-role-policy \
    --role-name ModResortsEKSNodeRole \
    --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
```

---

## EKS Cluster Setup

### Option 1: Using eksctl (Recommended)

```bash
# Create EKS cluster with managed node group
eksctl create cluster \
    --name modresorts-cluster \
    --region us-east-1 \
    --version 1.28 \
    --nodegroup-name modresorts-nodes \
    --node-type t3.medium \
    --nodes 2 \
    --nodes-min 2 \
    --nodes-max 4 \
    --managed

# Verify cluster
eksctl get cluster --name modresorts-cluster --region us-east-1
```

### Option 2: Using AWS Console

1. Navigate to EKS in AWS Console
2. Click "Create cluster"
3. Configure cluster settings:
   - Name: modresorts-cluster
   - Kubernetes version: 1.28
   - Cluster service role: ModResortsEKSClusterRole
4. Configure networking (VPC, subnets, security groups)
5. Create cluster
6. Add managed node group:
   - Name: modresorts-nodes
   - Instance type: t3.medium
   - Desired size: 2
   - Node IAM role: ModResortsEKSNodeRole

### Configure kubectl

```bash
# Update kubeconfig
aws eks update-kubeconfig \
    --region us-east-1 \
    --name modresorts-cluster

# Verify connection
kubectl cluster-info
kubectl get nodes
```

### Install AWS Load Balancer Controller

```bash
# Create IAM policy
curl -o iam-policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json

aws iam create-policy \
    --policy-name AWSLoadBalancerControllerIAMPolicy \
    --policy-document file://iam-policy.json

# Create service account
eksctl create iamserviceaccount \
    --cluster=modresorts-cluster \
    --namespace=kube-system \
    --name=aws-load-balancer-controller \
    --attach-policy-arn=arn:aws:iam::<AWS_ACCOUNT_ID>:policy/AWSLoadBalancerControllerIAMPolicy \
    --approve

# Install controller using Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
    -n kube-system \
    --set clusterName=modresorts-cluster \
    --set serviceAccount.create=false \
    --set serviceAccount.name=aws-load-balancer-controller
```

---

## Deploying to AWS EKS

### Using deploy-image.sh (Linux/macOS)

```bash
# Make script executable
chmod +x scripts/deploy-image.sh

# Run deployment script
./scripts/deploy-image.sh
```

**Script will prompt for:**
- AWS Region
- EKS Cluster Name
- Docker Image URI
- Database configuration (URL, username, password, driver)
- Redis configuration (host, port, password, timeout)
- Service registry URL
- AWS CloudMap namespace

### Using deploy-image.bat (Windows)

```cmd
# Run deployment script
scripts\deploy-image.bat
```

### Manual Deployment Steps

#### 1. Update Deployment Manifest

Edit `kubernetes/deployment.yaml` and replace placeholders:
```yaml
image: 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

Update environment variables:
```yaml
env:
- name: DB_URL
  value: "jdbc:postgresql://your-rds-endpoint:5432/modresorts"
- name: DB_USERNAME
  value: "dbuser"
- name: DB_PASSWORD
  value: "your-password"
- name: REDIS_HOST
  value: "your-elasticache-endpoint"
```

#### 2. Apply Kubernetes Manifests

```bash
# Create namespace
kubectl apply -f kubernetes/namespace.yaml

# Deploy application
kubectl apply -f kubernetes/deployment.yaml

# Create service
kubectl apply -f kubernetes/service.yaml

# Create ingress
kubectl apply -f kubernetes/ingress.yaml
```

#### 3. Verify Deployment

```bash
# Check deployment status
kubectl rollout status deployment/modresorts -n modresorts

# View pods
kubectl get pods -n modresorts

# View services
kubectl get svc -n modresorts

# View ingress
kubectl get ingress -n modresorts
```

#### 4. Get Application URL

```bash
# Get load balancer URL
kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# Test health endpoint
curl http://<LOAD_BALANCER_URL>/actuator/health
```

---

## Configuration Management

### Environment Variables

The application uses environment variables for configuration:

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| SERVER_PORT | Application port | 8080 | No |
| DB_URL | Database JDBC URL | jdbc:h2:mem:testdb | Yes |
| DB_USERNAME | Database username | sa | Yes |
| DB_PASSWORD | Database password | (empty) | Yes |
| DB_DRIVER | JDBC driver class | org.h2.Driver | Yes |
| REDIS_HOST | Redis host | localhost | Yes |
| REDIS_PORT | Redis port | 6379 | No |
| REDIS_PASSWORD | Redis password | (empty) | No |
| REDIS_TIMEOUT | Redis timeout (ms) | 2000 | No |
| SERVICE_REGISTRY_URL | Service registry URL | http://localhost:8080 | No |
| AWS_CLOUDMAP_NAMESPACE | AWS CloudMap namespace | (empty) | No |

### Using Kubernetes Secrets

For sensitive data, use Kubernetes Secrets:

```bash
# Create secret for database credentials
kubectl create secret generic db-credentials \
    --from-literal=username=dbuser \
    --from-literal=password=your-password \
    -n modresorts

# Create secret for Redis password
kubectl create secret generic redis-credentials \
    --from-literal=password=your-redis-password \
    -n modresorts
```

Update deployment to use secrets:
```yaml
env:
- name: DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: db-credentials
      key: username
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: db-credentials
      key: password
```

### Using ConfigMaps

For non-sensitive configuration:

```bash
# Create ConfigMap
kubectl create configmap app-config \
    --from-literal=server.port=8080 \
    --from-literal=redis.timeout=2000 \
    -n modresorts
```

---

## Monitoring and Health Checks

### Spring Boot Actuator Endpoints

The application exposes several actuator endpoints:

- **Health**: `/actuator/health` - Application health status
- **Info**: `/actuator/info` - Application information
- **Metrics**: `/actuator/metrics` - Application metrics
- **Prometheus**: `/actuator/prometheus` - Prometheus-formatted metrics

### Kubernetes Health Probes

**Liveness Probe:**
- Checks if application is running
- Endpoint: `/actuator/health`
- Initial delay: 90 seconds
- Period: 10 seconds

**Readiness Probe:**
- Checks if application is ready to serve traffic
- Endpoint: `/actuator/health`
- Initial delay: 60 seconds
- Period: 5 seconds

### View Application Logs

```bash
# View logs from all pods
kubectl logs -f deployment/modresorts -n modresorts

# View logs from specific pod
kubectl logs -f <pod-name> -n modresorts

# View previous container logs
kubectl logs <pod-name> -n modresorts --previous
```

### CloudWatch Integration

```bash
# Install CloudWatch agent
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluentd-quickstart.yaml

# View logs in CloudWatch
aws logs tail /aws/eks/modresorts-cluster/cluster --follow
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment/modresorts -n modresorts --replicas=3

# Verify scaling
kubectl get pods -n modresorts
```

### Horizontal Pod Autoscaler (HPA)

```bash
# Create HPA
kubectl autoscale deployment modresorts \
    --cpu-percent=70 \
    --min=2 \
    --max=10 \
    -n modresorts

# View HPA status
kubectl get hpa -n modresorts

# Describe HPA
kubectl describe hpa modresorts -n modresorts
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/modresorts \
    modresorts=123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:v2.0.1 \
    -n modresorts

# Monitor rollout
kubectl rollout status deployment/modresorts -n modresorts

# View rollout history
kubectl rollout history deployment/modresorts -n modresorts
```

### Rollback Deployment

```bash
# Rollback to previous version
kubectl rollout undo deployment/modresorts -n modresorts

# Rollback to specific revision
kubectl rollout undo deployment/modresorts -n modresorts --to-revision=2
```

---

## Troubleshooting

### Common Issues

#### 1. Pods Not Starting

**Symptoms:**
- Pods stuck in `Pending` or `CrashLoopBackOff` state

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -n modresorts

# Describe pod
kubectl describe pod <pod-name> -n modresorts

# View logs
kubectl logs <pod-name> -n modresorts
```

**Common Causes:**
- Insufficient cluster resources
- Image pull errors
- Configuration errors
- Health check failures

**Solutions:**
- Scale cluster nodes
- Verify image URI and ECR permissions
- Check environment variables
- Adjust health probe timings

#### 2. Service Not Accessible

**Symptoms:**
- Cannot access application via load balancer

**Diagnosis:**
```bash
# Check service
kubectl get svc -n modresorts

# Check ingress
kubectl get ingress -n modresorts

# Describe ingress
kubectl describe ingress modresorts-ingress -n modresorts
```

**Solutions:**
- Verify AWS Load Balancer Controller is installed
- Check security group rules
- Verify ingress annotations
- Check target group health in AWS Console

#### 3. Database Connection Errors

**Symptoms:**
- Application logs show database connection failures

**Solutions:**
- Verify database endpoint and credentials
- Check security group rules for RDS
- Verify VPC and subnet configuration
- Test database connectivity from pod:
```bash
kubectl exec -it <pod-name> -n modresorts -- /bin/sh
# Test connection using psql or telnet
```

#### 4. Redis Connection Errors

**Symptoms:**
- Cache operations failing

**Solutions:**
- Verify ElastiCache endpoint
- Check security group rules
- Verify Redis password if authentication enabled
- Test Redis connectivity:
```bash
kubectl exec -it <pod-name> -n modresorts -- /bin/sh
# Test with redis-cli if available
```

### Debug Commands

```bash
# Get all resources in namespace
kubectl get all -n modresorts

# Describe deployment
kubectl describe deployment modresorts -n modresorts

# Get events
kubectl get events -n modresorts --sort-by='.lastTimestamp'

# Execute command in pod
kubectl exec -it <pod-name> -n modresorts -- /bin/bash

# Port forward for local testing
kubectl port-forward deployment/modresorts 8080:8080 -n modresorts
```

---

## Security Considerations

### 1. Image Security

- Use official base images (Amazon Corretto)
- Scan images for vulnerabilities:
```bash
# Scan with AWS ECR
aws ecr start-image-scan \
    --repository-name modresorts \
    --image-id imageTag=latest \
    --region us-east-1
```

### 2. Network Security

- Use security groups to restrict traffic
- Enable VPC flow logs
- Use private subnets for pods
- Implement network policies:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: modresorts-network-policy
  namespace: modresorts
spec:
  podSelector:
    matchLabels:
      app: modresorts
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
```

### 3. Secrets Management

- Use AWS Secrets Manager or Parameter Store
- Integrate with Kubernetes External Secrets:
```bash
# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets-system --create-namespace
```

### 4. RBAC Configuration

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: modresorts-sa
  namespace: modresorts
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: modresorts-role
  namespace: modresorts
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: modresorts-rolebinding
  namespace: modresorts
subjects:
- kind: ServiceAccount
  name: modresorts-sa
roleRef:
  kind: Role
  name: modresorts-role
  apiGroup: rbac.authorization.k8s.io
```

### 5. Pod Security

- Run as non-root user (already configured in Dockerfile)
- Use read-only root filesystem where possible
- Drop unnecessary capabilities
- Set security context in deployment:
```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: false
```

---

## Additional Resources

### AWS Documentation
- [Amazon EKS User Guide](https://docs.aws.amazon.com/eks/latest/userguide/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)
- [Amazon ECR User Guide](https://docs.aws.amazon.com/ecr/latest/userguide/)

### Kubernetes Documentation
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)

### Spring Boot Documentation
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Spring Boot Docker](https://spring.io/guides/gs/spring-boot-docker/)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update Dependencies**: Regularly update Spring Boot and dependencies
2. **Patch Base Images**: Keep Docker base images updated
3. **Review Logs**: Monitor application and cluster logs
4. **Backup Configuration**: Maintain backups of Kubernetes manifests
5. **Security Audits**: Regular security scans and audits

### Monitoring Checklist

- [ ] Application health checks passing
- [ ] Pod resource utilization within limits
- [ ] No error logs or exceptions
- [ ] Database connections healthy
- [ ] Redis cache operational
- [ ] Load balancer health checks passing
- [ ] SSL certificates valid (if using HTTPS)

---

## Conclusion

This deployment guide provides comprehensive instructions for deploying ModResorts Spring Boot application to AWS EKS. Follow the steps carefully and refer to the troubleshooting section for common issues. For production deployments, ensure all security considerations are implemented and monitoring is properly configured.

For questions or issues, please contact the development team or refer to the official documentation links provided above.
