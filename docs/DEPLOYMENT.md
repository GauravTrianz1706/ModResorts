# ModResorts - Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [Azure AKS Deployment](#azure-aks-deployment)
6. [Configuration Management](#configuration-management)
7. [Monitoring and Health Checks](#monitoring-and-health-checks)
8. [Troubleshooting](#troubleshooting)
9. [Security Considerations](#security-considerations)
10. [Scaling and Performance](#scaling-and-performance)

---

## Overview

ModResorts is a Java EE web application packaged as a WAR file. This guide covers containerization and deployment to Azure Kubernetes Service (AKS).

**Technology Stack:**
- Java 8
- Maven 3.8+
- Apache Tomcat 9.0
- Servlet API 3.1
- Docker
- Kubernetes (Azure AKS)

**Application Details:**
- **Port:** 8080
- **Health Endpoint:** `/health` and `/actuator/health`
- **Package Type:** WAR
- **Base Image:** openjdk:8-jdk with Tomcat 9.0

---

## Prerequisites

### Required Software

1. **Java Development Kit (JDK) 8+**
   ```bash
   java -version
   ```

2. **Maven 3.8+**
   ```bash
   mvn -version
   ```

3. **Docker**
   ```bash
   docker --version
   ```

4. **Azure CLI** (for AKS deployment)
   ```bash
   az --version
   ```

5. **kubectl** (Kubernetes CLI)
   ```bash
   kubectl version --client
   ```

### Azure Requirements

- Active Azure subscription
- Azure Container Registry (ACR) or Docker Hub account
- Azure AKS cluster (or permissions to create one)
- Azure CLI authenticated: `az login`

---

## Local Development Setup

### 1. Clone and Build the Application

```bash
# Navigate to project directory
cd /path/to/modresorts

# Build with Maven
mvn clean package

# The WAR file will be generated at: target/modresorts-2.0.0.war
```

### 2. Run with Docker Compose

```bash
# Build and start the application
docker-compose up --build

# Access the application
# http://localhost:8080

# Stop the application
docker-compose down
```

### 3. Test Health Endpoint

```bash
# Test health check
curl http://localhost:8080/health

# Expected response:
# {"status":"UP","application":"ModResorts"}
```

---

## Building and Pushing Docker Images

### Option 1: Using Build Script (Linux/macOS)

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh
```

**Script will prompt for:**
1. Registry type (Azure ACR or Docker Hub)
2. Registry credentials
3. Image tag (default: latest)

### Option 2: Using Build Script (Windows)

```cmd
# Run the script
scripts\build-push.bat
```

### Option 3: Manual Build and Push

#### For Azure Container Registry (ACR)

```bash
# Login to ACR
az acr login --name <your-acr-name>

# Build image
docker build -t <your-acr-name>.azurecr.io/modresorts:latest .

# Push image
docker push <your-acr-name>.azurecr.io/modresorts:latest
```

#### For Docker Hub

```bash
# Login to Docker Hub
docker login

# Build image
docker build -t <your-username>/modresorts:latest .

# Push image
docker push <your-username>/modresorts:latest
```

---

## Azure AKS Deployment

### Prerequisites

1. **Create AKS Cluster** (if not exists)

```bash
# Create resource group
az group create --name modresorts-rg --location eastus

# Create AKS cluster
az aks create \
  --resource-group modresorts-rg \
  --name modresorts-aks \
  --node-count 2 \
  --node-vm-size Standard_DS2_v2 \
  --enable-managed-identity \
  --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group modresorts-rg --name modresorts-aks
```

2. **Attach ACR to AKS** (if using ACR)

```bash
az aks update \
  --resource-group modresorts-rg \
  --name modresorts-aks \
  --attach-acr <your-acr-name>
```

### Deployment Steps

#### Option 1: Using Deployment Script (Linux/macOS)

```bash
# Make script executable
chmod +x scripts/deploy-image.sh

# Run deployment script
./scripts/deploy-image.sh
```

**Script will prompt for:**
1. Azure Resource Group name
2. AKS Cluster name
3. Docker image URI

#### Option 2: Using Deployment Script (Windows)

```cmd
# Run deployment script
scripts\deploy-image.bat
```

#### Option 3: Manual Deployment

```bash
# 1. Update deployment.yaml with your image URI
sed -i 's|{{IMAGE_URI}}|<your-registry>/modresorts:latest|g' kubernetes/deployment.yaml

# 2. Apply Kubernetes manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 3. Wait for deployment
kubectl rollout status deployment/modresorts -n modresorts

# 4. Verify deployment
kubectl get pods,svc,ingress -n modresorts
```

### Verify Deployment

```bash
# Check pod status
kubectl get pods -n modresorts

# Check service
kubectl get svc -n modresorts

# Check ingress
kubectl get ingress -n modresorts

# View logs
kubectl logs -n modresorts -l app=modresorts

# Port forward for testing
kubectl port-forward -n modresorts svc/modresorts-service 8080:80

# Test application
curl http://localhost:8080/health
```

---

## Configuration Management

### Environment Variables

The application supports the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m` |
| `TZ` | Timezone | `UTC` |

### Adding External Service Connections

To connect to external services (databases, APIs, etc.), update `kubernetes/deployment.yaml`:

```yaml
env:
- name: DATABASE_URL
  value: "jdbc:postgresql://db-host:5432/modresorts"
- name: DATABASE_USERNAME
  value: "modresorts_user"
- name: DATABASE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: modresorts-secrets
      key: database-password
```

### Creating Secrets

```bash
# Create secret for sensitive data
kubectl create secret generic modresorts-secrets \
  --from-literal=database-password='your-password' \
  -n modresorts
```

### ConfigMaps

```bash
# Create ConfigMap for application configuration
kubectl create configmap modresorts-config \
  --from-file=application.properties \
  -n modresorts
```

---

## Monitoring and Health Checks

### Health Endpoints

- **Liveness Probe:** `GET /health` (checks if application is running)
- **Readiness Probe:** `GET /health` (checks if application is ready to serve traffic)

### Health Check Configuration

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 90
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

### Viewing Logs

```bash
# View logs from all pods
kubectl logs -n modresorts -l app=modresorts

# Follow logs in real-time
kubectl logs -n modresorts -l app=modresorts -f

# View logs from specific pod
kubectl logs -n modresorts <pod-name>

# View previous container logs (if crashed)
kubectl logs -n modresorts <pod-name> --previous
```

### Monitoring with Azure Monitor

```bash
# Enable Azure Monitor for AKS
az aks enable-addons \
  --resource-group modresorts-rg \
  --name modresorts-aks \
  --addons monitoring
```

---

## Troubleshooting

### Common Issues

#### 1. Pods Not Starting

```bash
# Check pod status
kubectl get pods -n modresorts

# Describe pod for events
kubectl describe pod <pod-name> -n modresorts

# Check logs
kubectl logs <pod-name> -n modresorts
```

**Common causes:**
- Image pull errors (check image URI and registry credentials)
- Resource limits too low (increase memory/CPU)
- Health check failures (increase initialDelaySeconds)

#### 2. Image Pull Errors

```bash
# Verify ACR attachment
az aks show --resource-group modresorts-rg --name modresorts-aks --query "servicePrincipalProfile"

# Re-attach ACR
az aks update --resource-group modresorts-rg --name modresorts-aks --attach-acr <acr-name>

# Or create image pull secret for Docker Hub
kubectl create secret docker-registry regcred \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=<username> \
  --docker-password=<password> \
  -n modresorts
```

#### 3. Application Not Responding

```bash
# Check if pods are ready
kubectl get pods -n modresorts

# Check service endpoints
kubectl get endpoints -n modresorts

# Test service connectivity
kubectl run -it --rm debug --image=busybox --restart=Never -n modresorts -- wget -O- http://modresorts-service/health
```

#### 4. Slow Startup / Health Check Failures

Java applications can take time to start. Adjust health check timings:

```yaml
livenessProbe:
  initialDelaySeconds: 120  # Increase from 90
readinessProbe:
  initialDelaySeconds: 90   # Increase from 60
```

#### 5. Out of Memory Errors

```bash
# Check pod resource usage
kubectl top pods -n modresorts

# Increase memory limits in deployment.yaml
resources:
  limits:
    memory: "2Gi"  # Increase from 1Gi
```

### Debugging Commands

```bash
# Execute shell in running pod
kubectl exec -it <pod-name> -n modresorts -- /bin/bash

# Check Tomcat logs inside container
kubectl exec <pod-name> -n modresorts -- cat /usr/local/tomcat/logs/catalina.out

# Port forward for direct access
kubectl port-forward -n modresorts <pod-name> 8080:8080

# Get all resources in namespace
kubectl get all -n modresorts

# Describe deployment
kubectl describe deployment modresorts -n modresorts
```

---

## Security Considerations

### 1. Non-Root User

The Dockerfile creates and uses a non-root user (`tomcat`) for running the application.

### 2. Image Scanning

```bash
# Scan image for vulnerabilities (using Azure ACR)
az acr task run --registry <acr-name> --cmd "scan <image-name>:<tag>"
```

### 3. Network Policies

Implement network policies to restrict pod-to-pod communication:

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
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 8080
```

### 4. Secrets Management

- Use Kubernetes Secrets for sensitive data
- Consider Azure Key Vault integration for production
- Never commit secrets to version control

### 5. RBAC

```bash
# Create service account with limited permissions
kubectl create serviceaccount modresorts-sa -n modresorts

# Create role and role binding
kubectl create role modresorts-role --verb=get,list --resource=pods -n modresorts
kubectl create rolebinding modresorts-binding --role=modresorts-role --serviceaccount=modresorts:modresorts-sa -n modresorts
```

---

## Scaling and Performance

### Horizontal Pod Autoscaling (HPA)

```bash
# Create HPA based on CPU usage
kubectl autoscale deployment modresorts \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n modresorts

# View HPA status
kubectl get hpa -n modresorts
```

### Manual Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment modresorts --replicas=5 -n modresorts

# Verify scaling
kubectl get pods -n modresorts
```

### Resource Optimization

**Current resource allocation:**
```yaml
resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
  limits:
    cpu: "500m"
    memory: "1Gi"
```

**Adjust based on monitoring:**
- Monitor actual usage: `kubectl top pods -n modresorts`
- Adjust requests to match average usage
- Set limits 20-30% above peak usage

### JVM Tuning

Optimize JVM settings in `deployment.yaml`:

```yaml
env:
- name: JAVA_OPTS
  value: "-Xmx768m -Xms512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/modresorts modresorts=<new-image> -n modresorts

# Monitor rollout
kubectl rollout status deployment/modresorts -n modresorts

# Rollback if needed
kubectl rollout undo deployment/modresorts -n modresorts
```

---

## Additional Resources

### Useful Commands Reference

```bash
# View all resources
kubectl get all -n modresorts

# Delete all resources
kubectl delete namespace modresorts

# View events
kubectl get events -n modresorts --sort-by='.lastTimestamp'

# Get pod YAML
kubectl get pod <pod-name> -n modresorts -o yaml

# Edit deployment
kubectl edit deployment modresorts -n modresorts

# Restart deployment
kubectl rollout restart deployment/modresorts -n modresorts
```

### Azure AKS Commands

```bash
# List AKS clusters
az aks list --output table

# Get AKS credentials
az aks get-credentials --resource-group <rg> --name <cluster>

# Upgrade AKS
az aks upgrade --resource-group <rg> --name <cluster> --kubernetes-version <version>

# Scale node pool
az aks scale --resource-group <rg> --name <cluster> --node-count 3
```

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update base images regularly**
   - Rebuild with latest security patches
   - Test thoroughly before deploying

2. **Monitor resource usage**
   - Review metrics weekly
   - Adjust resource limits as needed

3. **Review logs**
   - Check for errors and warnings
   - Set up log aggregation (Azure Monitor, ELK)

4. **Backup configurations**
   - Export Kubernetes manifests
   - Version control all configuration files

5. **Security updates**
   - Keep Kubernetes version up to date
   - Scan images for vulnerabilities
   - Rotate secrets regularly

---

## Conclusion

This deployment guide provides comprehensive instructions for containerizing and deploying the ModResorts application to Azure AKS. Follow the steps carefully and refer to the troubleshooting section for common issues.

For production deployments, ensure you:
- Use proper secrets management
- Implement monitoring and alerting
- Set up CI/CD pipelines
- Configure backup and disaster recovery
- Follow security best practices

**Next Steps:**
1. Complete local testing with Docker Compose
2. Build and push Docker image
3. Deploy to AKS development environment
4. Perform integration testing
5. Deploy to production with proper change management

For questions or issues, refer to the project documentation or contact the development team.
