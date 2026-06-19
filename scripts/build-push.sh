#!/bin/bash

set -e

echo "=========================================="
echo "ModResorts - Docker Build and Push Script"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="modresorts"

# Sanitize image name (lowercase, hyphenate spaces/special chars, trim hyphens)
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "Project: $PROJECT_NAME"
echo "Image Name: $IMAGE_NAME"
echo ""

# Prompt for registry type
echo "Select Docker Registry:"
echo "1. Azure Container Registry (ACR)"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    # Azure ACR
    echo ""
    echo "=== Azure Container Registry Configuration ==="
    read -p "Enter ACR name (e.g., myregistry): " ACR_NAME
    read -p "Enter ACR login server (e.g., myregistry.azurecr.io): " ACR_LOGIN_SERVER
    
    # Prompt for image tag
    read -p "Enter image tag (default: latest): " IMAGE_TAG
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    # Sanitize tag
    IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')
    [ -z "$IMAGE_TAG" ] && IMAGE_TAG="latest"
    
    FULL_IMAGE_NAME="${ACR_LOGIN_SERVER}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "Logging into Azure Container Registry..."
    az acr login --name "$ACR_NAME"
    
    if [ $? -ne 0 ]; then
        echo "ERROR: ACR login failed. Please check your Azure CLI configuration."
        exit 1
    fi
    
elif [ "$REGISTRY_CHOICE" == "2" ]; then
    # Docker Hub
    echo ""
    echo "=== Docker Hub Configuration ==="
    read -p "Enter Docker Hub username: " DOCKER_USERNAME
    read -sp "Enter Docker Hub password or access token: " DOCKER_PASSWORD
    echo ""
    
    # Prompt for image tag
    read -p "Enter image tag (default: latest): " IMAGE_TAG
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    # Sanitize tag
    IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')
    [ -z "$IMAGE_TAG" ] && IMAGE_TAG="latest"
    
    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "Logging into Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker Hub login failed. Please check your credentials."
        exit 1
    fi
    
else
    echo "ERROR: Invalid choice. Please select 1 or 2."
    exit 1
fi

echo ""
echo "=========================================="
echo "Building Docker Image"
echo "=========================================="
echo "Image: $FULL_IMAGE_NAME"
echo ""

# Build Docker image
docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo "ERROR: Docker build failed."
    exit 1
fi

echo ""
echo "=========================================="
echo "Pushing Docker Image"
echo "=========================================="
echo ""

# Push Docker image
docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed."
    exit 1
fi

echo ""
echo "=========================================="
echo "Build and Push Completed Successfully!"
echo "=========================================="
echo "Image: $FULL_IMAGE_NAME"
echo ""
echo "Next steps:"
echo "1. Update kubernetes/deployment.yaml with the image URI"
echo "2. Run deploy-image.sh to deploy to Azure AKS"
echo ""
