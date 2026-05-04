@echo off
setlocal enabledelayedexpansion

REM Build and Push Script for ModResorts Application (Windows)
REM Supports AWS ECR and Docker Hub registries

REM Project configuration
set PROJECT_NAME=modresorts
set DEFAULT_TAG=latest

echo ========================================
echo ModResorts Docker Build and Push Script
echo ========================================
echo.

REM Sanitize image name: lowercase, replace non-alphanumeric with hyphens
set IMAGE_NAME=%PROJECT_NAME%
for %%i in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do (
    set IMAGE_NAME=!IMAGE_NAME:%%i=%%i!
)
set IMAGE_NAME=%IMAGE_NAME: =-%
set IMAGE_NAME=%IMAGE_NAME:_=-%

echo Project: %IMAGE_NAME%
echo.

REM Prompt for image tag
set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=%DEFAULT_TAG%

echo Using tag: !IMAGE_TAG!
echo.

REM Registry selection
echo Select container registry:
echo 1. AWS ECR (Elastic Container Registry)
echo 2. Docker Hub
echo.
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    echo Selected: AWS ECR
    echo.
    
    REM AWS ECR Configuration
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    set /p ECR_REPO="Enter ECR Repository Name (default: %IMAGE_NAME%): "
    if "!ECR_REPO!"=="" set ECR_REPO=%IMAGE_NAME%
    
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
    echo.
    echo Registry URL: !REGISTRY_URL!
    echo Full Image Name: !FULL_IMAGE_NAME!
    echo.
    
    REM Authenticate with ECR
    echo Authenticating with AWS ECR...
    for /f "delims=" %%i in ('aws ecr get-login-password --region !AWS_REGION!') do set ECR_PASSWORD=%%i
    echo !ECR_PASSWORD! | docker login --username AWS --password-stdin !REGISTRY_URL!
    
    if !ERRORLEVEL! neq 0 (
        echo ECR authentication failed. Please check your AWS credentials.
        exit /b 1
    )
    
    echo ECR authentication successful!
    echo.
    
    REM Check if ECR repository exists, create if not
    echo Checking ECR repository...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Repository does not exist. Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo Failed to create ECR repository.
            exit /b 1
        )
        echo ECR repository created successfully!
    )
    echo.
    
) else if "!REGISTRY_CHOICE!"=="2" (
    echo Selected: Docker Hub
    echo.
    
    REM Docker Hub Configuration
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password or access token: "
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/%IMAGE_NAME%:!IMAGE_TAG!
    
    echo.
    echo Full Image Name: !FULL_IMAGE_NAME!
    echo.
    
    REM Authenticate with Docker Hub
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub authentication failed. Please check your credentials.
        exit /b 1
    )
    
    echo Docker Hub authentication successful!
    echo.
    
) else (
    echo Invalid choice. Exiting.
    exit /b 1
)

REM Build Docker image
echo Building Docker image...
echo Image: !FULL_IMAGE_NAME!
echo.

docker build -t "!FULL_IMAGE_NAME!" .

if !ERRORLEVEL! neq 0 (
    echo Docker build failed. Please check the Dockerfile and build context.
    exit /b 1
)

echo.
echo Docker image built successfully!
echo.

REM Push Docker image
echo Pushing Docker image to registry...
docker push "!FULL_IMAGE_NAME!"

if !ERRORLEVEL! neq 0 (
    echo Docker push failed. Please check your network connection and registry credentials.
    exit /b 1
)

echo.
echo ========================================
echo Docker image pushed successfully!
echo ========================================
echo.
echo Image: !FULL_IMAGE_NAME!
echo.
echo Next steps:
echo 1. Update your deployment manifests with the image URI
echo 2. Deploy to your target environment (AWS ECS, Kubernetes, etc.)
echo.

endlocal
