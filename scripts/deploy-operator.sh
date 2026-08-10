#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Deploying K8s AI Operator & Custom Resources"
echo "=========================================="

NAMESPACE=${1:-tuluat-system}
SKIP_OPERATOR_MANIFESTS=${2:-${SKIP_OPERATOR_MANIFESTS:-false}}

if [ "${SKIP_OPERATOR_MANIFESTS}" != "true" ] && [ "${SKIP_OPERATOR_MANIFESTS}" != "--skip-operator-manifests" ]; then
  echo "0. Creating Namespace..."
  kubectl apply -f manifests/operator/namespace.yaml
fi

echo "1. Applying Custom Resource Definitions (CRDs)..."
kubectl apply -f manifests/crd/llmproviders.ai.tuluat.com.yaml
kubectl apply -f manifests/crd/aiagents.ai.tuluat.com.yaml
kubectl apply -f manifests/crd/aiworkflows.ai.tuluat.com.yaml
kubectl apply -f manifests/crd/workflowsessions.ai.tuluat.com.yaml
kubectl apply -f manifests/crd/mcpservers.ai.tuluat.com.yaml

if [ "${SKIP_OPERATOR_MANIFESTS}" != "true" ] && [ "${SKIP_OPERATOR_MANIFESTS}" != "--skip-operator-manifests" ]; then
  echo "2. Applying RBAC, ServiceAccount, Database, and Services..."
  kubectl apply -f manifests/operator/rbac.yaml
  kubectl apply -f manifests/operator/service.yaml
fi
echo "3. Applying Telemetry, Temporal, and WireMock Stub Infrastructure..."
kubectl apply -f manifests/telemetry/prometheus-grafana.yaml
kubectl apply -f manifests/telemetry/prometheus-servicemonitor.yaml || true
kubectl apply -f manifests/temporal/temporal-cluster.yaml
kubectl apply -f manifests/testing/wiremock-stub.yaml

echo "4. Applying Sample Custom Resources & Kustomize Dynamic Secrets..."
kubectl apply -k config/

echo "5. Checking status of CRDs and Custom Resources..."
kubectl get crds | grep ai.tuluat.com || true
kubectl get llmproviders -n "$NAMESPACE" || true
kubectl get aiagents -n "$NAMESPACE" || true
kubectl get aiworkflows -n "$NAMESPACE" || true
kubectl get workflowsessions -n "$NAMESPACE" || true
kubectl get mcpservers -n "$NAMESPACE" || true

echo "Deployment finished successfully."
