@echo off
setlocal enabledelayedexpansion

set APP_NAME=modresorts
set NAMESPACE=modresorts

echo ============================================
echo   ModResorts - Deploy to Azure AKS
echo ============================================
echo.

rem ---- Prompt for Azure / AKS details ----
set /p RESOURCE_GROUP="Enter Azure Resource Group name: "
if "!RESOURCE_GROUP!"=="" (
    echo ERROR: Resource group cannot be empty.
    exit /b 1
)

set /p CLUSTER_NAME="Enter AKS Cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: AKS cluster name cannot be empty.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI (e.g. myregistry.azurecr.io/modresorts:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI cannot be empty.
    exit /b 1
)

echo.
echo ---- Application Environment Variables ----
echo Press Enter to skip any optional variable.
echo.

set /p WEATHER_API_KEY_VAL="Enter WEATHER_API_KEY (Weather Underground API key, or Enter to skip): "
set /p JNDI_FACTORY_VAL="Enter JNDI_FACTORY (or Enter for default): "
if "!JNDI_FACTORY_VAL!"=="" set JNDI_FACTORY_VAL=com.sun.jndi.fscontext.RefFSContextFactory

set /p JNDI_PROVIDER_URL_VAL="Enter JNDI_PROVIDER_URL (or Enter to skip): "

set /p SERVER_DISPLAY_NAME_VAL="Enter SERVER_DISPLAY_NAME (or Enter for 'modresorts'): "
if "!SERVER_DISPLAY_NAME_VAL!"=="" set SERVER_DISPLAY_NAME_VAL=modresorts

set /p SERVER_FULL_NAME_VAL="Enter SERVER_FULL_NAME (or Enter for 'modresorts'): "
if "!SERVER_FULL_NAME_VAL!"=="" set SERVER_FULL_NAME_VAL=modresorts

echo.
echo ---- Configuring kubectl for AKS ----
az aks get-credentials --resource-group !RESOURCE_GROUP! --name !CLUSTER_NAME! --overwrite-existing
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to get AKS credentials.
    exit /b 1
)

echo.
echo Verifying cluster connectivity ...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to AKS cluster.
    exit /b 1
)

echo.
echo ---- Updating Kubernetes manifests ----

rem Create temp copies of manifests
set TEMP_DIR=%TEMP%\modresorts-deploy
if exist "!TEMP_DIR!" rmdir /s /q "!TEMP_DIR!"
mkdir "!TEMP_DIR!"
xcopy /s /q kubernetes\* "!TEMP_DIR!\" >nul

rem Replace placeholders using PowerShell
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '\{\{IMAGE_URI\}\}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to update IMAGE_URI. & exit /b 1 )

powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '\{\{WEATHER_API_KEY\}\}', '!WEATHER_API_KEY_VAL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '\{\{JNDI_FACTORY\}\}', '!JNDI_FACTORY_VAL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '\{\{JNDI_PROVIDER_URL\}\}', '!JNDI_PROVIDER_URL_VAL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '\{\{SERVER_DISPLAY_NAME\}\}', '!SERVER_DISPLAY_NAME_VAL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '\{\{SERVER_FULL_NAME\}\}', '!SERVER_FULL_NAME_VAL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo.
echo ---- Applying Kubernetes manifests ----
echo Applying namespace ...
kubectl apply -f "!TEMP_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo Applying deployment ...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo Applying service ...
kubectl apply -f "!TEMP_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo Applying ingress ...
kubectl apply -f "!TEMP_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

echo.
echo ---- Waiting for rollout ----
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed. Initiating rollback ...
    kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    echo Rollback initiated. Check pod status with: kubectl get pods -n !NAMESPACE!
    exit /b 1
)

echo.
echo ---- Verifying resources ----
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ---- Application Access ----
echo Application URL: http://modresorts.example.com/resorts/
echo Health check:    http://modresorts.example.com/resorts/health
echo Note: Update your DNS to point modresorts.example.com to the ingress IP.

echo.
echo ============================================
echo   Deployment completed successfully!
echo ============================================

rem Cleanup
rmdir /s /q "!TEMP_DIR!" >nul 2>&1

endlocal
