#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Deploying K8s AI Operator & Custom Resources"
echo "=========================================="

NAMESPACE=${1:-tuluat-system}
SKIP_OPERATOR_MANIFESTS=${2:-${SKIP_OPERATOR_MANIFESTS:-false}}
E2E_MODE=${E2E_MODE:-false}
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

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
echo "3. Applying Telemetry, Temporal, MinIO, and WireMock Stub Infrastructure..."
kubectl apply -f manifests/telemetry/prometheus-grafana.yaml
kubectl apply -f manifests/telemetry/prometheus-servicemonitor.yaml || true
kubectl apply -f manifests/temporal/temporal-cluster.yaml
kubectl apply -f manifests/storage/minio.yaml
kubectl apply -f manifests/testing/wiremock-stub.yaml
if [ "${E2E_MODE}" = "true" ]; then
  echo "4. Applying E2E Sample Resources (WireMock-backed, no real API keys)..."
  kubectl apply -k config/e2e/
else
  echo "4. Applying Sample Custom Resources & Kustomize Dynamic Secrets..."
  kubectl apply -k config/
fi

echo "5. Running Flyway database migrations (Job)..."
./scripts/wait-for-postgres.sh "${NAMESPACE}" 180
kubectl create configmap flyway-migrations \
  --from-file=tuluat-engine/src/main/resources/db/migration/ \
  -n "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
kubectl delete job flyway-migration -n "$NAMESPACE" --ignore-not-found
kubectl apply -f manifests/operator/flyway-migration-job.yaml
kubectl wait --for=condition=complete job/flyway-migration -n "$NAMESPACE" --timeout=120s

echo "6. Checking status of CRDs and Custom Resources..."
kubectl get crds | grep ai.tuluat.com || true
kubectl get llmproviders -n "$NAMESPACE" || true
kubectl get aiagents -n "$NAMESPACE" || true
kubectl get aiworkflows -n "$NAMESPACE" || true
kubectl get workflowsessions -n "$NAMESPACE" || true
kubectl get mcpservers -n "$NAMESPACE" || true

echo "Deployment finished successfully."
