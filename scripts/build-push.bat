@echo off
setlocal enabledelayedexpansion

REM ModResorts - Docker Build and Push Script for Windows
REM This script builds the Docker image and pushes it to a container registry

echo ==========================================
echo ModResorts - Docker Build and Push Script
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=modresorts

REM Sanitize project name for Docker tag (lowercase, hyphenate)
set IMAGE_NAME=%PROJECT_NAME%
set IMAGE_NAME=!IMAGE_NAME: =-!
for %%i in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do set IMAGE_NAME=!IMAGE_NAME:%%i=%%i!
set IMAGE_NAME=%IMAGE_NAME: =%

echo Project: %PROJECT_NAME%
echo Image name: %IMAGE_NAME%
echo.

REM Prompt for registry selection
echo Select container registry:
echo 1. AWS ECR (Elastic Container Registry)
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter your choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    REM AWS ECR Configuration
    echo.
    echo === AWS ECR Configuration ===
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    set /p ECR_REPO="Enter ECR Repository Name (default: %IMAGE_NAME%): "
    if "!ECR_REPO!"=="" set ECR_REPO=%IMAGE_NAME%
    
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    
    echo.
    echo Authenticating with AWS ECR...
    for /f "delims=" %%i in ('aws ecr get-login-password --region !AWS_REGION!') do set ECR_PASSWORD=%%i
    echo !ECR_PASSWORD! | docker login --username AWS --password-stdin !REGISTRY_URL!
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR authentication failed
        exit /b 1
    )
    
    echo ECR authentication successful
    
    REM Check if repository exists, create if it doesn't
    echo Checking if ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Repository does not exist. Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag (default: latest): "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
) else if "!REGISTRY_CHOICE!"=="2" (
    REM Docker Hub Configuration
    echo.
    echo === Docker Hub Configuration ===
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password or access token: "
    
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub authentication failed
        exit /b 1
    )
    
    echo Docker Hub authentication successful
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag (default: latest): "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/%IMAGE_NAME%:!IMAGE_TAG!
    
) else (
    echo ERROR: Invalid choice. Please select 1 or 2.
    exit /b 1
)

echo.
echo ==========================================
echo Building Docker image...
echo Image: !FULL_IMAGE_NAME!
echo ==========================================
echo.

REM Build Docker image
docker build -t "!FULL_IMAGE_NAME!" .

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed
    exit /b 1
)

echo.
echo Docker build successful!
echo.

echo ==========================================
echo Pushing image to registry...
echo ==========================================
echo.

REM Push Docker image
docker push "!FULL_IMAGE_NAME!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed
    exit /b 1
)

echo.
echo ==========================================
echo SUCCESS!
echo ==========================================
echo Image pushed successfully: !FULL_IMAGE_NAME!
echo.
echo You can now deploy this image to AWS ECS using the deploy-image.bat script
echo.

endlocal
