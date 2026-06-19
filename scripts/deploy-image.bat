@echo off
setlocal enabledelayedexpansion

echo ==========================================
echo ModResorts - Azure AKS Deployment Script
echo ==========================================
echo.

REM Prompt for Azure AKS configuration
echo === Azure AKS Configuration ===
set /p RESOURCE_GROUP="Enter Azure Resource Group name: "
set /p CLUSTER_NAME="Enter AKS Cluster name: "

if "!RESOURCE_GROUP!"=="" (
    echo ERROR: Resource Group is required.
    exit /b 1
)

if "!CLUSTER_NAME!"=="" (
    echo ERROR: Cluster Name is required.
    exit /b 1
)

REM Prompt for Docker image URI
echo.
set /p IMAGE_URI="Enter Docker image URI (e.g., myregistry.azurecr.io/modresorts:latest): "

if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo ==========================================
echo Configuring kubectl for AKS
echo ==========================================
echo.

REM Get AKS credentials
az aks get-credentials --resource-group "!RESOURCE_GROUP!" --name "!CLUSTER_NAME!" --overwrite-existing

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to get AKS credentials. Please check your Azure configuration.
    exit /b 1
)

REM Verify cluster connectivity
echo.
echo Verifying cluster connectivity...
kubectl cluster-info

if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster.
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes Manifests
echo ==========================================
echo.

REM Create temporary directory for updated manifests
set TEMP_DIR=%TEMP%\k8s-deploy-%RANDOM%
mkdir "!TEMP_DIR!"
xcopy /E /I /Q kubernetes "!TEMP_DIR!" >nul

REM Update image URI in deployment manifest using PowerShell
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Manifests updated with image: !IMAGE_URI!

echo.
echo ==========================================
echo Deploying to Azure AKS
echo ==========================================
echo.

REM Apply namespace
echo Creating namespace...
kubectl apply -f "!TEMP_DIR!\namespace.yaml"

REM Apply deployment
echo Deploying application...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"

REM Apply service
echo Creating service...
kubectl apply -f "!TEMP_DIR!\service.yaml"

REM Apply ingress
echo Creating ingress...
kubectl apply -f "!TEMP_DIR!\ingress.yaml"

echo.
echo ==========================================
echo Waiting for Deployment Rollout
echo ==========================================
echo.

REM Wait for deployment to complete
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed or timed out.
    echo.
    echo Checking pod status...
    kubectl get pods -n modresorts
    echo.
    echo Checking pod logs...
    kubectl logs -n modresorts -l app=modresorts --tail=50
    exit /b 1
)

echo.
echo ==========================================
echo Deployment Status
echo ==========================================
echo.

REM Display deployment status
kubectl get pods,svc,ingress -n modresorts

echo.
echo ==========================================
echo Deployment Completed Successfully!
echo ==========================================
echo.
echo Application Details:
echo   Namespace: modresorts
echo   Deployment: modresorts
echo   Service: modresorts-service
echo   Ingress: modresorts-ingress
echo.
echo To access the application:
echo   1. Update your DNS to point modresorts.example.com to the ingress IP
echo   2. Or use port-forward for testing:
echo      kubectl port-forward -n modresorts svc/modresorts-service 8080:80
echo   3. Access: http://localhost:8080
echo.
echo Useful commands:
echo   View pods: kubectl get pods -n modresorts
echo   View logs: kubectl logs -n modresorts -l app=modresorts
echo   Describe deployment: kubectl describe deployment modresorts -n modresorts
echo   Scale deployment: kubectl scale deployment modresorts -n modresorts --replicas=3
echo.

REM Cleanup temporary directory
rmdir /S /Q "!TEMP_DIR!"

endlocal
