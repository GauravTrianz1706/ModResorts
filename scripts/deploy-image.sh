#!/bin/bash
set -e
set -o pipefail

APP_NAME="modresorts"
NAMESPACE="modresorts"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "============================================"
echo "  ModResorts - Deploy to Azure AKS"
echo "============================================"
echo ""

# ---- Prompt for Azure / AKS details ----
read -p "Enter Azure Resource Group name: " RESOURCE_GROUP
if [ -z "$RESOURCE_GROUP" ]; then
  echo "ERROR: Resource group cannot be empty." >&2
  exit 1
fi

read -p "Enter AKS Cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: AKS cluster name cannot be empty." >&2
  exit 1
fi

read -p "Enter full Docker image URI (e.g. myregistry.azurecr.io/modresorts:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Image URI cannot be empty." >&2
  exit 1
fi

echo ""
echo "---- Application Environment Variables ----"
echo "Press Enter to skip any optional variable."
echo ""

read -p "Enter WEATHER_API_KEY (Weather Underground API key, or Enter to skip): " WEATHER_API_KEY_VAL
read -p "Enter JNDI_FACTORY (or Enter for default 'com.sun.jndi.fscontext.RefFSContextFactory'): " JNDI_FACTORY_VAL
if [ -z "$JNDI_FACTORY_VAL" ]; then
  JNDI_FACTORY_VAL="com.sun.jndi.fscontext.RefFSContextFactory"
fi
read -p "Enter JNDI_PROVIDER_URL (or Enter to skip): " JNDI_PROVIDER_URL_VAL
read -p "Enter SERVER_DISPLAY_NAME (or Enter for 'modresorts'): " SERVER_DISPLAY_NAME_VAL
if [ -z "$SERVER_DISPLAY_NAME_VAL" ]; then
  SERVER_DISPLAY_NAME_VAL="modresorts"
fi
read -p "Enter SERVER_FULL_NAME (or Enter for 'modresorts'): " SERVER_FULL_NAME_VAL
if [ -z "$SERVER_FULL_NAME_VAL" ]; then
  SERVER_FULL_NAME_VAL="modresorts"
fi

echo ""
echo "---- Configuring kubectl for AKS ----"
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing
if [ $? -ne 0 ]; then
  echo "ERROR: Failed to get AKS credentials." >&2
  exit 1
fi

echo ""
echo "Verifying cluster connectivity ..."
kubectl cluster-info || { echo "ERROR: Cannot connect to AKS cluster." >&2; exit 1; }

echo ""
echo "---- Updating Kubernetes manifests ----"
# Work on copies to avoid modifying originals
DEPLOY_DIR="$(mktemp -d)"
cp -r "$PROJECT_ROOT/kubernetes/." "$DEPLOY_DIR/"

# Replace placeholders using pipe delimiter
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                         "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{WEATHER_API_KEY}}|${WEATHER_API_KEY_VAL}|g"         "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{JNDI_FACTORY}}|${JNDI_FACTORY_VAL}|g"              "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{JNDI_PROVIDER_URL}}|${JNDI_PROVIDER_URL_VAL}|g"    "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{SERVER_DISPLAY_NAME}}|${SERVER_DISPLAY_NAME_VAL}|g" "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{SERVER_FULL_NAME}}|${SERVER_FULL_NAME_VAL}|g"       "$DEPLOY_DIR/deployment.yaml"

echo ""
echo "---- Applying Kubernetes manifests ----"
echo "Applying namespace ..."
kubectl apply -f "$DEPLOY_DIR/namespace.yaml"

echo "Applying deployment ..."
kubectl apply -f "$DEPLOY_DIR/deployment.yaml"

echo "Applying service ..."
kubectl apply -f "$DEPLOY_DIR/service.yaml"

echo "Applying ingress ..."
kubectl apply -f "$DEPLOY_DIR/ingress.yaml"

echo ""
echo "---- Waiting for rollout ----"
kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s
if [ $? -ne 0 ]; then
  echo "ERROR: Deployment rollout failed. Rolling back ..." >&2
  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}
  echo "Rollback initiated. Check pod status with: kubectl get pods -n ${NAMESPACE}"
  exit 1
fi

echo ""
echo "---- Verifying resources ----"
kubectl get pods,svc,ingress -n ${NAMESPACE}

echo ""
echo "---- Application Access ----"
INGRESS_IP=$(kubectl get ingress modresorts-ingress -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
INGRESS_HOST=$(kubectl get ingress modresorts-ingress -n ${NAMESPACE} -o jsonpath='{.spec.rules[0].host}' 2>/dev/null || echo "modresorts.example.com")

if [ -n "$INGRESS_IP" ]; then
  echo "Application URL: http://${INGRESS_IP}/resorts/"
else
  echo "Application URL: http://${INGRESS_HOST}/resorts/"
  echo "Note: Update your DNS to point ${INGRESS_HOST} to the ingress IP."
fi

echo ""
echo "Health check: http://${INGRESS_HOST}/resorts/health"
echo ""
echo "============================================"
echo "  Deployment completed successfully!"
echo "============================================"

# Cleanup temp dir
rm -rf "$DEPLOY_DIR"
