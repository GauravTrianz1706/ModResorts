@echo off
setlocal enabledelayedexpansion

echo ==========================================
echo ModResorts - Docker Build and Push Script
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=modresorts

REM Sanitize image name using PowerShell
for /f "delims=" %%i in ('powershell -Command "$name = '%PROJECT_NAME%'; $name.ToLower() -replace '[^a-z0-9]+', '-' -replace '^-+', '' -replace '-+$', ''"') do set IMAGE_NAME=%%i

echo Project: %PROJECT_NAME%
echo Image Name: %IMAGE_NAME%
echo.

REM Prompt for registry type
echo Select Docker Registry:
echo 1. Azure Container Registry (ACR)
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    REM Azure ACR
    echo.
    echo === Azure Container Registry Configuration ===
    set /p ACR_NAME="Enter ACR name (e.g., myregistry): "
    set /p ACR_LOGIN_SERVER="Enter ACR login server (e.g., myregistry.azurecr.io): "
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag (default: latest): "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    REM Sanitize tag using PowerShell
    for /f "delims=" %%i in ('powershell -Command "$tag = '!IMAGE_TAG!'; $tag.ToLower() -replace '[^a-z0-9.-]+', '-' -replace '^-+', '' -replace '-+$', ''"') do set IMAGE_TAG=%%i
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!ACR_LOGIN_SERVER!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo.
    echo Logging into Azure Container Registry...
    az acr login --name !ACR_NAME!
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ACR login failed. Please check your Azure CLI configuration.
        exit /b 1
    )
    
) else if "!REGISTRY_CHOICE!"=="2" (
    REM Docker Hub
    echo.
    echo === Docker Hub Configuration ===
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password or access token: "
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag (default: latest): "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    REM Sanitize tag using PowerShell
    for /f "delims=" %%i in ('powershell -Command "$tag = '!IMAGE_TAG!'; $tag.ToLower() -replace '[^a-z0-9.-]+', '-' -replace '^-+', '' -replace '-+$', ''"') do set IMAGE_TAG=%%i
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo.
    echo Logging into Docker Hub...
    echo !DOCKER_PASSWORD!| docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub login failed. Please check your credentials.
        exit /b 1
    )
    
) else (
    echo ERROR: Invalid choice. Please select 1 or 2.
    exit /b 1
)

echo.
echo ==========================================
echo Building Docker Image
echo ==========================================
echo Image: !FULL_IMAGE_NAME!
echo.

REM Build Docker image
docker build -t "!FULL_IMAGE_NAME!" .

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)

echo.
echo ==========================================
echo Pushing Docker Image
echo ==========================================
echo.

REM Push Docker image
docker push "!FULL_IMAGE_NAME!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)

echo.
echo ==========================================
echo Build and Push Completed Successfully!
echo ==========================================
echo Image: !FULL_IMAGE_NAME!
echo.
echo Next steps:
echo 1. Update kubernetes\deployment.yaml with the image URI
echo 2. Run deploy-image.bat to deploy to Azure AKS
echo.

endlocal
