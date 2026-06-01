@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM build-push.bat - Build and Push Docker Image for ModResorts (Windows)
REM Supports: AWS ECR and Docker Hub registries
REM Usage: scripts\build-push.bat
REM Run from repository root directory
REM =============================================================================

set "PROJECT_NAME=modresorts"
set "DOCKERFILE_PATH=Dockerfile"

echo ==============================================
echo   ModResorts - Docker Build ^& Push Script
echo ==============================================
echo.

REM Sanitize image name using PowerShell
for /f "delims=" %%i in ('powershell -Command "$n = 'modresorts'; $n = $n.ToLower() -replace '[^a-z0-9]','-'; $n = $n.Trim('-'); Write-Output $n"') do set "IMAGE_NAME=%%i"
echo Image name: !IMAGE_NAME!
echo.

REM Prompt for image tag
set /p "IMAGE_TAG_INPUT=Enter image tag [latest]: "
if "!IMAGE_TAG_INPUT!"=="" set "IMAGE_TAG_INPUT=latest"
for /f "delims=" %%i in ('powershell -Command "$t = '!IMAGE_TAG_INPUT!'; $t = $t.ToLower() -replace '[^a-z0-9._-]','-'; $t = $t.Trim('-'); if ($t -eq '') { $t = 'latest' }; Write-Output $t"') do set "IMAGE_TAG=%%i"
echo Image tag: !IMAGE_TAG!
echo.

REM Select registry type
echo Select container registry:
echo   1. AWS ECR (Elastic Container Registry)
echo   2. Docker Hub
echo.
set /p "REGISTRY_CHOICE=Enter choice [1 or 2]: "

if "!REGISTRY_CHOICE!"=="1" goto :ecr_setup
if "!REGISTRY_CHOICE!"=="2" goto :dockerhub_setup
echo ERROR: Invalid registry choice. Please enter 1 or 2.
exit /b 1

:ecr_setup
echo.
echo --- AWS ECR Configuration ---
set /p "AWS_REGION_INPUT=Enter AWS Region [us-east-1]: "
if "!AWS_REGION_INPUT!"=="" set "AWS_REGION_INPUT=us-east-1"
set "AWS_REGION=!AWS_REGION_INPUT!"

set /p "ECR_REPO_INPUT=Enter ECR repository name [!IMAGE_NAME!]: "
if "!ECR_REPO_INPUT!"=="" set "ECR_REPO_INPUT=!IMAGE_NAME!"
set "ECR_REPO=!ECR_REPO_INPUT!"

echo.
echo Retrieving AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text 2^>^&1') do set "ACCOUNT_ID=%%i"
if "!ACCOUNT_ID!"=="" (
    echo ERROR: Could not retrieve AWS Account ID. Ensure AWS CLI is configured.
    exit /b 1
)
echo AWS Account ID: !ACCOUNT_ID!

set "REGISTRY_URL=!ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

echo.
echo Logging in to AWS ECR...
aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
if !ERRORLEVEL! neq 0 (
    echo ERROR: ECR login failed.
    exit /b 1
)
echo ECR login successful.

echo.
echo Checking if ECR repository '!ECR_REPO!' exists...
aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Repository not found. Creating ECR repository '!ECR_REPO!'...
    aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECR repository.
        exit /b 1
    )
    echo ECR repository created successfully.
) else (
    echo ECR repository already exists.
)
goto :build_image

:dockerhub_setup
echo.
echo --- Docker Hub Configuration ---
set /p "DOCKER_USERNAME=Enter Docker Hub username: "
set /p "DOCKER_PASSWORD=Enter Docker Hub password/token: "
set /p "DOCKER_NAMESPACE_INPUT=Enter Docker Hub namespace/org [!DOCKER_USERNAME!]: "
if "!DOCKER_NAMESPACE_INPUT!"=="" set "DOCKER_NAMESPACE_INPUT=!DOCKER_USERNAME!"
set "DOCKER_NAMESPACE=!DOCKER_NAMESPACE_INPUT!"

set "FULL_IMAGE_NAME=!DOCKER_NAMESPACE!/!IMAGE_NAME!:!IMAGE_TAG!"

echo.
echo Logging in to Docker Hub...
echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker Hub login failed.
    exit /b 1
)
echo Docker Hub login successful.
goto :build_image

:build_image
echo.
echo ==============================================
echo   Building Docker Image
echo ==============================================
echo Image: !FULL_IMAGE_NAME!
echo Dockerfile: !DOCKERFILE_PATH!
echo Build context: . (repository root)
echo.

docker build -f "!DOCKERFILE_PATH!" -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)
echo.
echo Docker build completed successfully.

REM Tag as latest if a specific tag was provided
if not "!IMAGE_TAG!"=="latest" (
    if "!REGISTRY_CHOICE!"=="1" (
        set "LATEST_IMAGE=!REGISTRY_URL!/!ECR_REPO!:latest"
    ) else (
        set "LATEST_IMAGE=!DOCKER_NAMESPACE!/!IMAGE_NAME!:latest"
    )
    docker tag "!FULL_IMAGE_NAME!" "!LATEST_IMAGE!"
    echo Tagged as: !LATEST_IMAGE!
)

echo.
echo ==============================================
echo   Pushing Docker Image
echo ==============================================
echo Pushing: !FULL_IMAGE_NAME!
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)

if not "!IMAGE_TAG!"=="latest" (
    echo Pushing: !LATEST_IMAGE!
    docker push "!LATEST_IMAGE!"
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker push of latest tag failed.
        exit /b 1
    )
)

echo.
echo ==============================================
echo   Build ^& Push Complete!
echo ==============================================
echo Image URI: !FULL_IMAGE_NAME!
echo.
echo Use this image URI in your ECS deployment:
echo   scripts\deploy-image.bat
echo.

endlocal
