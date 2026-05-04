@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Deploy to AWS EKS Script
REM For ModResorts Spring Boot Application
REM ============================================

echo ==========================================
echo ModResorts - AWS EKS Deployment Script
echo ==========================================
echo.

REM Prompt for AWS EKS configuration
echo === AWS EKS Configuration ===
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS Cluster Name: "
echo.

REM Prompt for Docker image URI
set /p IMAGE_URI="Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): "
echo.

REM Prompt for environment variables
echo === Application Configuration ===
echo Enter values for environment variables (press Enter to skip):
echo.

set /p DB_URL="Database URL [jdbc:postgresql://localhost:5432/modresorts]: "
if "!DB_URL!"=="" set DB_URL=jdbc:postgresql://localhost:5432/modresorts

set /p DB_USERNAME="Database Username [dbuser]: "
if "!DB_USERNAME!"=="" set DB_USERNAME=dbuser

set /p DB_PASSWORD="Database Password: "

set /p DB_DRIVER="Database Driver [org.postgresql.Driver]: "
if "!DB_DRIVER!"=="" set DB_DRIVER=org.postgresql.Driver

set /p REDIS_HOST="Redis Host [localhost]: "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

set /p REDIS_PORT="Redis Port [6379]: "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Redis Password (optional): "

set /p REDIS_TIMEOUT="Redis Timeout [2000]: "
if "!REDIS_TIMEOUT!"=="" set REDIS_TIMEOUT=2000

set /p SERVICE_REGISTRY_URL="Service Registry URL [http://service-registry:8080]: "
if "!SERVICE_REGISTRY_URL!"=="" set SERVICE_REGISTRY_URL=http://service-registry:8080

set /p AWS_CLOUDMAP_NAMESPACE="AWS CloudMap Namespace (optional): "

echo.
echo ==========================================
echo Configuring kubectl for EKS
echo ==========================================
echo.

REM Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster
    exit /b 1
)

REM Verify cluster connectivity
echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes Manifests
echo ==========================================
echo.

REM Create temporary directory for processed manifests
set TEMP_DIR=%TEMP%\k8s-deploy-%RANDOM%
mkdir "!TEMP_DIR!"
xcopy /E /I /Q kubernetes "!TEMP_DIR!" >nul

REM Update manifests with actual values using PowerShell for better string replacement
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_URL}}', '!DB_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_USERNAME}}', '!DB_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_DRIVER}}', '!DB_DRIVER!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_TIMEOUT}}', '!REDIS_TIMEOUT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{SERVICE_REGISTRY_URL}}', '!SERVICE_REGISTRY_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{AWS_CLOUDMAP_NAMESPACE}}', '!AWS_CLOUDMAP_NAMESPACE!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Manifests updated successfully
echo.

echo ==========================================
echo Deploying to AWS EKS
echo ==========================================
echo.

REM Apply Kubernetes manifests in order
echo Creating namespace...
kubectl apply -f kubernetes\namespace.yaml

echo Deploying application...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"

echo Creating service...
kubectl apply -f kubernetes\service.yaml

echo Creating ingress...
kubectl apply -f kubernetes\ingress.yaml

echo.
echo ==========================================
echo Waiting for Deployment Rollout
echo ==========================================
echo.

REM Wait for deployment to complete
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    echo Checking pod status...
    kubectl get pods -n modresorts
    kubectl describe pods -n modresorts
    rmdir /S /Q "!TEMP_DIR!"
    exit /b 1
)

echo.
echo ==========================================
echo Verifying Deployment
echo ==========================================
echo.

REM Verify resources
kubectl get pods,svc,ingress -n modresorts

echo.
echo ==========================================
echo Deployment Completed Successfully!
echo ==========================================
echo.

REM Get ingress URL
for /f "delims=" %%i in ('kubectl get ingress modresorts-ingress -n modresorts -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}" 2^>nul') do set INGRESS_URL=%%i
if "!INGRESS_URL!"=="" set INGRESS_URL=Pending...

echo Application Details:
echo   Namespace: modresorts
echo   Deployment: modresorts
echo   Service: modresorts-service
echo   Ingress URL: !INGRESS_URL!
echo.
echo Health Check: http://!INGRESS_URL!/actuator/health
echo.
echo To view logs:
echo   kubectl logs -f deployment/modresorts -n modresorts
echo.
echo To scale deployment:
echo   kubectl scale deployment/modresorts -n modresorts --replicas=3
echo.
echo To rollback deployment:
echo   kubectl rollout undo deployment/modresorts -n modresorts
echo.

REM Cleanup temporary directory
rmdir /S /Q "!TEMP_DIR!"

endlocal
