#!/bin/bash

# Deploy ModResorts Application to AWS EKS
# This script configures kubectl and deploys the application to EKS cluster

set -e
set -o pipefail

echo "=========================================="
echo "ModResorts - AWS EKS Deployment Script"
echo "=========================================="
echo ""

# Prompt for AWS region
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "Error: AWS region is required"
    exit 1
fi

# Prompt for EKS cluster name
read -p "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "Error: EKS cluster name is required"
    exit 1
fi

# Prompt for Docker image URI
echo ""
echo "Enter the full Docker image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest)"
read -p "Docker image URI: " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Error: Docker image URI is required"
    exit 1
fi

echo ""
echo "=========================================="
echo "Configuring kubectl for EKS cluster"
echo "=========================================="

# Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME

if [ $? -ne 0 ]; then
    echo "Error: Failed to configure kubectl for EKS cluster"
    exit 1
fi

echo "kubectl configured successfully"

# Verify cluster connectivity
echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info

if [ $? -ne 0 ]; then
    echo "Error: Cannot connect to EKS cluster"
    exit 1
fi

echo ""
echo "=========================================="
echo "Updating Kubernetes manifests"
echo "=========================================="

# Create temporary directory for modified manifests
TEMP_DIR=$(mktemp -d)
cp -r kubernetes/* $TEMP_DIR/

# Update deployment.yaml with actual image URI
sed -i.bak "s|{{IMAGE_URI}}|$IMAGE_URI|g" $TEMP_DIR/deployment.yaml

echo "Manifests updated with image URI: $IMAGE_URI"

echo ""
echo "=========================================="
echo "Deploying to AWS EKS"
echo "=========================================="

# Apply namespace
echo "Creating namespace..."
kubectl apply -f $TEMP_DIR/namespace.yaml

# Apply deployment
echo "Deploying application..."
kubectl apply -f $TEMP_DIR/deployment.yaml

# Apply service
echo "Creating service..."
kubectl apply -f $TEMP_DIR/service.yaml

# Apply ingress
echo "Creating ingress..."
kubectl apply -f $TEMP_DIR/ingress.yaml

echo ""
echo "=========================================="
echo "Waiting for deployment to complete"
echo "=========================================="

# Wait for deployment rollout
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if [ $? -ne 0 ]; then
    echo "Error: Deployment rollout failed"
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

# Display deployment status
kubectl get pods -n modresorts
echo ""
kubectl get svc -n modresorts
echo ""
kubectl get ingress -n modresorts

echo ""
echo "=========================================="
echo "Deployment Completed Successfully!"
echo "=========================================="

# Get ingress URL
INGRESS_URL=$(kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

if [ -n "$INGRESS_URL" ]; then
    echo ""
    echo "Application URL: http://$INGRESS_URL"
    echo ""
    echo "Note: It may take a few minutes for the Load Balancer to become active"
    echo "Health check endpoint: http://$INGRESS_URL/health"
else
    echo ""
    echo "Ingress is being provisioned. Run the following command to get the URL:"
    echo "kubectl get ingress modresorts-ingress -n modresorts"
fi

echo ""
echo "Useful commands:"
echo "  View pods:        kubectl get pods -n modresorts"
echo "  View logs:        kubectl logs -n modresorts -l app=modresorts"
echo "  Describe pod:     kubectl describe pod <pod-name> -n modresorts"
echo "  Scale deployment: kubectl scale deployment modresorts -n modresorts --replicas=3"
echo "  Delete deployment: kubectl delete -f kubernetes/"
echo ""

# Cleanup temporary directory
rm -rf $TEMP_DIR

echo "=========================================="
