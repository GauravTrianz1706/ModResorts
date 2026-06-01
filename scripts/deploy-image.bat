@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM deploy-image.bat - Deploy ModResorts to AWS ECS Fargate (Windows)
REM Usage: scripts\deploy-image.bat
REM Prerequisites: AWS CLI configured, ECS task/service definition files present
REM =============================================================================

set "PROJECT_NAME=modresorts"
set "TASK_DEF_FILE=ecs\task-definition.json"
set "SERVICE_DEF_FILE=ecs\service-definition.json"
set "LOG_GROUP=/ecs/modresorts"
set "SERVICE_NAME=modresorts-service"

echo ==============================================
echo   ModResorts - AWS ECS Fargate Deployment
echo ==============================================
echo.

REM ---- Collect Configuration ----
set /p "AWS_REGION_INPUT=Enter AWS Region [us-east-1]: "
if "!AWS_REGION_INPUT!"=="" set "AWS_REGION_INPUT=us-east-1"
set "AWS_REGION=!AWS_REGION_INPUT!"

set /p "CLUSTER_NAME_INPUT=Enter ECS Cluster name [modresorts-cluster]: "
if "!CLUSTER_NAME_INPUT!"=="" set "CLUSTER_NAME_INPUT=modresorts-cluster"
set "CLUSTER_NAME=!CLUSTER_NAME_INPUT!"

set /p "IMAGE_URI=Enter ECR Image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI is required.
    exit /b 1
)

echo.
echo --- Network Configuration ---
set /p "VPC_ID=Enter VPC ID (e.g. vpc-xxxxxxxx): "
if "!VPC_ID!"=="" (
    echo ERROR: VPC ID is required.
    exit /b 1
)

set /p "SUBNETS_INPUT=Enter Subnet IDs comma-separated (e.g. subnet-aaa,subnet-bbb): "
if "!SUBNETS_INPUT!"=="" (
    echo ERROR: At least one subnet ID is required.
    exit /b 1
)

REM Parse first two subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set "SUBNET_1=%%a"
    set "SUBNET_2=%%b"
)
if "!SUBNET_2!"=="" set "SUBNET_2=!SUBNET_1!"

set /p "SECURITY_GROUP=Enter Security Group ID (e.g. sg-xxxxxxxx): "
if "!SECURITY_GROUP!"=="" (
    echo ERROR: Security Group ID is required.
    exit /b 1
)

REM ---- Get AWS Account ID ----
echo.
echo Retrieving AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text 2^>^&1') do set "ACCOUNT_ID=%%i"
if "!ACCOUNT_ID!"=="" (
    echo ERROR: Could not retrieve AWS Account ID. Ensure AWS CLI is configured.
    exit /b 1
)
echo AWS Account ID: !ACCOUNT_ID!

REM ---- Ensure CloudWatch Log Group Exists ----
echo.
echo Ensuring CloudWatch log group '!LOG_GROUP!' exists...
aws logs create-log-group --log-group-name "!LOG_GROUP!" --region "!AWS_REGION!" >nul 2>&1
echo Log group ready: !LOG_GROUP!

REM ---- Check / Create ECS Cluster ----
echo.
echo Checking ECS cluster '!CLUSTER_NAME!'...
for /f "delims=" %%i in ('aws ecs describe-clusters --clusters "!CLUSTER_NAME!" --region "!AWS_REGION!" --query "clusters[0].status" --output text 2^>^&1') do set "CLUSTER_STATUS=%%i"

if not "!CLUSTER_STATUS!"=="ACTIVE" (
    echo Cluster not found or inactive. Creating cluster '!CLUSTER_NAME!'...
    aws ecs create-cluster --cluster-name "!CLUSTER_NAME!" --region "!AWS_REGION!"
    echo Cluster created.
) else (
    echo Cluster '!CLUSTER_NAME!' is ACTIVE.
)

REM ---- Load Balancer Prompt ----
echo.
set /p "NEED_LB_INPUT=Do you need an Application Load Balancer for this service? (y/n) [n]: "
if "!NEED_LB_INPUT!"=="" set "NEED_LB_INPUT=n"
set "TARGET_GROUP_ARN="
set "LB_DNS="

if /i "!NEED_LB_INPUT!"=="y" (
    echo.
    echo --- Creating Application Load Balancer ---

    echo Creating Application Load Balancer...
    for /f "delims=" %%i in ('aws elbv2 create-load-balancer --name "!PROJECT_NAME!-alb" --subnets !SUBNETS_INPUT! --security-groups "!SECURITY_GROUP!" --scheme internet-facing --type application --ip-address-type ipv4 --region "!AWS_REGION!" --query "LoadBalancers[0].LoadBalancerArn" --output text 2^>^&1') do set "ALB_ARN=%%i"
    echo ALB ARN: !ALB_ARN!

    for /f "delims=" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns "!ALB_ARN!" --region "!AWS_REGION!" --query "LoadBalancers[0].DNSName" --output text 2^>^&1') do set "LB_DNS=%%i"

    echo Creating Target Group...
    for /f "delims=" %%i in ('aws elbv2 create-target-group --name "!PROJECT_NAME!-tg" --protocol HTTP --port 9080 --vpc-id "!VPC_ID!" --target-type ip --health-check-protocol HTTP --health-check-path "/resorts/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 10 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region "!AWS_REGION!" --query "TargetGroups[0].TargetGroupArn" --output text 2^>^&1') do set "TARGET_GROUP_ARN=%%i"
    echo Target Group ARN: !TARGET_GROUP_ARN!

    echo Creating ALB Listener on port 80...
    aws elbv2 create-listener --load-balancer-arn "!ALB_ARN!" --protocol HTTP --port 80 --default-actions "Type=forward,TargetGroupArn=!TARGET_GROUP_ARN!" --region "!AWS_REGION!" --output text >nul
    echo ALB Listener created.
)

REM ---- Prepare Task Definition ----
echo.
echo Preparing task definition...
copy /Y "!TASK_DEF_FILE!" "%TEMP%\task-definition-deploy.json" >nul

powershell -Command "(Get-Content '%TEMP%\task-definition-deploy.json') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' -replace '{{AWS_REGION}}', '!AWS_REGION!' -replace '{{ACCOUNT_ID}}', '!ACCOUNT_ID!' | Set-Content '%TEMP%\task-definition-deploy.json'"

REM ---- Register Task Definition ----
echo Registering ECS task definition...
for /f "delims=" %%i in ('aws ecs register-task-definition --cli-input-json "file://%TEMP%\task-definition-deploy.json" --region "!AWS_REGION!" --query "taskDefinition.taskDefinitionArn" --output text 2^>^&1') do set "TASK_DEF_ARN=%%i"
echo Task Definition ARN: !TASK_DEF_ARN!

REM ---- Prepare Service Definition ----
echo.
echo Preparing service definition...
copy /Y "!SERVICE_DEF_FILE!" "%TEMP%\service-definition-deploy.json" >nul

powershell -Command "(Get-Content '%TEMP%\service-definition-deploy.json') -replace '{{CLUSTER_NAME}}', '!CLUSTER_NAME!' -replace '{{SUBNET_1}}', '!SUBNET_1!' -replace '{{SUBNET_2}}', '!SUBNET_2!' -replace '{{SECURITY_GROUP}}', '!SECURITY_GROUP!' | Set-Content '%TEMP%\service-definition-deploy.json'"

REM ---- Create or Update ECS Service ----
echo.
echo Checking if ECS service '!SERVICE_NAME!' exists...
for /f "delims=" %%i in ('aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[?status==''ACTIVE''].serviceName" --output text 2^>^&1') do set "EXISTING_SERVICE=%%i"

if "!EXISTING_SERVICE!"=="" (
    echo Service does not exist. Creating new ECS service '!SERVICE_NAME!'...
    aws ecs create-service --cli-input-json "file://%TEMP%\service-definition-deploy.json" --region "!AWS_REGION!"
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS service.
        exit /b 1
    )
    echo ECS service created.
) else (
    echo Service '!SERVICE_NAME!' exists. Updating service with new task definition...
    aws ecs update-service --cluster "!CLUSTER_NAME!" --service "!SERVICE_NAME!" --task-definition "!TASK_DEF_ARN!" --region "!AWS_REGION!" --output text >nul
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to update ECS service.
        exit /b 1
    )
    echo ECS service updated.
)

REM ---- Wait for Service Stability ----
echo.
echo Waiting for service to reach stable state (this may take several minutes)...
aws ecs wait services-stable --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!"
echo Service is stable.

REM ---- Verify Deployment ----
echo.
echo ==============================================
echo   Deployment Verification
echo ==============================================
aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[0].{ServiceName:serviceName,Status:status,DesiredCount:desiredCount,RunningCount:runningCount,PendingCount:pendingCount}" --output table

echo.
echo CloudWatch Log Group: !LOG_GROUP!
echo   View logs: aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!

if not "!LB_DNS!"=="" (
    echo.
    echo Load Balancer DNS: http://!LB_DNS!
    echo Application URL:   http://!LB_DNS!/resorts/
    echo Health Check URL:  http://!LB_DNS!/resorts/health
)

echo.
echo ==============================================
echo   Deployment Complete!
echo ==============================================
echo.
echo Troubleshooting tips:
echo   - View task failures: aws ecs describe-tasks --cluster !CLUSTER_NAME! --tasks ^<task-arn^> --region !AWS_REGION!
echo   - View service events: aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!
echo   - Check logs: aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!
echo.

endlocal
