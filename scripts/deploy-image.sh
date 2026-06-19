#!/bin/bash

set -e
set -o pipefail

echo "=========================================="
echo "ModResorts - Azure AKS Deployment Script"
echo "=========================================="
echo ""

# Prompt for Azure AKS configuration
echo "=== Azure AKS Configuration ==="
read -p "Enter Azure Resource Group name: " RESOURCE_GROUP
read -p "Enter AKS Cluster name: " CLUSTER_NAME

if [ -z "$RESOURCE_GROUP" ] || [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: Resource Group and Cluster Name are required."
    exit 1
fi

# Prompt for Docker image URI
echo ""
read -p "Enter Docker image URI (e.g., myregistry.azurecr.io/modresorts:latest): " IMAGE_URI

if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker image URI is required."
    exit 1
fi

echo ""
echo "=========================================="
echo "Configuring kubectl for AKS"
echo "=========================================="
echo ""

# Get AKS credentials
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to get AKS credentials. Please check your Azure configuration."
    exit 1
fi

# Verify cluster connectivity
echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info

if [ $? -ne 0 ]; then
    echo "ERROR: Cannot connect to Kubernetes cluster."
    exit 1
fi

echo ""
echo "=========================================="
echo "Updating Kubernetes Manifests"
echo "=========================================="
echo ""

# Create temporary directory for updated manifests
TEMP_DIR=$(mktemp -d)
cp -r kubernetes/* "$TEMP_DIR/"

# Update image URI in deployment manifest
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"

echo "Manifests updated with image: $IMAGE_URI"

echo ""
echo "=========================================="
echo "Deploying to Azure AKS"
echo "=========================================="
echo ""

# Apply namespace
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

# Apply deployment
echo "Deploying application..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

# Apply service
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

# Apply ingress
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

echo ""
echo "=========================================="
echo "Waiting for Deployment Rollout"
echo "=========================================="
echo ""

# Wait for deployment to complete
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed or timed out."
    echo ""
    echo "Checking pod status..."
    kubectl get pods -n modresorts
    echo ""
    echo "Checking pod logs..."
    kubectl logs -n modresorts -l app=modresorts --tail=50
    exit 1
fi

echo ""
echo "=========================================="
echo "Deployment Status"
echo "=========================================="
echo ""

# Display deployment status
kubectl get pods,svc,ingress -n modresorts

echo ""
echo "=========================================="
echo "Deployment Completed Successfully!"
echo "=========================================="
echo ""
echo "Application Details:"
echo "  Namespace: modresorts"
echo "  Deployment: modresorts"
echo "  Service: modresorts-service"
echo "  Ingress: modresorts-ingress"
echo ""
echo "To access the application:"
echo "  1. Update your DNS to point modresorts.example.com to the ingress IP"
echo "  2. Or use port-forward for testing:"
echo "     kubectl port-forward -n modresorts svc/modresorts-service 8080:80"
echo "  3. Access: http://localhost:8080"
echo ""
echo "Useful commands:"
echo "  View pods: kubectl get pods -n modresorts"
echo "  View logs: kubectl logs -n modresorts -l app=modresorts"
echo "  Describe deployment: kubectl describe deployment modresorts -n modresorts"
echo "  Scale deployment: kubectl scale deployment modresorts -n modresorts --replicas=3"
echo ""

# Cleanup temporary directory
rm -rf "$TEMP_DIR"
