#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="tuluat-cluster"
IMAGE_NAME="tuluat-operator:latest"
NAMESPACE="tuluat-system"

echo "=========================================================="
echo " 🚀 Tuluat AI Operator (Spring Boot 4.1 / Java 25 LTS): Build, Deploy & E2E"
echo "=========================================================="

echo "1. Building Fat JAR & Production Docker Image (${IMAGE_NAME})..."
./mvnw clean package -DskipTests ${MAVEN_ARGS:-}
docker build -f Dockerfile.local -t "${IMAGE_NAME}" .

echo "2. Loading Docker Image into Kind Cluster (${CLUSTER_NAME})..."
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"

echo "3. Deploying Infrastructure, CRDs, Operator & E2E Samples (WireMock-backed)..."
E2E_MODE=true ./scripts/deploy-operator.sh "${NAMESPACE}"
kubectl apply -f manifests/operator/deployment.yaml -n "${NAMESPACE}"

echo "4. Restarting Operator Deployment & Waiting for Pod Readiness..."
kubectl rollout restart deployment/tuluat-operator -n "${NAMESPACE}"
kubectl rollout status deployment/tuluat-operator -n "${NAMESPACE}" --timeout=360s

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

sleep 3
./scripts/e2e-acceptance-test.sh "http://localhost:${PORT}" "${NAMESPACE}"
echo -e "\n=========================================================="
echo " ✅ Build, Deploy, and E2E Verification Completed!"
echo "=========================================================="
