#!/bin/bash

# AWS ECS Fargate Deployment Script for ModResorts Application
# This script deploys the containerized application to AWS ECS Fargate

set -e  # Exit on error
set -o pipefail  # Exit on pipe failure

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}ModResorts ECS Fargate Deployment Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Prompt for AWS configuration
echo -e "${YELLOW}AWS Configuration${NC}"
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter ECS Cluster Name: " CLUSTER_NAME
echo ""

# Get AWS Account ID
echo -e "${BLUE}Retrieving AWS Account ID...${NC}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to retrieve AWS Account ID. Please check your AWS credentials.${NC}"
    exit 1
fi
echo -e "${GREEN}Account ID: ${ACCOUNT_ID}${NC}"
echo ""

# Check if cluster exists, create if not
echo -e "${BLUE}Checking ECS cluster...${NC}"
aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo -e "${YELLOW}Cluster does not exist. Creating ECS cluster: ${CLUSTER_NAME}${NC}"
    aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to create ECS cluster.${NC}"
        exit 1
    fi
    echo -e "${GREEN}ECS cluster created successfully!${NC}"
}
echo ""

# Prompt for network configuration
echo -e "${YELLOW}Network Configuration${NC}"
read -p "Enter VPC ID: " VPC_ID
read -p "Enter Subnet IDs (comma-separated, at least 2): " SUBNETS_INPUT
read -p "Enter Security Group ID: " SECURITY_GROUP
echo ""

# Parse subnets
IFS=',' read -ra SUBNETS <<< "$SUBNETS_INPUT"
SUBNET_1=$(echo "${SUBNETS[0]}" | xargs)
SUBNET_2=$(echo "${SUBNETS[1]}" | xargs)

if [ -z "$SUBNET_1" ] || [ -z "$SUBNET_2" ]; then
    echo -e "${RED}At least 2 subnets are required for high availability.${NC}"
    exit 1
fi

# Prompt for Docker image URI
echo -e "${YELLOW}Docker Image Configuration${NC}"
read -p "Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI
echo ""

# Load balancer configuration
echo -e "${YELLOW}Load Balancer Configuration${NC}"
read -p "Do you need a load balancer for this service? (y/n): " NEED_LB

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}Creating Application Load Balancer and Target Group...${NC}"
    
    # Create ALB
    ALB_NAME="modresorts-alb"
    echo -e "${BLUE}Creating Application Load Balancer: ${ALB_NAME}${NC}"
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
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to create Application Load Balancer.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}ALB created: ${ALB_ARN}${NC}"
    
    # Get ALB DNS name
    ALB_DNS=$(aws elbv2 describe-load-balancers \
        --load-balancer-arns "$ALB_ARN" \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].DNSName' \
        --output text)
    
    # Create Target Group with target-type ip (required for Fargate)
    TG_NAME="modresorts-tg"
    echo -e "${BLUE}Creating Target Group: ${TG_NAME}${NC}"
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
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to create Target Group.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}Target Group created: ${TARGET_GROUP_ARN}${NC}"
    
    # Create Listener
    echo -e "${BLUE}Creating ALB Listener...${NC}"
    aws elbv2 create-listener \
        --load-balancer-arn "$ALB_ARN" \
        --protocol HTTP \
        --port 80 \
        --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
        --region "$AWS_REGION" >/dev/null
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to create ALB Listener.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}ALB Listener created successfully!${NC}"
    echo ""
    
    USE_LB=true
else
    echo -e "${YELLOW}Skipping load balancer creation.${NC}"
    USE_LB=false
    echo ""
fi

# Create CloudWatch Log Group
LOG_GROUP="/ecs/modresorts"
echo -e "${BLUE}Creating CloudWatch Log Group: ${LOG_GROUP}${NC}"
aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$AWS_REGION" 2>/dev/null || echo -e "${YELLOW}Log group already exists.${NC}"
echo ""

# Replace placeholders in task definition
echo -e "${BLUE}Preparing task definition...${NC}"
TASK_DEF_FILE="ecs/task-definition.json"
TASK_DEF_TEMP="ecs/task-definition-temp.json"

sed "s|{{IMAGE_URI}}|${IMAGE_URI}|g; s|{{AWS_REGION}}|${AWS_REGION}|g; s|{{ACCOUNT_ID}}|${ACCOUNT_ID}|g" "$TASK_DEF_FILE" > "$TASK_DEF_TEMP"

# Register task definition
echo -e "${BLUE}Registering ECS task definition...${NC}"
TASK_DEF_ARN=$(aws ecs register-task-definition \
    --cli-input-json file://"$TASK_DEF_TEMP" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to register task definition.${NC}"
    rm -f "$TASK_DEF_TEMP"
    exit 1
fi

echo -e "${GREEN}Task definition registered: ${TASK_DEF_ARN}${NC}"
rm -f "$TASK_DEF_TEMP"
echo ""

# Prepare service definition
echo -e "${BLUE}Preparing service definition...${NC}"
SERVICE_DEF_FILE="ecs/service-definition.json"
SERVICE_DEF_TEMP="ecs/service-definition-temp.json"

# Replace placeholders
sed "s|{{CLUSTER_NAME}}|${CLUSTER_NAME}|g; s|{{SUBNET_1}}|${SUBNET_1}|g; s|{{SUBNET_2}}|${SUBNET_2}|g; s|{{SECURITY_GROUP}}|${SECURITY_GROUP}|g" "$SERVICE_DEF_FILE" > "$SERVICE_DEF_TEMP"

# Handle load balancer configuration
if [ "$USE_LB" = true ]; then
    # Replace TARGET_GROUP_ARN placeholder
    sed -i "s|{{TARGET_GROUP_ARN}}|${TARGET_GROUP_ARN}|g" "$SERVICE_DEF_TEMP"
else
    # Remove loadBalancers section from service definition
    python3 -c "
import json
import sys

with open('$SERVICE_DEF_TEMP', 'r') as f:
    service_def = json.load(f)

# Remove loadBalancers and healthCheckGracePeriodSeconds
service_def.pop('loadBalancers', None)
service_def.pop('healthCheckGracePeriodSeconds', None)

with open('$SERVICE_DEF_TEMP', 'w') as f:
    json.dump(service_def, f, indent=2)
" 2>/dev/null || {
        # Fallback if python3 is not available
        echo -e "${YELLOW}Warning: Could not remove loadBalancers section. Proceeding anyway.${NC}"
    }
fi

# Check if service exists
SERVICE_NAME="modresorts-service"
echo -e "${BLUE}Checking if service exists...${NC}"
EXISTING_SERVICE=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[?status==`ACTIVE`].serviceName' \
    --output text 2>/dev/null)

if [ -n "$EXISTING_SERVICE" ] && [ "$EXISTING_SERVICE" != "None" ]; then
    # Update existing service
    echo -e "${YELLOW}Service exists. Updating service...${NC}"
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --task-definition "$TASK_DEF_ARN" \
        --desired-count 2 \
        --region "$AWS_REGION" >/dev/null
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to update service.${NC}"
        rm -f "$SERVICE_DEF_TEMP"
        exit 1
    fi
    
    echo -e "${GREEN}Service updated successfully!${NC}"
else
    # Create new service
    echo -e "${YELLOW}Service does not exist. Creating new service...${NC}"
    aws ecs create-service \
        --cli-input-json file://"$SERVICE_DEF_TEMP" \
        --region "$AWS_REGION" >/dev/null
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to create service.${NC}"
        rm -f "$SERVICE_DEF_TEMP"
        exit 1
    fi
    
    echo -e "${GREEN}Service created successfully!${NC}"
fi

rm -f "$SERVICE_DEF_TEMP"
echo ""

# Wait for service to stabilize
echo -e "${BLUE}Waiting for service to stabilize (this may take a few minutes)...${NC}"
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION"

if [ $? -ne 0 ]; then
    echo -e "${RED}Service failed to stabilize. Check ECS console for details.${NC}"
    exit 1
fi

echo -e "${GREEN}Service is stable!${NC}"
echo ""

# Display deployment information
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment Successful!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}Deployment Details:${NC}"
echo -e "  Cluster: ${CLUSTER_NAME}"
echo -e "  Service: ${SERVICE_NAME}"
echo -e "  Task Definition: ${TASK_DEF_ARN}"
echo -e "  Region: ${AWS_REGION}"
echo ""

if [ "$USE_LB" = true ]; then
    echo -e "${BLUE}Application Access:${NC}"
    echo -e "  Load Balancer DNS: ${ALB_DNS}"
    echo -e "  Application URL: http://${ALB_DNS}"
    echo -e "  Health Check: http://${ALB_DNS}/health"
    echo ""
fi

echo -e "${BLUE}CloudWatch Logs:${NC}"
echo -e "  Log Group: ${LOG_GROUP}"
echo -e "  View logs: aws logs tail ${LOG_GROUP} --follow --region ${AWS_REGION}"
echo ""

echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Verify the application is running: aws ecs describe-services --cluster ${CLUSTER_NAME} --services ${SERVICE_NAME} --region ${AWS_REGION}"
echo "2. Check CloudWatch logs for application output"
if [ "$USE_LB" = true ]; then
    echo "3. Access the application at: http://${ALB_DNS}"
fi
echo ""
