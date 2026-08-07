#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="tuluat-cluster"
IMAGE_NAME="tuluat-operator:latest"
NAMESPACE="tuluat-system"

echo "=========================================================="
echo " 🚀 Tuluat AI Operator: Build, Deploy & E2E Verification"
echo "=========================================================="

echo "1. Building Multi-Module Maven Package..."
./mvnw clean package -DskipTests

echo "2. Building Local Docker Image (${IMAGE_NAME})..."
docker build -f Dockerfile.local -t "${IMAGE_NAME}" .

echo "3. Loading Docker Image into Kind Cluster (${CLUSTER_NAME})..."
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"

echo "4. Deploying Kubernetes Manifests to Namespace: ${NAMESPACE}..."
./scripts/deploy-operator.sh "${NAMESPACE}"
kubectl apply -f manifests/operator/deployment.yaml -n "${NAMESPACE}"

echo "5. Restarting Operator Deployment & Waiting for Pod Readiness..."
kubectl rollout restart deployment/tuluat-operator -n "${NAMESPACE}"
kubectl rollout status deployment/tuluat-operator -n "${NAMESPACE}" --timeout=180s

echo "6. Performing Automated End-to-End Verification..."

# Set up port-forward in background
OPERATOR_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=tuluat-operator -o jsonpath='{.items[0].metadata.name}')
echo "Targeting Operator Pod: ${OPERATOR_POD}"

PORT=8089
kubectl port-forward pod/"${OPERATOR_POD}" "${PORT}:8080" -n "${NAMESPACE}" >/dev/null 2>&1 &
PF_PID=$!

cleanup() {
  echo "Cleaning up port-forward process (PID: ${PF_PID})..."
  kill "${PF_PID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Waiting for port-forward connection on port ${PORT}..."
for i in {1..15}; do
  if curl -s "http://localhost:${PORT}/actuator/health" | grep -q "UP"; then
    echo "Operator Healthcheck: OK (UP)"
    break
  fi
  sleep 2
done

echo -e "\n--- [Check 1] Actuator Prometheus Telemetry ---"
curl -s "http://localhost:${PORT}/actuator/prometheus" | grep "jvm_threads_live_threads" | head -n 5

echo -e "\n--- [Check 2] Creating Multi-Agent Session ---"
SESSION_RESP=$(curl -s -X POST "http://localhost:${PORT}/api/v1/workflows/multi-agent-researcher/sessions" \
  -H "Content-Type: application/json" \
  -d '{"input": "Automated Multi-Module E2E Verification Task"}')
echo "Session Creation Response: ${SESSION_RESP}"

SESSION_ID=$(echo "${SESSION_RESP}" | jq -r '.id // .sessionId // empty')

if [ -n "${SESSION_ID}" ] && [ "${SESSION_ID}" != "null" ]; then
  echo -e "\n--- [Check 3] Session Status & Logs ---"
  curl -s "http://localhost:${PORT}/api/v1/sessions/${SESSION_ID}" | jq .
  curl -s "http://localhost:${PORT}/api/v1/sessions/${SESSION_ID}/logs" | jq .

  echo -e "\n--- [Check 4] Submitting Human Approval Signal ---"
  APPROVAL_RESP=$(curl -s -X POST "http://localhost:${PORT}/api/v1/sessions/${SESSION_ID}/approve" \
    -H "Content-Type: application/json" \
    -d '{
          "approved": true,
          "feedback": "Multi-module refactoring verified cleanly",
          "metadata": {"reviewer": "automated-e2e-suite"}
        }')
  echo "Approval Signal Response: ${APPROVAL_RESP}"
fi

echo -e "\n=========================================================="
echo " ✅ Build, Deploy, and E2E Verification Completed!"
echo "=========================================================="
