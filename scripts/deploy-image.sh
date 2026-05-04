#!/bin/bash

# ModResorts - AWS ECS Fargate Deployment Script for Linux/macOS
# This script deploys the Docker image to AWS ECS Fargate

set -e
set -o pipefail

echo "=========================================="
echo "ModResorts - AWS ECS Fargate Deployment"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="modresorts"
SERVICE_NAME="modresorts-service"
TASK_FAMILY="modresorts-task"

# Prompt for AWS configuration
echo "=== AWS Configuration ==="
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter ECS Cluster Name: " CLUSTER_NAME
read -p "Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI

echo ""
echo "=== Network Configuration ==="
read -p "Enter VPC ID: " VPC_ID
read -p "Enter Subnet IDs (comma-separated, at least 2): " SUBNETS_INPUT
read -p "Enter Security Group ID: " SECURITY_GROUP

# Convert comma-separated subnets to array
IFS=',' read -ra SUBNETS <<< "$SUBNETS_INPUT"
SUBNET_1=$(echo "${SUBNETS[0]}" | xargs)
SUBNET_2=$(echo "${SUBNETS[1]}" | xargs)

echo ""
echo "=== Load Balancer Configuration ==="
read -p "Do you need a load balancer for this service? (y/n): " NEED_LB

TARGET_GROUP_ARN=""
if [[ "$NEED_LB" == "y" || "$NEED_LB" == "Y" ]]; then
    echo "Creating Application Load Balancer and Target Group..."
    
    # Create ALB
    ALB_NAME="modresorts-alb"
    echo "Creating ALB: $ALB_NAME"
    ALB_ARN=$(aws elbv2 create-load-balancer \
        --name "$ALB_NAME" \
        --subnets "$SUBNET_1" "$SUBNET_2" \
        --security-groups "$SECURITY_GROUP" \
        --scheme internet-facing \
        --type application \
        --ip-address-type ipv4 \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].LoadBalancerArn' \
        --output text)
    
    echo "ALB created: $ALB_ARN"
    
    # Create Target Group with target-type ip (required for Fargate)
    TG_NAME="modresorts-tg"
    echo "Creating Target Group: $TG_NAME"
    TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
        --name "$TG_NAME" \
        --protocol HTTP \
        --port 8080 \
        --vpc-id "$VPC_ID" \
        --target-type ip \
        --health-check-enabled \
        --health-check-protocol HTTP \
        --health-check-path "/health" \
        --health-check-interval-seconds 30 \
        --health-check-timeout-seconds 5 \
        --healthy-threshold-count 2 \
        --unhealthy-threshold-count 3 \
        --region "$AWS_REGION" \
        --query 'TargetGroups[0].TargetGroupArn' \
        --output text)
    
    echo "Target Group created: $TARGET_GROUP_ARN"
    
    # Create Listener
    echo "Creating ALB Listener..."
    aws elbv2 create-listener \
        --load-balancer-arn "$ALB_ARN" \
        --protocol HTTP \
        --port 80 \
        --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
        --region "$AWS_REGION"
    
    echo "Listener created successfully"
    
    # Get ALB DNS name
    ALB_DNS=$(aws elbv2 describe-load-balancers \
        --load-balancer-arns "$ALB_ARN" \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].DNSName' \
        --output text)
    
    echo "ALB DNS Name: $ALB_DNS"
fi

# Get AWS Account ID
echo ""
echo "Retrieving AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account ID: $ACCOUNT_ID"

# Check if ECS cluster exists, create if it doesn't
echo ""
echo "Checking if ECS cluster exists..."
aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo "Cluster does not exist. Creating ECS cluster: $CLUSTER_NAME"
    aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
}

# Create CloudWatch log group
echo ""
echo "Creating CloudWatch log group..."
aws logs create-log-group --log-group-name "/ecs/$PROJECT_NAME" --region "$AWS_REGION" 2>/dev/null || echo "Log group already exists"

# Prepare task definition JSON
echo ""
echo "Preparing task definition..."
TASK_DEF_FILE="ecs/task-definition.json"
TASK_DEF_TEMP="/tmp/task-definition-$$.json"

cp "$TASK_DEF_FILE" "$TASK_DEF_TEMP"

# Replace placeholders in task definition
sed -i.bak "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TASK_DEF_TEMP"
sed -i.bak "s|{{AWS_REGION}}|$AWS_REGION|g" "$TASK_DEF_TEMP"
sed -i.bak "s|{{ACCOUNT_ID}}|$ACCOUNT_ID|g" "$TASK_DEF_TEMP"

# Register task definition
echo "Registering task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
    --cli-input-json file://"$TASK_DEF_TEMP" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo "Task definition registered: $TASK_DEF_ARN"

# Clean up temp file
rm -f "$TASK_DEF_TEMP" "$TASK_DEF_TEMP.bak"

# Prepare service definition JSON
echo ""
echo "Preparing service definition..."
SERVICE_DEF_FILE="ecs/service-definition.json"
SERVICE_DEF_TEMP="/tmp/service-definition-$$.json"

cp "$SERVICE_DEF_FILE" "$SERVICE_DEF_TEMP"

# Replace placeholders in service definition
sed -i.bak "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g" "$SERVICE_DEF_TEMP"
sed -i.bak "s|{{SUBNET_1}}|$SUBNET_1|g" "$SERVICE_DEF_TEMP"
sed -i.bak "s|{{SUBNET_2}}|$SUBNET_2|g" "$SERVICE_DEF_TEMP"
sed -i.bak "s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g" "$SERVICE_DEF_TEMP"

if [[ "$NEED_LB" == "y" || "$NEED_LB" == "Y" ]]; then
    sed -i.bak "s|{{TARGET_GROUP_ARN}}|$TARGET_GROUP_ARN|g" "$SERVICE_DEF_TEMP"
else
    # Remove loadBalancers section if no LB needed
    python3 -c "
import json
import sys
with open('$SERVICE_DEF_TEMP', 'r') as f:
    data = json.load(f)
if 'loadBalancers' in data:
    del data['loadBalancers']
if 'healthCheckGracePeriodSeconds' in data:
    del data['healthCheckGracePeriodSeconds']
with open('$SERVICE_DEF_TEMP', 'w') as f:
    json.dump(data, f, indent=2)
" 2>/dev/null || {
    # Fallback if python3 is not available
    echo "Warning: Could not remove loadBalancers section. Please ensure it's configured correctly."
}
fi

# Check if service exists
echo ""
echo "Checking if service exists..."
EXISTING_SERVICE=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[?status==`ACTIVE`].serviceName' \
    --output text 2>/dev/null || echo "")

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" == "None" ]; then
    # Create new service
    echo "Service does not exist. Creating new service..."
    aws ecs create-service \
        --cli-input-json file://"$SERVICE_DEF_TEMP" \
        --region "$AWS_REGION"
    
    echo "Service created successfully"
else
    # Update existing service
    echo "Service exists. Updating service with new task definition..."
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --task-definition "$TASK_DEF_ARN" \
        --force-new-deployment \
        --region "$AWS_REGION"
    
    echo "Service updated successfully"
fi

# Clean up temp file
rm -f "$SERVICE_DEF_TEMP" "$SERVICE_DEF_TEMP.bak"

# Wait for service to stabilize
echo ""
echo "Waiting for service to stabilize (this may take a few minutes)..."
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION"

# Verify deployment
echo ""
echo "=========================================="
echo "Deployment Status"
echo "=========================================="

SERVICE_INFO=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0]')

RUNNING_COUNT=$(echo "$SERVICE_INFO" | grep -o '"runningCount": [0-9]*' | grep -o '[0-9]*')
DESIRED_COUNT=$(echo "$SERVICE_INFO" | grep -o '"desiredCount": [0-9]*' | grep -o '[0-9]*')

echo "Service: $SERVICE_NAME"
echo "Cluster: $CLUSTER_NAME"
echo "Running Tasks: $RUNNING_COUNT"
echo "Desired Tasks: $DESIRED_COUNT"
echo ""

if [[ "$NEED_LB" == "y" || "$NEED_LB" == "Y" ]]; then
    echo "Load Balancer DNS: $ALB_DNS"
    echo "Application URL: http://$ALB_DNS"
    echo ""
fi

echo "CloudWatch Logs: /ecs/$PROJECT_NAME"
echo ""
echo "=========================================="
echo "Deployment completed successfully!"
echo "=========================================="
echo ""
echo "To view logs, run:"
echo "aws logs tail /ecs/$PROJECT_NAME --follow --region $AWS_REGION"
echo ""
