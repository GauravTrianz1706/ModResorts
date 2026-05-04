#!/bin/bash

# Build and Push Script for ModResorts Application
# Supports AWS ECR and Docker Hub registries

set -e  # Exit on error
set -o pipefail  # Exit on pipe failure

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project configuration
PROJECT_NAME="modresorts"
DEFAULT_TAG="latest"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}ModResorts Docker Build and Push Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Sanitize image name: lowercase, replace non-alphanumeric with hyphens, trim hyphens
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo -e "${GREEN}Project: ${IMAGE_NAME}${NC}"
echo ""

# Prompt for image tag
echo -e "${YELLOW}Enter image tag (default: latest):${NC}"
read -r IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-$DEFAULT_TAG}

# Sanitize tag: lowercase, replace non-alphanumeric with hyphens, trim hyphens
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')

# Default to 'latest' if tag is empty after sanitization
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG="latest"
fi

echo -e "${GREEN}Using tag: ${IMAGE_TAG}${NC}"
echo ""

# Registry selection
echo -e "${YELLOW}Select container registry:${NC}"
echo "1. AWS ECR (Elastic Container Registry)"
echo "2. Docker Hub"
echo ""
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

case $REGISTRY_CHOICE in
    1)
        echo -e "${BLUE}Selected: AWS ECR${NC}"
        echo ""
        
        # AWS ECR Configuration
        read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
        read -p "Enter AWS Account ID: " AWS_ACCOUNT_ID
        read -p "Enter ECR Repository Name (default: ${IMAGE_NAME}): " ECR_REPO
        ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
        
        REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"
        
        echo ""
        echo -e "${GREEN}Registry URL: ${REGISTRY_URL}${NC}"
        echo -e "${GREEN}Full Image Name: ${FULL_IMAGE_NAME}${NC}"
        echo ""
        
        # Authenticate with ECR
        echo -e "${BLUE}Authenticating with AWS ECR...${NC}"
        aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}ECR authentication failed. Please check your AWS credentials.${NC}"
            exit 1
        fi
        
        echo -e "${GREEN}ECR authentication successful!${NC}"
        echo ""
        
        # Check if ECR repository exists, create if not
        echo -e "${BLUE}Checking ECR repository...${NC}"
        aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || {
            echo -e "${YELLOW}Repository does not exist. Creating ECR repository: ${ECR_REPO}${NC}"
            aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
            echo -e "${GREEN}ECR repository created successfully!${NC}"
        }
        echo ""
        ;;
        
    2)
        echo -e "${BLUE}Selected: Docker Hub${NC}"
        echo ""
        
        # Docker Hub Configuration
        read -p "Enter Docker Hub username: " DOCKER_USERNAME
        read -sp "Enter Docker Hub password or access token: " DOCKER_PASSWORD
        echo ""
        
        FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
        
        echo ""
        echo -e "${GREEN}Full Image Name: ${FULL_IMAGE_NAME}${NC}"
        echo ""
        
        # Authenticate with Docker Hub
        echo -e "${BLUE}Authenticating with Docker Hub...${NC}"
        echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}Docker Hub authentication failed. Please check your credentials.${NC}"
            exit 1
        fi
        
        echo -e "${GREEN}Docker Hub authentication successful!${NC}"
        echo ""
        ;;
        
    *)
        echo -e "${RED}Invalid choice. Exiting.${NC}"
        exit 1
        ;;
esac

# Build Docker image
echo -e "${BLUE}Building Docker image...${NC}"
echo -e "${YELLOW}Image: ${FULL_IMAGE_NAME}${NC}"
echo ""

docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker build failed. Please check the Dockerfile and build context.${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Docker image built successfully!${NC}"
echo ""

# Push Docker image
echo -e "${BLUE}Pushing Docker image to registry...${NC}"
docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker push failed. Please check your network connection and registry credentials.${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Docker image pushed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}Image: ${FULL_IMAGE_NAME}${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Update your deployment manifests with the image URI"
echo "2. Deploy to your target environment (AWS ECS, Kubernetes, etc.)"
echo ""
