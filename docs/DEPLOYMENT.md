# ModResorts Application - AWS EKS Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [AWS EKS Deployment](#aws-eks-deployment)
6. [Configuration Management](#configuration-management)
7. [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
8. [Scaling and Management](#scaling-and-management)
9. [Security Considerations](#security-considerations)
10. [Technology-Specific Notes](#technology-specific-notes)

---

## Overview

ModResorts is a Java 8 web application built with Spring MVC and packaged as a WAR file. This guide covers containerization and deployment to AWS EKS (Elastic Kubernetes Service).

**Application Details:**
- **Technology Stack**: Java 8, Spring MVC, Maven
- **Package Type**: WAR (deployed on Tomcat 9)
- **Application Port**: 8080
- **Health Endpoint**: `/health` and `/actuator/health`
- **Base Image**: Amazon Corretto 8 (amazoncorretto:8)

---

## Prerequisites

### Required Tools

1. **Docker** (version 20.10 or later)
   ```bash
   docker --version
   ```

2. **AWS CLI** (version 2.x)
   ```bash
   aws --version
   ```
   Configure AWS credentials:
   ```bash
   aws configure
   ```

3. **kubectl** (Kubernetes CLI)
   ```bash
   kubectl version --client
   ```

4. **eksctl** (optional, for EKS cluster management)
   ```bash
   eksctl version
   ```

5. **Maven** (version 3.6 or later)
   ```bash
   mvn --version
   ```

### AWS Requirements

- **AWS Account** with appropriate permissions
- **IAM Permissions** for:
  - ECR (Elastic Container Registry)
  - EKS (Elastic Kubernetes Service)
  - EC2, VPC, CloudFormation (for EKS cluster)
- **EKS Cluster** (existing or new)
- **AWS Load Balancer Controller** installed in EKS cluster

### Network Requirements

- VPC with public and private subnets
- Internet Gateway for public subnets
- NAT Gateway for private subnets
- Security groups configured for EKS

---

## Local Development Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd Backendservice
```

### 2. Build the Application Locally

```bash
mvn clean package
```

The WAR file will be generated in `target/modresorts-2.0.0.war`

### 3. Run with Docker Compose

```bash
docker-compose up --build
```

Access the application:
- Application: http://localhost:8080
- Health Check: http://localhost:8080/health

### 4. Stop the Application

```bash
docker-compose down
```

---

## Building and Pushing Docker Images

### Option 1: Using Build Script (Recommended)

#### Linux/macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

#### Windows

```cmd
scripts\build-push.bat
```

The script will prompt you for:
1. **Registry Type**: AWS ECR or Docker Hub
2. **Registry Details**: Region, account ID, repository name
3. **Image Tag**: Version tag (default: latest)

### Option 2: Manual Build and Push

#### AWS ECR

```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
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

#### Docker Hub

```bash
# Set variables
DOCKER_USERNAME=your-username
IMAGE_TAG=latest

# Login to Docker Hub
docker login

# Build image
docker build -t $DOCKER_USERNAME/modresorts:$IMAGE_TAG .

# Push image
docker push $DOCKER_USERNAME/modresorts:$IMAGE_TAG
```

---

## AWS EKS Deployment

### Prerequisites for EKS Deployment

1. **EKS Cluster Setup**

If you don't have an EKS cluster, create one:

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

2. **Install AWS Load Balancer Controller**

```bash
# Create IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.4.7/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

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

### Deploy Using Script (Recommended)

#### Linux/macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows

```cmd
scripts\deploy-image.bat
```

The script will prompt you for:
1. **AWS Region**: e.g., us-east-1
2. **EKS Cluster Name**: Your cluster name
3. **Docker Image URI**: Full image path with tag

### Manual Deployment

1. **Configure kubectl**

```bash
aws eks update-kubeconfig --region us-east-1 --name modresorts-cluster
```

2. **Update Deployment Manifest**

Edit `kubernetes/deployment.yaml` and replace `{{IMAGE_URI}}` with your actual image URI:

```yaml
image: 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

3. **Apply Kubernetes Manifests**

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

4. **Verify Deployment**

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

5. **Get Application URL**

```bash
kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

---

## Configuration Management

### Environment Variables

The application supports the following environment variables (configured in `kubernetes/deployment.yaml`):

#### JVM Configuration
- `JAVA_OPTS`: JVM options (default: `-Xmx512m -Xms256m -XX:+UseContainerSupport`)
- `TZ`: Timezone (default: `UTC`)

#### Database Configuration (if needed)
```yaml
- name: DB_HOST
  value: "your-database-host"
- name: DB_PORT
  value: "5432"
- name: DB_NAME
  value: "modresorts"
- name: DB_USER
  valueFrom:
    secretKeyRef:
      name: modresorts-secrets
      key: db-user
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: modresorts-secrets
      key: db-password
```

#### Redis Configuration (if needed)
```yaml
- name: REDIS_HOST
  value: "your-redis-host"
- name: REDIS_PORT
  value: "6379"
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: modresorts-secrets
      key: redis-password
```

### Creating Secrets

```bash
# Create secret for database credentials
kubectl create secret generic modresorts-secrets \
  --from-literal=db-user=dbuser \
  --from-literal=db-password=dbpassword \
  --from-literal=redis-password=redispassword \
  -n modresorts
```

### ConfigMaps

For application configuration files:

```bash
kubectl create configmap modresorts-config \
  --from-file=application.properties \
  -n modresorts
```

Mount in deployment:
```yaml
volumeMounts:
- name: config
  mountPath: /app/config
volumes:
- name: config
  configMap:
    name: modresorts-config
```

---

## Monitoring and Troubleshooting

### View Logs

```bash
# View logs from all pods
kubectl logs -n modresorts -l app=modresorts

# View logs from specific pod
kubectl logs -n modresorts <pod-name>

# Follow logs
kubectl logs -n modresorts -l app=modresorts -f

# View previous container logs (if crashed)
kubectl logs -n modresorts <pod-name> --previous
```

### Describe Resources

```bash
# Describe pod
kubectl describe pod <pod-name> -n modresorts

# Describe deployment
kubectl describe deployment modresorts -n modresorts

# Describe service
kubectl describe svc modresorts-service -n modresorts

# Describe ingress
kubectl describe ingress modresorts-ingress -n modresorts
```

### Common Issues and Solutions

#### 1. Pods Not Starting

**Symptoms**: Pods stuck in `Pending` or `CrashLoopBackOff`

**Solutions**:
```bash
# Check pod events
kubectl describe pod <pod-name> -n modresorts

# Check resource availability
kubectl top nodes

# Check pod logs
kubectl logs <pod-name> -n modresorts
```

#### 2. Image Pull Errors

**Symptoms**: `ImagePullBackOff` or `ErrImagePull`

**Solutions**:
- Verify image URI is correct
- Check ECR permissions
- Ensure nodes have ECR access (IAM role)

```bash
# Verify image exists in ECR
aws ecr describe-images --repository-name modresorts --region us-east-1
```

#### 3. Health Check Failures

**Symptoms**: Pods restarting frequently

**Solutions**:
- Check health endpoint is accessible
- Increase `initialDelaySeconds` in probes
- Verify application is starting correctly

```bash
# Test health endpoint from within pod
kubectl exec -it <pod-name> -n modresorts -- curl http://localhost:8080/health
```

#### 4. Ingress Not Working

**Symptoms**: Cannot access application via Load Balancer

**Solutions**:
- Verify AWS Load Balancer Controller is running
- Check security groups allow traffic
- Verify target group health in AWS Console

```bash
# Check Load Balancer Controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Check ingress status
kubectl get ingress modresorts-ingress -n modresorts -o yaml
```

### Health Checks

Test health endpoints:

```bash
# From outside cluster (via Load Balancer)
curl http://<load-balancer-url>/health

# From within cluster
kubectl run curl --image=curlimages/curl -i --rm --restart=Never -- \
  curl http://modresorts-service.modresorts.svc.cluster.local/health
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment modresorts -n modresorts --replicas=3

# Verify scaling
kubectl get pods -n modresorts
```

### Horizontal Pod Autoscaler (HPA)

Create HPA based on CPU utilization:

```bash
kubectl autoscale deployment modresorts \
  -n modresorts \
  --cpu-percent=70 \
  --min=2 \
  --max=10
```

Or create HPA manifest:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: modresorts-hpa
  namespace: modresorts
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: modresorts
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/modresorts \
  modresorts=123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:v2.0.1 \
  -n modresorts

# Check rollout status
kubectl rollout status deployment/modresorts -n modresorts

# View rollout history
kubectl rollout history deployment/modresorts -n modresorts
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/modresorts -n modresorts

# Rollback to specific revision
kubectl rollout undo deployment/modresorts -n modresorts --to-revision=2
```

### Resource Management

Update resource limits:

```bash
kubectl set resources deployment modresorts \
  -n modresorts \
  --limits=cpu=1000m,memory=2Gi \
  --requests=cpu=500m,memory=1Gi
```

---

## Security Considerations

### 1. Image Security

- Use specific image tags (avoid `latest`)
- Scan images for vulnerabilities
- Use minimal base images
- Keep base images updated

```bash
# Scan image with AWS ECR
aws ecr start-image-scan \
  --repository-name modresorts \
  --image-id imageTag=latest \
  --region us-east-1
```

### 2. Network Security

- Use Network Policies to restrict pod communication
- Configure Security Groups for EKS nodes
- Use private subnets for worker nodes
- Enable VPC Flow Logs

Example Network Policy:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: modresorts-netpol
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
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 443
```

### 3. Secrets Management

- Use Kubernetes Secrets for sensitive data
- Consider AWS Secrets Manager integration
- Enable encryption at rest for Secrets
- Use RBAC to restrict Secret access

### 4. RBAC Configuration

Create service account with limited permissions:

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

- Run containers as non-root user (already configured)
- Use read-only root filesystem where possible
- Drop unnecessary capabilities
- Enable Pod Security Standards

---

## Technology-Specific Notes

### Java 8 and Tomcat 9

**JVM Configuration:**
- The application uses Amazon Corretto 8 as the base image
- JVM is configured for containerized environments with `-XX:+UseContainerSupport`
- Memory limits are set to 512MB heap max, 256MB heap min
- MaxRAMPercentage is set to 75% for optimal container memory usage

**Tomcat Configuration:**
- Tomcat 9.0.82 is used as the servlet container
- Application is deployed as ROOT.war for root context
- Default connector port is 8080
- Graceful shutdown is configured with 30-second timeout

**Startup Time:**
- Initial startup may take 60-90 seconds
- Liveness probe has 90-second initial delay
- Readiness probe has 60-second initial delay

**Health Checks:**
- Custom health check servlet at `/health` and `/actuator/health`
- Returns JSON status with application information
- Includes basic JVM memory checks

**Performance Tuning:**
- Consider increasing heap size for production workloads
- Monitor garbage collection metrics
- Use JMX for runtime monitoring
- Enable JVM diagnostic flags if needed:
  ```yaml
  env:
  - name: JAVA_OPTS
    value: "-Xmx1g -Xms512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+PrintGCDetails -XX:+PrintGCDateStamps"
  ```

**Logging:**
- Application logs are written to stdout/stderr
- Captured by Kubernetes and available via `kubectl logs`
- Consider integrating with CloudWatch Logs or ELK stack

**Spring Framework:**
- Application uses Spring MVC 5.3.20
- Spring Data Redis is configured for caching
- Lettuce client is used for Redis connections

**External Dependencies:**
- Redis cache (optional, configure via environment variables)
- Database connections (configure via environment variables)
- External APIs (configure via environment variables)

---

## Additional Resources

### AWS Documentation
- [Amazon EKS User Guide](https://docs.aws.amazon.com/eks/latest/userguide/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)
- [Amazon ECR User Guide](https://docs.aws.amazon.com/ecr/latest/userguide/)

### Kubernetes Documentation
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)

### Monitoring and Observability
- [Prometheus Operator](https://github.com/prometheus-operator/prometheus-operator)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/)
- [AWS CloudWatch Container Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/ContainerInsights.html)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update Base Images**: Regularly update to latest security patches
2. **Monitor Resource Usage**: Adjust limits based on actual usage
3. **Review Logs**: Check for errors and warnings
4. **Update Dependencies**: Keep Java dependencies up to date
5. **Backup Configuration**: Version control all manifests

### Cleanup

To remove the deployment:

```bash
# Delete all resources
kubectl delete -f kubernetes/

# Or delete namespace (removes everything)
kubectl delete namespace modresorts
```

To delete ECR repository:

```bash
aws ecr delete-repository --repository-name modresorts --region us-east-1 --force
```

---

## Conclusion

This guide provides comprehensive instructions for deploying the ModResorts Java application to AWS EKS. Follow the steps carefully and refer to the troubleshooting section if you encounter issues.

For questions or issues, please contact the development team or refer to the project documentation.
