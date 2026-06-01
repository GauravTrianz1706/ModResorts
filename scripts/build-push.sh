#!/bin/bash
# =============================================================================
# build-push.sh - Build and Push Docker Image for ModResorts
# Supports: AWS ECR and Docker Hub registries
# Usage: ./scripts/build-push.sh
# Run from repository root directory
# =============================================================================

set -e
set -o pipefail

PROJECT_NAME="modresorts"
DOCKERFILE_PATH="Dockerfile"

echo "=============================================="
echo "  ModResorts - Docker Build & Push Script"
echo "=============================================="
echo ""

# Sanitize image name: lowercase, replace non-alphanumeric with hyphens, trim hyphens
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "Image name: $IMAGE_NAME"
echo ""

# Prompt for image tag
read -p "Enter image tag [latest]: " IMAGE_TAG_INPUT
IMAGE_TAG=$(echo "${IMAGE_TAG_INPUT:-latest}" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-*//;s/-*$//')
if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="latest"
fi
echo "Image tag: $IMAGE_TAG"
echo ""

# Select registry type
echo "Select container registry:"
echo "  1. AWS ECR (Elastic Container Registry)"
echo "  2. Docker Hub"
echo ""
read -p "Enter choice [1 or 2]: " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" = "1" ]; then
  # ---- AWS ECR ----
  echo ""
  echo "--- AWS ECR Configuration ---"
  read -p "Enter AWS Region [us-east-1]: " AWS_REGION_INPUT
  AWS_REGION="${AWS_REGION_INPUT:-us-east-1}"

  read -p "Enter ECR repository name [$IMAGE_NAME]: " ECR_REPO_INPUT
  ECR_REPO="${ECR_REPO_INPUT:-$IMAGE_NAME}"

  echo ""
  echo "Retrieving AWS Account ID..."
  ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
  if [ -z "$ACCOUNT_ID" ]; then
    echo "ERROR: Could not retrieve AWS Account ID. Ensure AWS CLI is configured."
    exit 1
  fi
  echo "AWS Account ID: $ACCOUNT_ID"

  REGISTRY_URL="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

  echo ""
  echo "Logging in to AWS ECR..."
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
  if [ $? -ne 0 ]; then
    echo "ERROR: ECR login failed."
    exit 1
  fi
  echo "ECR login successful."

  # Auto-create ECR repository if it doesn't exist
  echo ""
  echo "Checking if ECR repository '$ECR_REPO' exists..."
  aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo "Repository not found. Creating ECR repository '$ECR_REPO'..."
    aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
    echo "ECR repository created successfully."
  }

elif [ "$REGISTRY_CHOICE" = "2" ]; then
  # ---- Docker Hub ----
  echo ""
  echo "--- Docker Hub Configuration ---"
  read -p "Enter Docker Hub username: " DOCKER_USERNAME
  read -s -p "Enter Docker Hub password/token: " DOCKER_PASSWORD
  echo ""
  read -p "Enter Docker Hub namespace/org [$DOCKER_USERNAME]: " DOCKER_NAMESPACE_INPUT
  DOCKER_NAMESPACE="${DOCKER_NAMESPACE_INPUT:-$DOCKER_USERNAME}"

  FULL_IMAGE_NAME="${DOCKER_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

  echo ""
  echo "Logging in to Docker Hub..."
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
  if [ $? -ne 0 ]; then
    echo "ERROR: Docker Hub login failed."
    exit 1
  fi
  echo "Docker Hub login successful."

else
  echo "ERROR: Invalid registry choice. Please enter 1 or 2."
  exit 1
fi

echo ""
echo "=============================================="
echo "  Building Docker Image"
echo "=============================================="
echo "Image: $FULL_IMAGE_NAME"
echo "Dockerfile: $DOCKERFILE_PATH"
echo "Build context: . (repository root)"
echo ""

docker build -f "$DOCKERFILE_PATH" -t "$FULL_IMAGE_NAME" .
if [ $? -ne 0 ]; then
  echo "ERROR: Docker build failed."
  exit 1
fi
echo ""
echo "Docker build completed successfully."

# Also tag as latest if a specific tag was provided
if [ "$IMAGE_TAG" != "latest" ]; then
  if [ "$REGISTRY_CHOICE" = "1" ]; then
    LATEST_IMAGE="${REGISTRY_URL}/${ECR_REPO}:latest"
  else
    LATEST_IMAGE="${DOCKER_NAMESPACE}/${IMAGE_NAME}:latest"
  fi
  docker tag "$FULL_IMAGE_NAME" "$LATEST_IMAGE"
  echo "Tagged as: $LATEST_IMAGE"
fi

echo ""
echo "=============================================="
echo "  Pushing Docker Image"
echo "=============================================="
echo "Pushing: $FULL_IMAGE_NAME"
docker push "$FULL_IMAGE_NAME"
if [ $? -ne 0 ]; then
  echo "ERROR: Docker push failed."
  exit 1
fi

if [ "$IMAGE_TAG" != "latest" ]; then
  echo "Pushing: $LATEST_IMAGE"
  docker push "$LATEST_IMAGE"
fi

echo ""
echo "=============================================="
echo "  Build & Push Complete!"
echo "=============================================="
echo "Image URI: $FULL_IMAGE_NAME"
echo ""
echo "Use this image URI in your ECS deployment:"
echo "  ./scripts/deploy-image.sh"
echo ""
