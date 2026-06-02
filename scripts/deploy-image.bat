@echo off
setlocal enabledelayedexpansion

:: =============================================================
:: deploy-image.bat - Deploy ModResorts to AWS EKS (Windows)
:: Usage: scripts\deploy-image.bat
:: Run from repository root directory
:: Prerequisites: aws-cli, kubectl
:: =============================================================

echo ==============================================
echo   ModResorts - AWS EKS Deployment Script
echo ==============================================
echo.

:: ---- Collect AWS / EKS configuration ----
set /p AWS_REGION="Enter AWS Region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS Region is required.
    exit /b 1
)

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo --- Optional Application Environment Variables ---
echo Press Enter to skip any variable.
echo.

set /p WEATHER_API_KEY_VAL="Enter WEATHER_API_KEY value (or press Enter to skip): "
set /p SERVER_DISPLAY_NAME_VAL="Enter SERVER_DISPLAY_NAME value [modresorts-server]: "
if "!SERVER_DISPLAY_NAME_VAL!"=="" set SERVER_DISPLAY_NAME_VAL=modresorts-server

set /p SERVER_FULL_NAME_VAL="Enter SERVER_FULL_NAME value [modresorts-server/default]: "
if "!SERVER_FULL_NAME_VAL!"=="" set SERVER_FULL_NAME_VAL=modresorts-server/default

set /p JNDI_FACTORY_VAL="Enter JNDI_FACTORY value [com.sun.jndi.rmi.registry.RegistryContextFactory]: "
if "!JNDI_FACTORY_VAL!"=="" set JNDI_FACTORY_VAL=com.sun.jndi.rmi.registry.RegistryContextFactory

set /p JNDI_PROVIDER_URL_VAL="Enter JNDI_PROVIDER_URL value [rmi://localhost:1099]: "
if "!JNDI_PROVIDER_URL_VAL!"=="" set JNDI_PROVIDER_URL_VAL=rmi://localhost:1099

set /p DB_HOST_VAL="Enter DB_HOST value (or press Enter to skip): "
set /p DB_PORT_VAL="Enter DB_PORT value [5432]: "
if "!DB_PORT_VAL!"=="" set DB_PORT_VAL=5432

set /p DB_NAME_VAL="Enter DB_NAME value [modresorts]: "
if "!DB_NAME_VAL!"=="" set DB_NAME_VAL=modresorts

set /p DB_USERNAME_VAL="Enter DB_USERNAME value [modresorts]: "
if "!DB_USERNAME_VAL!"=="" set DB_USERNAME_VAL=modresorts

set /p DB_PASSWORD_VAL="Enter DB_PASSWORD value (or press Enter to skip): "

echo.
echo --- Configuring kubectl for EKS cluster: !CLUSTER_NAME! ---
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster.
    exit /b 1
)

echo.
echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster.
    exit /b 1
)

echo.
echo --- Updating Kubernetes manifests with deployment values ---

:: Copy manifests to a temporary directory
xcopy /E /I /Y kubernetes kubernetes_deploy_tmp >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to copy Kubernetes manifests.
    exit /b 1
)

:: Use PowerShell to replace placeholders in deployment.yaml
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{IMAGE_URI\}\}', '!IMAGE_URI!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{WEATHER_API_KEY\}\}', '!WEATHER_API_KEY_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{SERVER_DISPLAY_NAME\}\}', '!SERVER_DISPLAY_NAME_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{SERVER_FULL_NAME\}\}', '!SERVER_FULL_NAME_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{JNDI_FACTORY\}\}', '!JNDI_FACTORY_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{JNDI_PROVIDER_URL\}\}', '!JNDI_PROVIDER_URL_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{DB_HOST\}\}', '!DB_HOST_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{DB_PORT\}\}', '!DB_PORT_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{DB_NAME\}\}', '!DB_NAME_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{DB_USERNAME\}\}', '!DB_USERNAME_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"
powershell -Command "(Get-Content 'kubernetes_deploy_tmp\deployment.yaml') -replace '\{\{DB_PASSWORD\}\}', '!DB_PASSWORD_VAL!' | Set-Content 'kubernetes_deploy_tmp\deployment.yaml'"

echo.
echo --- Applying Kubernetes manifests ---

echo Applying namespace...
kubectl apply -f kubernetes_deploy_tmp\namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace.
    rmdir /S /Q kubernetes_deploy_tmp
    exit /b 1
)

echo Applying deployment...
kubectl apply -f kubernetes_deploy_tmp\deployment.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment.
    rmdir /S /Q kubernetes_deploy_tmp
    exit /b 1
)

echo Applying service...
kubectl apply -f kubernetes_deploy_tmp\service.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service.
    rmdir /S /Q kubernetes_deploy_tmp
    exit /b 1
)

echo Applying ingress...
kubectl apply -f kubernetes_deploy_tmp\ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress.
    rmdir /S /Q kubernetes_deploy_tmp
    exit /b 1
)

:: Clean up temporary manifests
rmdir /S /Q kubernetes_deploy_tmp

echo.
echo --- Waiting for deployment rollout ---
kubectl rollout status deployment/modresorts -n modresorts --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed or timed out.
    echo To rollback, run: kubectl rollout undo deployment/modresorts -n modresorts
    exit /b 1
)

echo.
echo --- Verifying deployed resources ---
kubectl get pods,svc,ingress -n modresorts

echo.
echo ==============================================
echo   SUCCESS: ModResorts deployed to EKS!
echo ==============================================
echo.
echo Useful commands:
echo   kubectl get pods -n modresorts
echo   kubectl logs -f deployment/modresorts -n modresorts
echo   kubectl rollout undo deployment/modresorts -n modresorts

endlocal
exit /b 0
