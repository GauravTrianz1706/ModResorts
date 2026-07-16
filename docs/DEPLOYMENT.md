# ModResorts – Deployment Guide

## Overview

This guide covers building, containerizing, and deploying the **ModResorts** Java EE web application to **Azure Kubernetes Service (AKS)**.

- **Application**: ModResorts (modresorts)
- **Version**: 2.0.0
- **Build Tool**: Maven 3.x
- **Java Version**: Java 8
- **Package Type**: WAR
- **Runtime**: Open Liberty (on `eclipse-temurin:8-jre`)
- **Context Root**: `/resorts`
- **Application Port**: `9080`
- **Health Endpoint**: `GET /resorts/health`

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [Azure AKS Prerequisites](#azure-aks-prerequisites)
6. [AKS Cluster Setup](#aks-cluster-setup)
7. [Kubernetes Deployment](#kubernetes-deployment)
8. [Configuration Management](#configuration-management)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)

---

## Prerequisites

### Local Development
- **Docker** 20.10+ and **Docker Compose** v2+
- **Java 8 JDK** (for local builds outside Docker)
- **Maven 3.8+** (for local builds outside Docker)

### Azure AKS Deployment
- **Azure CLI** (`az`) 2.40+
- **kubectl** 1.25+
- **Azure Subscription** with permissions to create AKS clusters and ACR registries
- **Azure Container Registry (ACR)** or Docker Hub account

---

## Project Structure

```
BE/
├── Dockerfile                  # Multi-stage build (Maven builder + eclipse-temurin:8-jre runtime)
├── docker-compose.yml          # Local development compose file
├── .dockerignore               # Files excluded from Docker build context
├── docker/
│   └── server.xml              # Open Liberty server configuration
├── kubernetes/
│   ├── namespace.yaml          # Kubernetes namespace
│   ├── deployment.yaml         # Application deployment (2 replicas)
│   ├── service.yaml            # ClusterIP service (port 80 → 9080)
│   └── ingress.yaml            # Azure Application Gateway ingress
├── scripts/
│   ├── build-push.sh           # Linux/macOS: build & push Docker image
│   ├── build-push.bat          # Windows: build & push Docker image
│   ├── deploy-image.sh         # Linux/macOS: deploy to AKS
│   └── deploy-image.bat        # Windows: deploy to AKS
├── src/                        # Java source code
├── WebContent/                 # Web resources (HTML, JSP, JS, CSS)
└── pom.xml                     # Maven build descriptor
```

---

## Local Development with Docker Compose

### 1. Build and Start

```bash
# From the BE/ directory
docker compose up --build
```

The application will be available at: `http://localhost:9080/resorts/`

### 2. Environment Variables

Create a `.env` file in the `BE/` directory to override defaults:

```env
WEATHER_API_KEY=your_wunderground_api_key
JNDI_FACTORY=com.sun.jndi.fscontext.RefFSContextFactory
JNDI_PROVIDER_URL=
SERVER_DISPLAY_NAME=modresorts-local
SERVER_FULL_NAME=modresorts-local
```

### 3. Verify Health

```bash
curl http://localhost:9080/resorts/health
# Expected: {"status":"UP","application":"modresorts"}
```

### 4. Stop

```bash
docker compose down
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

The script will prompt you to:
1. Enter an image tag (defaults to `latest`)
2. Choose registry type: **Azure ACR** or **Docker Hub**
3. Provide registry credentials

**Example session (ACR):**
```
Enter image tag (press Enter for 'latest'): v2.0.0
Select container registry:
  1. Azure Container Registry (ACR)
  2. Docker Hub
Enter choice [1 or 2]: 1
Enter ACR name (e.g. myregistry): mycompanyacr
```

---

## Azure AKS Prerequisites

### Install Azure CLI

```bash
# macOS
brew install azure-cli

# Linux
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# Windows
winget install Microsoft.AzureCLI
```

### Install kubectl

```bash
az aks install-cli
```

### Login to Azure

```bash
az login
az account set --subscription "<your-subscription-id>"
```

---

## AKS Cluster Setup

### Create Resource Group

```bash
az group create --name modresorts-rg --location eastus
```

### Create Azure Container Registry

```bash
az acr create \
  --resource-group modresorts-rg \
  --name modresortsacr \
  --sku Basic
```

### Create AKS Cluster with AGIC (Application Gateway Ingress Controller)

```bash
az aks create \
  --resource-group modresorts-rg \
  --name modresorts-aks \
  --node-count 2 \
  --node-vm-size Standard_DS2_v2 \
  --enable-addons ingress-appgw \
  --appgw-name modresorts-appgw \
  --appgw-subnet-cidr "10.225.0.0/16" \
  --attach-acr modresortsacr \
  --generate-ssh-keys
```

### Configure kubectl

```bash
az aks get-credentials \
  --resource-group modresorts-rg \
  --name modresorts-aks
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
- Azure Resource Group name
- AKS Cluster name
- Full Docker image URI (e.g., `modresortsacr.azurecr.io/modresorts:v2.0.0`)
- Application environment variables (WEATHER_API_KEY, etc.)

### Manual Deployment

If you prefer to deploy manually:

```bash
# 1. Update the image URI in deployment.yaml
sed -i 's|{{IMAGE_URI}}|modresortsacr.azurecr.io/modresorts:v2.0.0|g' kubernetes/deployment.yaml

# 2. Update environment variable placeholders
sed -i 's|{{WEATHER_API_KEY}}|your_api_key|g' kubernetes/deployment.yaml
sed -i 's|{{JNDI_FACTORY}}|com.sun.jndi.fscontext.RefFSContextFactory|g' kubernetes/deployment.yaml
sed -i 's|{{JNDI_PROVIDER_URL}}||g' kubernetes/deployment.yaml
sed -i 's|{{SERVER_DISPLAY_NAME}}|modresorts|g' kubernetes/deployment.yaml
sed -i 's|{{SERVER_FULL_NAME}}|modresorts|g' kubernetes/deployment.yaml

# 3. Apply manifests in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Wait for rollout
kubectl rollout status deployment/modresorts -n modresorts --timeout=300s

# 5. Verify
kubectl get pods,svc,ingress -n modresorts
```

### Kubernetes Manifest Descriptions

| File | Description |
|------|-------------|
| `namespace.yaml` | Creates the `modresorts` namespace |
| `deployment.yaml` | Deploys 2 replicas with liveness/readiness probes on `/resorts/health` |
| `service.yaml` | ClusterIP service mapping port 80 → container port 9080 |
| `ingress.yaml` | Azure Application Gateway ingress routing traffic to the service |

---

## Configuration Management

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WEATHER_API_KEY` | Weather Underground API key for real-time weather | *(empty – uses mock data)* |
| `JNDI_FACTORY` | JNDI initial context factory class | `com.sun.jndi.fscontext.RefFSContextFactory` |
| `JNDI_PROVIDER_URL` | JNDI provider URL | *(empty)* |
| `SERVER_DISPLAY_NAME` | Server display name for logging | `modresorts` |
| `SERVER_FULL_NAME` | Server full name for logging | `modresorts` |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |
| `TZ` | Timezone | `UTC` |

### Using Kubernetes Secrets for Sensitive Values

For sensitive values like `WEATHER_API_KEY`, use Kubernetes Secrets:

```bash
kubectl create secret generic modresorts-secrets \
  --from-literal=WEATHER_API_KEY=your_api_key \
  -n modresorts
```

Then reference in `deployment.yaml`:

```yaml
env:
  - name: WEATHER_API_KEY
    valueFrom:
      secretKeyRef:
        name: modresorts-secrets
        key: WEATHER_API_KEY
```

---

## Scaling and Management

### Manual Scaling

```bash
kubectl scale deployment modresorts --replicas=3 -n modresorts
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment modresorts \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n modresorts
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/modresorts \
  modresorts=modresortsacr.azurecr.io/modresorts:v2.1.0 \
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
kubectl logs <pod-name> -n modresorts --previous  # crashed pod logs
```

### Health Check Failures

The application health endpoint is `GET /resorts/health`.

```bash
# Port-forward to test locally
kubectl port-forward deployment/modresorts 9080:9080 -n modresorts

# In another terminal
curl http://localhost:9080/resorts/health
# Expected: {"status":"UP","application":"modresorts"}
```

If health checks fail:
- Increase `initialDelaySeconds` in `deployment.yaml` (Liberty startup can take 45-60s)
- Check Liberty logs: `kubectl logs <pod-name> -n modresorts`

### Image Pull Errors

```bash
# Verify ACR attachment to AKS
az aks check-acr \
  --resource-group modresorts-rg \
  --name modresorts-aks \
  --acr modresortsacr

# Re-attach ACR if needed
az aks update \
  --resource-group modresorts-rg \
  --name modresorts-aks \
  --attach-acr modresortsacr
```

### Ingress Not Accessible

```bash
# Check ingress status
kubectl describe ingress modresorts-ingress -n modresorts

# Get ingress IP
kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].ip}'

# Check Application Gateway health
az network application-gateway show-backend-health \
  --resource-group modresorts-rg \
  --name modresorts-appgw
```

### JVM Memory Issues

If pods are OOMKilled, increase memory limits in `deployment.yaml`:

```yaml
resources:
  requests:
    memory: "768Mi"
  limits:
    memory: "1536Mi"
```

And update `JAVA_OPTS`:
```yaml
- name: JAVA_OPTS
  value: "-Xmx1g -Xms512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

---

## Security Considerations

1. **Non-root container**: The application runs as a non-root user (`appuser`) inside the container.
2. **Secrets management**: Store sensitive values (API keys, passwords) in Kubernetes Secrets, not plain environment variables.
3. **Network policies**: Consider adding Kubernetes NetworkPolicies to restrict pod-to-pod communication.
4. **TLS/HTTPS**: Configure TLS termination at the Application Gateway level for production deployments.
5. **Image scanning**: Enable Azure Defender for Containers or use `az acr task` to scan images for vulnerabilities.
6. **RBAC**: Use Kubernetes RBAC to restrict access to the `modresorts` namespace.
7. **Application security**: The `web.xml` security constraints are commented out for demo purposes. Enable them for production by uncommenting the `<security-constraint>` blocks.

---

## Java / Open Liberty Specific Notes

- **Liberty startup time**: Open Liberty typically takes 30-60 seconds to start. The `initialDelaySeconds` in health probes is set accordingly.
- **JVM container support**: `-XX:+UseContainerSupport` ensures the JVM respects container memory limits rather than host memory.
- **WAR context root**: The application is deployed at `/resorts` (configured in `docker/server.xml` and `WebContent/WEB-INF/ibm-web-ext.xml`).
- **CDI**: The application uses CDI (`@ApplicationScoped`) beans. Ensure the Liberty `cdi-1.2` feature is enabled (already configured in `docker/server.xml`).
- **Weather API**: Without a `WEATHER_API_KEY`, the application serves static weather data from `src/main/resources/`. Set the key to enable real-time weather from Weather Underground.
