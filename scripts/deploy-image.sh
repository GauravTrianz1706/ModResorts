#!/bin/bash
set -e
set -o pipefail

# ============================================
# Deploy to AWS EKS Script
# For ModResorts Spring Boot Application
# ============================================

echo "=========================================="
echo "ModResorts - AWS EKS Deployment Script"
echo "=========================================="
echo ""

# Prompt for AWS EKS configuration
echo "=== AWS EKS Configuration ==="
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
echo ""

# Prompt for Docker image URI
read -p "Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI
echo ""

# Prompt for environment variables
echo "=== Application Configuration ==="
echo "Enter values for environment variables (press Enter to skip):"
echo ""

read -p "Database URL [jdbc:postgresql://localhost:5432/modresorts]: " DB_URL
DB_URL=${DB_URL:-jdbc:postgresql://localhost:5432/modresorts}

read -p "Database Username [dbuser]: " DB_USERNAME
DB_USERNAME=${DB_USERNAME:-dbuser}

read -sp "Database Password: " DB_PASSWORD
echo ""

read -p "Database Driver [org.postgresql.Driver]: " DB_DRIVER
DB_DRIVER=${DB_DRIVER:-org.postgresql.Driver}

read -p "Redis Host [localhost]: " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-localhost}

read -p "Redis Port [6379]: " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -sp "Redis Password (optional): " REDIS_PASSWORD
echo ""

read -p "Redis Timeout [2000]: " REDIS_TIMEOUT
REDIS_TIMEOUT=${REDIS_TIMEOUT:-2000}

read -p "Service Registry URL [http://service-registry:8080]: " SERVICE_REGISTRY_URL
SERVICE_REGISTRY_URL=${SERVICE_REGISTRY_URL:-http://service-registry:8080}

read -p "AWS CloudMap Namespace (optional): " AWS_CLOUDMAP_NAMESPACE

echo ""
echo "=========================================="
echo "Configuring kubectl for EKS"
echo "=========================================="
echo ""

# Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for EKS cluster"
    exit 1
fi

# Verify cluster connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || {
    echo "ERROR: Cannot connect to EKS cluster"
    exit 1
}

echo ""
echo "=========================================="
echo "Updating Kubernetes Manifests"
echo "=========================================="
echo ""

# Create temporary directory for processed manifests
TEMP_DIR=$(mktemp -d)
cp -r kubernetes/* "$TEMP_DIR/"

# Update manifests with actual values using pipe delimiter
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_URL}}|$DB_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_USERNAME}}|$DB_USERNAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_DRIVER}}|$DB_DRIVER|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_TIMEOUT}}|$REDIS_TIMEOUT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SERVICE_REGISTRY_URL}}|$SERVICE_REGISTRY_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{AWS_CLOUDMAP_NAMESPACE}}|$AWS_CLOUDMAP_NAMESPACE|g" "$TEMP_DIR/deployment.yaml"

echo "Manifests updated successfully"
echo ""

echo "=========================================="
echo "Deploying to AWS EKS"
echo "=========================================="
echo ""

# Apply Kubernetes manifests in order
echo "Creating namespace..."
kubectl apply -f kubernetes/namespace.yaml

echo "Deploying application..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

echo "Creating service..."
kubectl apply -f kubernetes/service.yaml

echo "Creating ingress..."
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "=========================================="
echo "Waiting for Deployment Rollout"
echo "=========================================="
echo ""

# Wait for deployment to complete
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed"
    echo "Checking pod status..."
    kubectl get pods -n modresorts
    kubectl describe pods -n modresorts
    rm -rf "$TEMP_DIR"
    exit 1
fi

echo ""
echo "=========================================="
echo "Verifying Deployment"
echo "=========================================="
echo ""

# Verify resources
kubectl get pods,svc,ingress -n modresorts

echo ""
echo "=========================================="
echo "Deployment Completed Successfully!"
echo "=========================================="
echo ""

# Get ingress URL
INGRESS_URL=$(kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Pending...")

echo "Application Details:"
echo "  Namespace: modresorts"
echo "  Deployment: modresorts"
echo "  Service: modresorts-service"
echo "  Ingress URL: $INGRESS_URL"
echo ""
echo "Health Check: http://$INGRESS_URL/actuator/health"
echo ""
echo "To view logs:"
echo "  kubectl logs -f deployment/modresorts -n modresorts"
echo ""
echo "To scale deployment:"
echo "  kubectl scale deployment/modresorts -n modresorts --replicas=3"
echo ""
echo "To rollback deployment:"
echo "  kubectl rollout undo deployment/modresorts -n modresorts"
echo ""

# Cleanup temporary directory
rm -rf "$TEMP_DIR"
