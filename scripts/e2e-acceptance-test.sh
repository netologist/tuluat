#!/usr/bin/env bash
set -euo pipefail

HOST=${1:-"http://localhost:8089"}
NAMESPACE=${2:-"tuluat-system"}

echo "=========================================================="
echo " 🧪 Tuluat AI Operator: E2E Acceptance & Smoke Test Suite"
echo "=========================================================="
echo "Target Base URL : ${HOST}"
echo "Target Namespace: ${NAMESPACE}"
echo "----------------------------------------------------------"

# Helper for colorized assertions
assert_ok() {
  echo -e "  [PASS] $1"
}

assert_fail() {
  echo -e "  [FAIL] $1"
  exit 1
}

# 1. Healthcheck Assertion
echo "1. Asserting Operator Health Status..."
HEALTH_OK=false
HEALTH_RESP=""
for i in {1..20}; do
  HEALTH_RESP=$(curl -s "${HOST}/actuator/health" 2>/dev/null || true)
  if echo "${HEALTH_RESP}" | grep -q '"status":"UP"'; then
    HEALTH_OK=true
    break
  fi
  sleep 2
done

if [ "${HEALTH_OK}" = "true" ]; then
  assert_ok "Spring Boot Actuator Health is UP (PostgreSQL DB connected)"
else
  assert_fail "Healthcheck failed or DB unreachable: ${HEALTH_RESP}"
fi
# 2. Telemetry Assertion
echo "2. Asserting Prometheus Telemetry Endpoint..."
METRICS_RESP=$(curl -s "${HOST}/actuator/prometheus" || true)
if echo "${METRICS_RESP}" | grep -q "jvm_threads_live_threads"; then
  assert_ok "Prometheus telemetry metrics exposed cleanly"
else
  assert_fail "Prometheus metrics missing from /actuator/prometheus"
fi

# 3. Kubernetes CRD Status Assertion
echo "3. Asserting Custom Resource Instances in KinD (${NAMESPACE})..."
AIWORKFLOWS=$(kubectl get aiworkflows -n "${NAMESPACE}" --no-headers 2>/dev/null | wc -l | tr -d ' ')
AIAGENTS=$(kubectl get aiagents -n "${NAMESPACE}" --no-headers 2>/dev/null | wc -l | tr -d ' ')
LLMPROVIDERS=$(kubectl get llmproviders -n "${NAMESPACE}" --no-headers 2>/dev/null | wc -l | tr -d ' ')

if [ "${AIWORKFLOWS}" -gt 0 ] && [ "${AIAGENTS}" -gt 0 ] && [ "${LLMPROVIDERS}" -gt 0 ]; then
  assert_ok "CRDs deployed and reconciled: ${LLMPROVIDERS} LLMProviders, ${AIAGENTS} AiAgents, ${AIWORKFLOWS} AiWorkflows"
else
  assert_fail "Missing CRD instances in namespace ${NAMESPACE}"
fi

# 4. Multi-Agent Workflow Session Execution Acceptance Test
echo "4. Executing Multi-Agent Workflow Session Acceptance Test..."
CREATE_RESP=$(curl -s --max-time 60 -X POST "${HOST}/api/v1/workflows/multi-agent-researcher/sessions" \
  -H "Content-Type: application/json" \
  -d '{"input": "KinD E2E Acceptance Test Execution", "maxLoops": 10}' || true)

SESSION_ID=$(echo "${CREATE_RESP}" | jq -r '.id // .sessionId // empty' 2>/dev/null || true)
STATUS=$(echo "${CREATE_RESP}" | jq -r '.status // empty' 2>/dev/null || true)
CURRENT_NODE=$(echo "${CREATE_RESP}" | jq -r '.currentNodeId // empty' 2>/dev/null || true)

if [ -n "${SESSION_ID}" ] && [ "${STATUS}" = "COMPLETED" ]; then
  assert_ok "WorkflowSession created and completed successfully: ID=${SESSION_ID}, Final Node=${CURRENT_NODE}"
else
  assert_fail "WorkflowSession execution failed or did not complete: ${CREATE_RESP}"
fi

# 5. Execution Logs Retrieval Assertion
echo "5. Asserting Session Execution Audit Logs..."
LOGS_RESP=$(curl -s "${HOST}/api/v1/sessions/${SESSION_ID}/logs" || true)
LOG_COUNT=$(echo "${LOGS_RESP}" | jq '. | length' 2>/dev/null || echo "0")

if [ "${LOG_COUNT}" -ge 5 ]; then
  assert_ok "Execution audit log entries persisted: ${LOG_COUNT} log records found"
else
  assert_fail "Expected >= 5 log records, found: ${LOG_COUNT}"
fi

# 6. Human-in-the-Loop Approval Signal Acceptance Test
echo "6. Executing Human-in-the-Loop (HITL) Approval Signal Test..."
APPROVAL_RESP=$(curl -s -X POST "${HOST}/api/v1/sessions/${SESSION_ID}/approve" \
  -H "Content-Type: application/json" \
  -d '{
        "approved": true,
        "feedback": "E2E Acceptance Test Signal Verified",
        "metadata": {"source": "github-actions-kind-ci"}
      }' || true)

SIGNAL_STATUS=$(echo "${APPROVAL_RESP}" | jq -r '.status // empty' 2>/dev/null || true)
APPROVED=$(echo "${APPROVAL_RESP}" | jq -r '.approved // false' 2>/dev/null || true)

if [ "${SIGNAL_STATUS}" = "SIGNAL_SENT" ] && [ "${APPROVED}" = "true" ]; then
  assert_ok "HITL Approval Signal delivered successfully (Status=${SIGNAL_STATUS})"
else
  assert_fail "Approval signal failed: ${APPROVAL_RESP}"
fi

echo "=========================================================="
echo " 🎉 ALL E2E ACCEPTANCE & SMOKE TESTS PASSED!"
echo "=========================================================="
