#!/bin/bash

# Build and Push Docker Image Script for ModResorts
# Supports AWS ECR and Docker Hub registries

set -e
set -o pipefail

echo "=========================================="
echo "ModResorts - Docker Build and Push Script"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="modresorts"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Prompt for image tag
read -p "Enter image tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')

echo ""
echo "Select Docker Registry:"
echo "1. AWS ECR (Elastic Container Registry)"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    echo ""
    echo "=== AWS ECR Configuration ==="
    
    # Prompt for AWS region
    read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
    if [ -z "$AWS_REGION" ]; then
        echo "Error: AWS region is required"
        exit 1
    fi
    
    # Prompt for AWS account ID
    read -p "Enter AWS account ID: " AWS_ACCOUNT_ID
    if [ -z "$AWS_ACCOUNT_ID" ]; then
        echo "Error: AWS account ID is required"
        exit 1
    fi
    
    # Prompt for ECR repository name
    read -p "Enter ECR repository name (default: $IMAGE_NAME): " ECR_REPO
    ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
    
    # Construct ECR registry URL
    REGISTRY_URL="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
    FULL_IMAGE_NAME="$REGISTRY_URL/$ECR_REPO:$IMAGE_TAG"
    
    echo ""
    echo "Authenticating with AWS ECR..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $REGISTRY_URL
    
    if [ $? -ne 0 ]; then
        echo "Error: ECR authentication failed"
        exit 1
    fi
    
    echo "ECR authentication successful"
    
    # Check if repository exists, create if not
    echo "Checking if ECR repository exists..."
    aws ecr describe-repositories --repository-names $ECR_REPO --region $AWS_REGION >/dev/null 2>&1 || {
        echo "Repository does not exist. Creating ECR repository..."
        aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION
        echo "ECR repository created successfully"
    }
    
elif [ "$REGISTRY_CHOICE" == "2" ]; then
    echo ""
    echo "=== Docker Hub Configuration ==="
    
    # Prompt for Docker Hub username
    read -p "Enter Docker Hub username: " DOCKER_USERNAME
    if [ -z "$DOCKER_USERNAME" ]; then
        echo "Error: Docker Hub username is required"
        exit 1
    fi
    
    # Prompt for Docker Hub password
    read -sp "Enter Docker Hub password: " DOCKER_PASSWORD
    echo ""
    if [ -z "$DOCKER_PASSWORD" ]; then
        echo "Error: Docker Hub password is required"
        exit 1
    fi
    
    FULL_IMAGE_NAME="$DOCKER_USERNAME/$IMAGE_NAME:$IMAGE_TAG"
    
    echo ""
    echo "Authenticating with Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username $DOCKER_USERNAME --password-stdin
    
    if [ $? -ne 0 ]; then
        echo "Error: Docker Hub authentication failed"
        exit 1
    fi
    
    echo "Docker Hub authentication successful"
    
else
    echo "Error: Invalid choice. Please select 1 or 2"
    exit 1
fi

echo ""
echo "=========================================="
echo "Building Docker image: $FULL_IMAGE_NAME"
echo "=========================================="

# Build Docker image
docker build -t $FULL_IMAGE_NAME .

if [ $? -ne 0 ]; then
    echo "Error: Docker build failed"
    exit 1
fi

echo ""
echo "Docker build completed successfully"

echo ""
echo "=========================================="
echo "Pushing Docker image to registry"
echo "=========================================="

# Push Docker image
docker push $FULL_IMAGE_NAME

if [ $? -ne 0 ]; then
    echo "Error: Docker push failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "Build and Push Completed Successfully!"
echo "=========================================="
echo "Image: $FULL_IMAGE_NAME"
echo ""
echo "Next steps:"
echo "1. Update kubernetes/deployment.yaml with image URI: $FULL_IMAGE_NAME"
echo "2. Run deploy-image.sh to deploy to AWS EKS"
echo "=========================================="
