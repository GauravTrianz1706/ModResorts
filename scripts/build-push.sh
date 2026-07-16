#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="modresorts"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "============================================"
echo "  ModResorts - Docker Build & Push Script"
echo "============================================"
echo ""

# Prompt for image tag
read -p "Enter image tag (press Enter for 'latest'): " IMAGE_TAG_INPUT
IMAGE_TAG=$(echo "$IMAGE_TAG_INPUT" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-*//;s/-*$//')
if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="latest"
fi
echo "Using image tag: $IMAGE_TAG"
echo ""

# Prompt for registry type
echo "Select container registry:"
echo "  1. Azure Container Registry (ACR)"
echo "  2. Docker Hub"
read -p "Enter choice [1 or 2]: " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" = "1" ]; then
  # Azure ACR
  read -p "Enter ACR name (e.g. myregistry): " ACR_NAME
  if [ -z "$ACR_NAME" ]; then
    echo "ERROR: ACR name cannot be empty." >&2
    exit 1
  fi
  REGISTRY="${ACR_NAME}.azurecr.io"
  FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

  echo ""
  echo "Logging in to Azure Container Registry: $ACR_NAME ..."
  az acr login --name "$ACR_NAME"
  if [ $? -ne 0 ]; then
    echo "ERROR: ACR login failed." >&2
    exit 1
  fi

elif [ "$REGISTRY_CHOICE" = "2" ]; then
  # Docker Hub
  read -p "Enter Docker Hub username: " DOCKER_USERNAME
  read -s -p "Enter Docker Hub password/token: " DOCKER_PASSWORD
  echo ""
  if [ -z "$DOCKER_USERNAME" ] || [ -z "$DOCKER_PASSWORD" ]; then
    echo "ERROR: Docker Hub username and password cannot be empty." >&2
    exit 1
  fi
  REGISTRY="docker.io"
  FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"

  echo ""
  echo "Logging in to Docker Hub ..."
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
  if [ $? -ne 0 ]; then
    echo "ERROR: Docker Hub login failed." >&2
    exit 1
  fi

else
  echo "ERROR: Invalid choice. Please enter 1 or 2." >&2
  exit 1
fi

echo ""
echo "Building Docker image: $FULL_IMAGE_NAME ..."
docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .
if [ $? -ne 0 ]; then
  echo "ERROR: Docker build failed." >&2
  exit 1
fi
echo "Docker build succeeded."

echo ""
echo "Pushing image: $FULL_IMAGE_NAME ..."
docker push "$FULL_IMAGE_NAME"
if [ $? -ne 0 ]; then
  echo "ERROR: Docker push failed." >&2
  exit 1
fi

echo ""
echo "============================================"
echo "  Image pushed successfully!"
echo "  $FULL_IMAGE_NAME"
echo "============================================"
