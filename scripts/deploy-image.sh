#!/bin/bash
# =============================================================================
# deploy-image.sh - Deploy ModResorts to AWS ECS Fargate
# Usage: ./scripts/deploy-image.sh
# Prerequisites: AWS CLI configured, ECS task/service definition files present
# =============================================================================

set -e
set -o pipefail

PROJECT_NAME="modresorts"
TASK_DEF_FILE="ecs/task-definition.json"
SERVICE_DEF_FILE="ecs/service-definition.json"
LOG_GROUP="/ecs/${PROJECT_NAME}"

echo "=============================================="
echo "  ModResorts - AWS ECS Fargate Deployment"
echo "=============================================="
echo ""

# ---- Collect Configuration ----
read -p "Enter AWS Region [us-east-1]: " AWS_REGION_INPUT
AWS_REGION="${AWS_REGION_INPUT:-us-east-1}"

read -p "Enter ECS Cluster name [modresorts-cluster]: " CLUSTER_NAME_INPUT
CLUSTER_NAME="${CLUSTER_NAME_INPUT:-modresorts-cluster}"

read -p "Enter ECR Image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Image URI is required."
  exit 1
fi

echo ""
echo "--- Network Configuration ---"
read -p "Enter VPC ID (e.g. vpc-xxxxxxxx): " VPC_ID
if [ -z "$VPC_ID" ]; then
  echo "ERROR: VPC ID is required."
  exit 1
fi

read -p "Enter Subnet IDs (comma-separated, e.g. subnet-aaa,subnet-bbb): " SUBNETS_INPUT
if [ -z "$SUBNETS_INPUT" ]; then
  echo "ERROR: At least one subnet ID is required."
  exit 1
fi

# Parse subnets into array
IFS=',' read -ra SUBNET_ARRAY <<< "$SUBNETS_INPUT"
SUBNET_1="${SUBNET_ARRAY[0]}"
SUBNET_2="${SUBNET_ARRAY[1]:-${SUBNET_ARRAY[0]}}"

read -p "Enter Security Group ID (e.g. sg-xxxxxxxx): " SECURITY_GROUP
if [ -z "$SECURITY_GROUP" ]; then
  echo "ERROR: Security Group ID is required."
  exit 1
fi

# ---- Get AWS Account ID ----
echo ""
echo "Retrieving AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
if [ -z "$ACCOUNT_ID" ]; then
  echo "ERROR: Could not retrieve AWS Account ID. Ensure AWS CLI is configured."
  exit 1
fi
echo "AWS Account ID: $ACCOUNT_ID"

# ---- Ensure CloudWatch Log Group Exists ----
echo ""
echo "Ensuring CloudWatch log group '$LOG_GROUP' exists..."
aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$AWS_REGION" 2>/dev/null || true
echo "Log group ready: $LOG_GROUP"

# ---- Check / Create ECS Cluster ----
echo ""
echo "Checking ECS cluster '$CLUSTER_NAME'..."
CLUSTER_STATUS=$(aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" \
  --query "clusters[0].status" --output text 2>/dev/null || echo "MISSING")

if [ "$CLUSTER_STATUS" != "ACTIVE" ]; then
  echo "Cluster not found or inactive. Creating cluster '$CLUSTER_NAME'..."
  aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
  echo "Cluster created."
else
  echo "Cluster '$CLUSTER_NAME' is ACTIVE."
fi

# ---- Load Balancer Prompt ----
echo ""
read -p "Do you need an Application Load Balancer for this service? (y/n) [n]: " NEED_LB_INPUT
NEED_LB="${NEED_LB_INPUT:-n}"

TARGET_GROUP_ARN=""
LB_DNS=""

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
  echo ""
  echo "--- Creating Application Load Balancer ---"

  # Create ALB
  echo "Creating Application Load Balancer..."
  ALB_ARN=$(aws elbv2 create-load-balancer \
    --name "${PROJECT_NAME}-alb" \
    --subnets "${SUBNET_ARRAY[@]}" \
    --security-groups "$SECURITY_GROUP" \
    --scheme internet-facing \
    --type application \
    --ip-address-type ipv4 \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].LoadBalancerArn" \
    --output text)
  echo "ALB ARN: $ALB_ARN"

  LB_DNS=$(aws elbv2 describe-load-balancers \
    --load-balancer-arns "$ALB_ARN" \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].DNSName" \
    --output text)

  # Create Target Group (target-type ip required for Fargate awsvpc mode)
  echo "Creating Target Group..."
  TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
    --name "${PROJECT_NAME}-tg" \
    --protocol HTTP \
    --port 9080 \
    --vpc-id "$VPC_ID" \
    --target-type ip \
    --health-check-protocol HTTP \
    --health-check-path "/resorts/health" \
    --health-check-interval-seconds 30 \
    --health-check-timeout-seconds 10 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 3 \
    --region "$AWS_REGION" \
    --query "TargetGroups[0].TargetGroupArn" \
    --output text)
  echo "Target Group ARN: $TARGET_GROUP_ARN"

  # Create ALB Listener
  echo "Creating ALB Listener on port 80..."
  aws elbv2 create-listener \
    --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP \
    --port 80 \
    --default-actions "Type=forward,TargetGroupArn=${TARGET_GROUP_ARN}" \
    --region "$AWS_REGION" \
    --output text > /dev/null
  echo "ALB Listener created."
fi

# ---- Prepare Task Definition ----
echo ""
echo "Preparing task definition..."
cp "$TASK_DEF_FILE" /tmp/task-definition-deploy.json

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g" /tmp/task-definition-deploy.json
sed -i "s|{{AWS_REGION}}|${AWS_REGION}|g" /tmp/task-definition-deploy.json
sed -i "s|{{ACCOUNT_ID}}|${ACCOUNT_ID}|g" /tmp/task-definition-deploy.json

# ---- Register Task Definition ----
echo "Registering ECS task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json file:///tmp/task-definition-deploy.json \
  --region "$AWS_REGION" \
  --query "taskDefinition.taskDefinitionArn" \
  --output text)
echo "Task Definition ARN: $TASK_DEF_ARN"

# ---- Prepare Service Definition ----
echo ""
echo "Preparing service definition..."
cp "$SERVICE_DEF_FILE" /tmp/service-definition-deploy.json

sed -i "s|{{CLUSTER_NAME}}|${CLUSTER_NAME}|g" /tmp/service-definition-deploy.json
sed -i "s|{{SUBNET_1}}|${SUBNET_1}|g" /tmp/service-definition-deploy.json
sed -i "s|{{SUBNET_2}}|${SUBNET_2}|g" /tmp/service-definition-deploy.json
sed -i "s|{{SECURITY_GROUP}}|${SECURITY_GROUP}|g" /tmp/service-definition-deploy.json

# Add load balancer configuration if needed
if [[ "$NEED_LB" =~ ^[Yy]$ ]] && [ -n "$TARGET_GROUP_ARN" ]; then
  # Inject loadBalancers and healthCheckGracePeriodSeconds into service definition
  python3 -c "
import json, sys
with open('/tmp/service-definition-deploy.json') as f:
    svc = json.load(f)
svc['loadBalancers'] = [{
    'targetGroupArn': '${TARGET_GROUP_ARN}',
    'containerName': '${PROJECT_NAME}',
    'containerPort': 9080
}]
svc['healthCheckGracePeriodSeconds'] = 300
with open('/tmp/service-definition-deploy.json', 'w') as f:
    json.dump(svc, f, indent=2)
print('Load balancer configuration injected.')
" 2>/dev/null || {
    # Fallback: use sed if python3 not available
    sed -i "s|\"desiredCount\"|\"loadBalancers\": [{\"targetGroupArn\": \"${TARGET_GROUP_ARN}\", \"containerName\": \"${PROJECT_NAME}\", \"containerPort\": 9080}], \"healthCheckGracePeriodSeconds\": 300, \"desiredCount\"|g" /tmp/service-definition-deploy.json
  }
fi

# ---- Create or Update ECS Service ----
SERVICE_NAME="${PROJECT_NAME}-service"
echo ""
echo "Checking if ECS service '$SERVICE_NAME' exists..."

EXISTING_SERVICE=$(aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[?status=='ACTIVE'].serviceName" \
  --output text 2>/dev/null || echo "")

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" = "None" ]; then
  echo "Service does not exist. Creating new ECS service '$SERVICE_NAME'..."
  aws ecs create-service \
    --cli-input-json file:///tmp/service-definition-deploy.json \
    --region "$AWS_REGION"
  echo "ECS service created."
else
  echo "Service '$SERVICE_NAME' exists. Updating service with new task definition..."
  aws ecs update-service \
    --cluster "$CLUSTER_NAME" \
    --service "$SERVICE_NAME" \
    --task-definition "$TASK_DEF_ARN" \
    --region "$AWS_REGION" \
    --output text > /dev/null
  echo "ECS service updated."
fi

# ---- Wait for Service Stability ----
echo ""
echo "Waiting for service to reach stable state (this may take several minutes)..."
aws ecs wait services-stable \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION"
echo "Service is stable."

# ---- Verify Deployment ----
echo ""
echo "=============================================="
echo "  Deployment Verification"
echo "=============================================="
aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[0].{ServiceName:serviceName,Status:status,DesiredCount:desiredCount,RunningCount:runningCount,PendingCount:pendingCount}" \
  --output table

echo ""
echo "CloudWatch Log Group: $LOG_GROUP"
echo "  View logs: aws logs tail $LOG_GROUP --follow --region $AWS_REGION"

if [ -n "$LB_DNS" ]; then
  echo ""
  echo "Load Balancer DNS: http://$LB_DNS"
  echo "Application URL:   http://$LB_DNS/resorts/"
  echo "Health Check URL:  http://$LB_DNS/resorts/health"
fi

echo ""
echo "=============================================="
echo "  Deployment Complete!"
echo "=============================================="
echo ""
echo "Troubleshooting tips:"
echo "  - View task failures: aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks <task-arn> --region $AWS_REGION"
echo "  - View service events: aws ecs describe-services --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $AWS_REGION"
echo "  - Check logs: aws logs tail $LOG_GROUP --follow --region $AWS_REGION"
echo ""
