@echo off
setlocal enabledelayedexpansion

REM Deploy ModResorts Application to AWS EKS
REM This script configures kubectl and deploys the application to EKS cluster

echo ==========================================
echo ModResorts - AWS EKS Deployment Script
echo ==========================================
echo.

REM Prompt for AWS region
set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
    echo Error: AWS region is required
    exit /b 1
)

REM Prompt for EKS cluster name
set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo Error: EKS cluster name is required
    exit /b 1
)

REM Prompt for Docker image URI
echo.
echo Enter the full Docker image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest)
set /p IMAGE_URI="Docker image URI: "
if "!IMAGE_URI!"=="" (
    echo Error: Docker image URI is required
    exit /b 1
)

echo.
echo ==========================================
echo Configuring kubectl for EKS cluster
echo ==========================================

REM Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

if !ERRORLEVEL! neq 0 (
    echo Error: Failed to configure kubectl for EKS cluster
    exit /b 1
)

echo kubectl configured successfully

REM Verify cluster connectivity
echo.
echo Verifying cluster connectivity...
kubectl cluster-info

if !ERRORLEVEL! neq 0 (
    echo Error: Cannot connect to EKS cluster
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes manifests
echo ==========================================

REM Create temporary directory for modified manifests
set TEMP_DIR=%TEMP%\modresorts-k8s-%RANDOM%
mkdir !TEMP_DIR!
xcopy /E /I /Q kubernetes !TEMP_DIR! >nul

REM Update deployment.yaml with actual image URI using PowerShell
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Manifests updated with image URI: !IMAGE_URI!

echo.
echo ==========================================
echo Deploying to AWS EKS
echo ==========================================

REM Apply namespace
echo Creating namespace...
kubectl apply -f !TEMP_DIR!\namespace.yaml

REM Apply deployment
echo Deploying application...
kubectl apply -f !TEMP_DIR!\deployment.yaml

REM Apply service
echo Creating service...
kubectl apply -f !TEMP_DIR!\service.yaml

REM Apply ingress
echo Creating ingress...
kubectl apply -f !TEMP_DIR!\ingress.yaml

echo.
echo ==========================================
echo Waiting for deployment to complete
echo ==========================================

REM Wait for deployment rollout
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo Error: Deployment rollout failed
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

REM Display deployment status
kubectl get pods -n modresorts
echo.
kubectl get svc -n modresorts
echo.
kubectl get ingress -n modresorts

echo.
echo ==========================================
echo Deployment Completed Successfully!
echo ==========================================

REM Get ingress URL
for /f "delims=" %%i in ('kubectl get ingress modresorts-ingress -n modresorts -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}" 2^>nul') do set INGRESS_URL=%%i

if not "!INGRESS_URL!"=="" (
    echo.
    echo Application URL: http://!INGRESS_URL!
    echo.
    echo Note: It may take a few minutes for the Load Balancer to become active
    echo Health check endpoint: http://!INGRESS_URL!/health
) else (
    echo.
    echo Ingress is being provisioned. Run the following command to get the URL:
    echo kubectl get ingress modresorts-ingress -n modresorts
)

echo.
echo Useful commands:
echo   View pods:        kubectl get pods -n modresorts
echo   View logs:        kubectl logs -n modresorts -l app=modresorts
echo   Describe pod:     kubectl describe pod ^<pod-name^> -n modresorts
echo   Scale deployment: kubectl scale deployment modresorts -n modresorts --replicas=3
echo   Delete deployment: kubectl delete -f kubernetes/
echo.

REM Cleanup temporary directory
rmdir /S /Q !TEMP_DIR!

echo ==========================================

endlocal
