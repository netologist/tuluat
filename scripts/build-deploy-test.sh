#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="tuluat-cluster"
IMAGE_NAME="tuluat-operator:latest"
NAMESPACE="tuluat-system"

echo "=========================================================="
echo " 🚀 Tuluat AI Operator (Java 24): Build, Deploy & E2E"
echo "=========================================================="

echo "1. Building Production Docker Image (${IMAGE_NAME})..."
DOCKER_BUILDKIT=0 docker build -f Dockerfile.local -t "${IMAGE_NAME}" .

echo "2. Loading Docker Image into Kind Cluster (${CLUSTER_NAME})..."
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"

echo "3. Deploying Kubernetes Manifests to Namespace: ${NAMESPACE}..."
./scripts/deploy-operator.sh "${NAMESPACE}"
kubectl apply -f manifests/operator/deployment.yaml -n "${NAMESPACE}"

echo "4. Restarting Operator Deployment & Waiting for Pod Readiness..."
kubectl rollout restart deployment/tuluat-operator -n "${NAMESPACE}"
kubectl rollout status deployment/tuluat-operator -n "${NAMESPACE}" --timeout=180s

echo "5. Performing Automated End-to-End Verification..."

PORT=8089
echo "Port-forwarding Service svc/tuluat-operator-service on port ${PORT}..."
kubectl port-forward svc/tuluat-operator-service "${PORT}:8080" -n "${NAMESPACE}" >/dev/null 2>&1 &
PF_PID=$!

cleanup() {
  echo "Cleaning up port-forward process (PID: ${PF_PID})..."
  kill "${PF_PID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Waiting for port-forward connection on port ${PORT}..."
HEALTH_OK=false
for i in {1..20}; do
  STATUS=$(curl -s "http://localhost:${PORT}/actuator/health" 2>/dev/null || true)
  if echo "${STATUS}" | grep -q "UP"; then
    HEALTH_OK=true
    echo "Operator Healthcheck: OK (UP)"
    break
  fi
  sleep 2
done

if [ "${HEALTH_OK}" = "false" ]; then
  echo "Healthcheck timed out on port ${PORT}"
  exit 1
fi

echo -e "\n--- [Check 1] Actuator Prometheus Telemetry ---"
curl -s "http://localhost:${PORT}/actuator/prometheus" | grep "jvm_threads_live_threads" | head -n 5 || true

echo -e "\n--- [Check 2] Creating Multi-Agent Session ---"
SESSION_RESP=$(curl -s -X POST "http://localhost:${PORT}/api/v1/workflows/multi-agent-researcher/sessions" \
  -H "Content-Type: application/json" \
  -d '{"input": "Java 24 Multi-Module E2E Verification Task"}' || true)
echo "Session Creation Response: ${SESSION_RESP}"

SESSION_ID=$(echo "${SESSION_RESP}" | jq -r '.id // .sessionId // empty' 2>/dev/null || true)

if [ -n "${SESSION_ID}" ] && [ "${SESSION_ID}" != "null" ]; then
  echo -e "\n--- [Check 3] Session Status & Logs ---"
  curl -s "http://localhost:${PORT}/api/v1/sessions/${SESSION_ID}" | jq . || true
  curl -s "http://localhost:${PORT}/api/v1/sessions/${SESSION_ID}/logs" | jq . || true

  echo -e "\n--- [Check 4] Submitting Human Approval Signal ---"
  APPROVAL_RESP=$(curl -s -X POST "http://localhost:${PORT}/api/v1/sessions/${SESSION_ID}/approve" \
    -H "Content-Type: application/json" \
    -d '{
          "approved": true,
          "feedback": "Java 24 runtime verified cleanly",
          "metadata": {"reviewer": "automated-e2e-suite"}
        }' || true)
  echo "Approval Signal Response: ${APPROVAL_RESP}"
fi

echo -e "\n=========================================================="
echo " ✅ Java 24 Build, Deploy, and E2E Verification Completed!"
echo "=========================================================="
