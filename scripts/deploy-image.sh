#!/bin/bash
set -e
set -o pipefail

# =============================================================
# deploy-image.sh - Deploy ModResorts to AWS EKS
# Usage: ./scripts/deploy-image.sh
# Run from repository root directory
# Prerequisites: aws-cli, kubectl
# =============================================================

echo "=============================================="
echo "  ModResorts - AWS EKS Deployment Script"
echo "=============================================="
echo ""

# ---- Collect AWS / EKS configuration ----
read -p "Enter AWS Region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS Region is required."
  exit 1
fi

read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required."
  exit 1
fi

read -p "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required."
  exit 1
fi

echo ""
echo "--- Optional Application Environment Variables ---"
echo "Press Enter to skip any variable (placeholder will remain in manifest)."
echo ""

read -p "Enter WEATHER_API_KEY value (or press Enter to skip): " WEATHER_API_KEY_VAL
read -p "Enter SERVER_DISPLAY_NAME value [modresorts-server]: " SERVER_DISPLAY_NAME_VAL
SERVER_DISPLAY_NAME_VAL="${SERVER_DISPLAY_NAME_VAL:-modresorts-server}"
read -p "Enter SERVER_FULL_NAME value [modresorts-server/default]: " SERVER_FULL_NAME_VAL
SERVER_FULL_NAME_VAL="${SERVER_FULL_NAME_VAL:-modresorts-server/default}"
read -p "Enter JNDI_FACTORY value [com.sun.jndi.rmi.registry.RegistryContextFactory]: " JNDI_FACTORY_VAL
JNDI_FACTORY_VAL="${JNDI_FACTORY_VAL:-com.sun.jndi.rmi.registry.RegistryContextFactory}"
read -p "Enter JNDI_PROVIDER_URL value [rmi://localhost:1099]: " JNDI_PROVIDER_URL_VAL
JNDI_PROVIDER_URL_VAL="${JNDI_PROVIDER_URL_VAL:-rmi://localhost:1099}"
read -p "Enter DB_HOST value (or press Enter to skip): " DB_HOST_VAL
read -p "Enter DB_PORT value [5432]: " DB_PORT_VAL
DB_PORT_VAL="${DB_PORT_VAL:-5432}"
read -p "Enter DB_NAME value [modresorts]: " DB_NAME_VAL
DB_NAME_VAL="${DB_NAME_VAL:-modresorts}"
read -p "Enter DB_USERNAME value [modresorts]: " DB_USERNAME_VAL
DB_USERNAME_VAL="${DB_USERNAME_VAL:-modresorts}"
read -p "Enter DB_PASSWORD value (or press Enter to skip): " DB_PASSWORD_VAL

echo ""
echo "--- Configuring kubectl for EKS cluster: $CLUSTER_NAME ---"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
if [ $? -ne 0 ]; then
  echo "ERROR: Failed to configure kubectl for EKS cluster."
  exit 1
fi

echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to Kubernetes cluster."; exit 1; }

echo ""
echo "--- Updating Kubernetes manifests with deployment values ---"

# Work on copies to avoid modifying originals
cp -r kubernetes kubernetes_deploy_tmp

# Replace IMAGE_URI placeholder
sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' kubernetes_deploy_tmp/deployment.yaml

# Replace environment variable placeholders
sed -i 's|{{WEATHER_API_KEY}}|'"$WEATHER_API_KEY_VAL"'|g'       kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{SERVER_DISPLAY_NAME}}|'"$SERVER_DISPLAY_NAME_VAL"'|g' kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{SERVER_FULL_NAME}}|'"$SERVER_FULL_NAME_VAL"'|g'       kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{JNDI_FACTORY}}|'"$JNDI_FACTORY_VAL"'|g'               kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{JNDI_PROVIDER_URL}}|'"$JNDI_PROVIDER_URL_VAL"'|g'     kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{DB_HOST}}|'"$DB_HOST_VAL"'|g'                         kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{DB_PORT}}|'"$DB_PORT_VAL"'|g'                         kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{DB_NAME}}|'"$DB_NAME_VAL"'|g'                         kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{DB_USERNAME}}|'"$DB_USERNAME_VAL"'|g'                 kubernetes_deploy_tmp/deployment.yaml
sed -i 's|{{DB_PASSWORD}}|'"$DB_PASSWORD_VAL"'|g'                 kubernetes_deploy_tmp/deployment.yaml

echo ""
echo "--- Applying Kubernetes manifests ---"

echo "Applying namespace..."
kubectl apply -f kubernetes_deploy_tmp/namespace.yaml

echo "Applying deployment..."
kubectl apply -f kubernetes_deploy_tmp/deployment.yaml

echo "Applying service..."
kubectl apply -f kubernetes_deploy_tmp/service.yaml

echo "Applying ingress..."
kubectl apply -f kubernetes_deploy_tmp/ingress.yaml

# Clean up temporary manifests
rm -rf kubernetes_deploy_tmp

echo ""
echo "--- Waiting for deployment rollout ---"
kubectl rollout status deployment/modresorts -n modresorts --timeout=300s
if [ $? -ne 0 ]; then
  echo "ERROR: Deployment rollout failed or timed out."
  echo "To rollback, run: kubectl rollout undo deployment/modresorts -n modresorts"
  exit 1
fi

echo ""
echo "--- Verifying deployed resources ---"
kubectl get pods,svc,ingress -n modresorts

echo ""
echo "--- Application Access URL ---"
INGRESS_HOST=$(kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")
if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://$INGRESS_HOST/resorts/"
  echo "Health Check:    http://$INGRESS_HOST/resorts/health"
else
  echo "Ingress hostname is still provisioning. Run the following to check:"
  echo "  kubectl get ingress modresorts-ingress -n modresorts"
fi

echo ""
echo "=============================================="
echo "  SUCCESS: ModResorts deployed to EKS!"
echo "=============================================="
echo ""
echo "Useful commands:"
echo "  kubectl get pods -n modresorts"
echo "  kubectl logs -f deployment/modresorts -n modresorts"
echo "  kubectl rollout undo deployment/modresorts -n modresorts  # rollback"
