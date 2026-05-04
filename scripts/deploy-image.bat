@echo off
setlocal enabledelayedexpansion

REM AWS ECS Fargate Deployment Script for ModResorts Application (Windows)
REM This script deploys the containerized application to AWS ECS Fargate

echo ========================================
echo ModResorts ECS Fargate Deployment Script
echo ========================================
echo.

REM Prompt for AWS configuration
echo AWS Configuration
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter ECS Cluster Name: "
echo.

REM Get AWS Account ID
echo Retrieving AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
if !ERRORLEVEL! neq 0 (
    echo Failed to retrieve AWS Account ID. Please check your AWS credentials.
    exit /b 1
)
echo Account ID: !ACCOUNT_ID!
echo.

REM Check if cluster exists, create if not
echo Checking ECS cluster...
aws ecs describe-clusters --clusters "!CLUSTER_NAME!" --region "!AWS_REGION!" >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Cluster does not exist. Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name "!CLUSTER_NAME!" --region "!AWS_REGION!"
    if !ERRORLEVEL! neq 0 (
        echo Failed to create ECS cluster.
        exit /b 1
    )
    echo ECS cluster created successfully!
)
echo.

REM Prompt for network configuration
echo Network Configuration
set /p VPC_ID="Enter VPC ID: "
set /p SUBNETS_INPUT="Enter Subnet IDs (comma-separated, at least 2): "
set /p SECURITY_GROUP="Enter Security Group ID: "
echo.

REM Parse subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)
set SUBNET_1=!SUBNET_1: =!
set SUBNET_2=!SUBNET_2: =!

if "!SUBNET_1!"=="" (
    echo At least 2 subnets are required for high availability.
    exit /b 1
)
if "!SUBNET_2!"=="" (
    echo At least 2 subnets are required for high availability.
    exit /b 1
)

REM Prompt for Docker image URI
echo Docker Image Configuration
set /p IMAGE_URI="Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): "
echo.

REM Load balancer configuration
echo Load Balancer Configuration
set /p NEED_LB="Do you need a load balancer for this service? (y/n): "

if /i "!NEED_LB!"=="y" (
    echo Creating Application Load Balancer and Target Group...
    
    REM Create ALB
    set ALB_NAME=modresorts-alb
    echo Creating Application Load Balancer: !ALB_NAME!
    for /f "delims=" %%i in ('aws elbv2 create-load-balancer --name "!ALB_NAME!" --subnets "!SUBNET_1!" "!SUBNET_2!" --security-groups "!SECURITY_GROUP!" --scheme internet-facing --type application --ip-address-type ipv4 --region "!AWS_REGION!" --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    
    if !ERRORLEVEL! neq 0 (
        echo Failed to create Application Load Balancer.
        exit /b 1
    )
    
    echo ALB created: !ALB_ARN!
    
    REM Get ALB DNS name
    for /f "delims=" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns "!ALB_ARN!" --region "!AWS_REGION!" --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i
    
    REM Create Target Group with target-type ip (required for Fargate)
    set TG_NAME=modresorts-tg
    echo Creating Target Group: !TG_NAME!
    for /f "delims=" %%i in ('aws elbv2 create-target-group --name "!TG_NAME!" --protocol HTTP --port 8080 --vpc-id "!VPC_ID!" --target-type ip --health-check-enabled --health-check-protocol HTTP --health-check-path "/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 5 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region "!AWS_REGION!" --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    
    if !ERRORLEVEL! neq 0 (
        echo Failed to create Target Group.
        exit /b 1
    )
    
    echo Target Group created: !TARGET_GROUP_ARN!
    
    REM Create Listener
    echo Creating ALB Listener...
    aws elbv2 create-listener --load-balancer-arn "!ALB_ARN!" --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn="!TARGET_GROUP_ARN!" --region "!AWS_REGION!" >nul
    
    if !ERRORLEVEL! neq 0 (
        echo Failed to create ALB Listener.
        exit /b 1
    )
    
    echo ALB Listener created successfully!
    echo.
    
    set USE_LB=true
) else (
    echo Skipping load balancer creation.
    set USE_LB=false
    echo.
)

REM Create CloudWatch Log Group
set LOG_GROUP=/ecs/modresorts
echo Creating CloudWatch Log Group: !LOG_GROUP!
aws logs create-log-group --log-group-name "!LOG_GROUP!" --region "!AWS_REGION!" 2>nul
if !ERRORLEVEL! neq 0 (
    echo Log group already exists or creation skipped.
)
echo.

REM Replace placeholders in task definition
echo Preparing task definition...
set TASK_DEF_FILE=ecs\task-definition.json
set TASK_DEF_TEMP=ecs\task-definition-temp.json

powershell -Command "(Get-Content '!TASK_DEF_FILE!') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' -replace '{{AWS_REGION}}', '!AWS_REGION!' -replace '{{ACCOUNT_ID}}', '!ACCOUNT_ID!' | Set-Content '!TASK_DEF_TEMP!'"

REM Register task definition
echo Registering ECS task definition...
for /f "delims=" %%i in ('aws ecs register-task-definition --cli-input-json file://!TASK_DEF_TEMP! --region "!AWS_REGION!" --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i

if !ERRORLEVEL! neq 0 (
    echo Failed to register task definition.
    del /f !TASK_DEF_TEMP! 2>nul
    exit /b 1
)

echo Task definition registered: !TASK_DEF_ARN!
del /f !TASK_DEF_TEMP! 2>nul
echo.

REM Prepare service definition
echo Preparing service definition...
set SERVICE_DEF_FILE=ecs\service-definition.json
set SERVICE_DEF_TEMP=ecs\service-definition-temp.json

powershell -Command "(Get-Content '!SERVICE_DEF_FILE!') -replace '{{CLUSTER_NAME}}', '!CLUSTER_NAME!' -replace '{{SUBNET_1}}', '!SUBNET_1!' -replace '{{SUBNET_2}}', '!SUBNET_2!' -replace '{{SECURITY_GROUP}}', '!SECURITY_GROUP!' | Set-Content '!SERVICE_DEF_TEMP!'"

REM Handle load balancer configuration
if "!USE_LB!"=="true" (
    powershell -Command "(Get-Content '!SERVICE_DEF_TEMP!') -replace '{{TARGET_GROUP_ARN}}', '!TARGET_GROUP_ARN!' | Set-Content '!SERVICE_DEF_TEMP!'"
) else (
    REM Remove loadBalancers section - simplified approach for Windows
    echo Note: Load balancer section not removed automatically on Windows. Manual edit may be required.
)

REM Check if service exists
set SERVICE_NAME=modresorts-service
echo Checking if service exists...
for /f "delims=" %%i in ('aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[?status==`ACTIVE`].serviceName" --output text 2^>nul') do set EXISTING_SERVICE=%%i

if not "!EXISTING_SERVICE!"=="" (
    if not "!EXISTING_SERVICE!"=="None" (
        REM Update existing service
        echo Service exists. Updating service...
        aws ecs update-service --cluster "!CLUSTER_NAME!" --service "!SERVICE_NAME!" --task-definition "!TASK_DEF_ARN!" --desired-count 2 --region "!AWS_REGION!" >nul
        
        if !ERRORLEVEL! neq 0 (
            echo Failed to update service.
            del /f !SERVICE_DEF_TEMP! 2>nul
            exit /b 1
        )
        
        echo Service updated successfully!
    ) else (
        goto create_service
    )
) else (
    :create_service
    REM Create new service
    echo Service does not exist. Creating new service...
    aws ecs create-service --cli-input-json file://!SERVICE_DEF_TEMP! --region "!AWS_REGION!" >nul
    
    if !ERRORLEVEL! neq 0 (
        echo Failed to create service.
        del /f !SERVICE_DEF_TEMP! 2>nul
        exit /b 1
    )
    
    echo Service created successfully!
)

del /f !SERVICE_DEF_TEMP! 2>nul
echo.

REM Wait for service to stabilize
echo Waiting for service to stabilize (this may take a few minutes)...
aws ecs wait services-stable --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!"

if !ERRORLEVEL! neq 0 (
    echo Service failed to stabilize. Check ECS console for details.
    exit /b 1
)

echo Service is stable!
echo.

REM Display deployment information
echo ========================================
echo Deployment Successful!
echo ========================================
echo.
echo Deployment Details:
echo   Cluster: !CLUSTER_NAME!
echo   Service: !SERVICE_NAME!
echo   Task Definition: !TASK_DEF_ARN!
echo   Region: !AWS_REGION!
echo.

if "!USE_LB!"=="true" (
    echo Application Access:
    echo   Load Balancer DNS: !ALB_DNS!
    echo   Application URL: http://!ALB_DNS!
    echo   Health Check: http://!ALB_DNS!/health
    echo.
)

echo CloudWatch Logs:
echo   Log Group: !LOG_GROUP!
echo   View logs: aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!
echo.

echo Next Steps:
echo 1. Verify the application is running: aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!
echo 2. Check CloudWatch logs for application output
if "!USE_LB!"=="true" (
    echo 3. Access the application at: http://!ALB_DNS!
)
echo.

endlocal
