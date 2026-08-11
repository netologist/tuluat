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

# 7. RAG E2E: Financial Document Ingestion + Retrieval + Source Attribution
echo "7. RAG E2E: Financial Document Ingestion and Source Attribution..."

# 7a. Ingest a financial earnings report into the RAG pipeline
INGEST_RESP=$(curl -s -X POST "${HOST}/api/v1/rag/ingest" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceRef": "reports/acme-q4-2025",
    "content": "Acme Corp Q4 2025 Earnings Report. Revenue: $847M, up 23% year-over-year. Net Income: $142M ($2.34 EPS). Operating Margin: 16.8%. Cloud division revenue grew 47% to $312M."
  }' || true)

INGEST_SOURCE=$(echo "${INGEST_RESP}" | jq -r '.sourceRef // empty' 2>/dev/null || true)
INGEST_CHUNKS=$(echo "${INGEST_RESP}" | jq -r '.chunks // 0' 2>/dev/null || true)

if [ -n "${INGEST_SOURCE}" ] && [ "${INGEST_CHUNKS}" -ge 1 ]; then
  assert_ok "RAG document ingested: ${INGEST_SOURCE} (${INGEST_CHUNKS} chunk(s))"
else
  assert_fail "RAG ingest failed: ${INGEST_RESP}"
fi

# 7b. Query the financial-analyst agent — answer must cite source and data
CHAT_RESP=$(curl -s --max-time 30 -X POST "${HOST}/api/v1/agents/financial-analyst-agent/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "'"${NAMESPACE}"'",
    "prompt": "What was Acme Corp quarterly revenue and operating margin?"
  }' || true)

AGENT_NAME=$(echo "${CHAT_RESP}" | jq -r '.agentName // empty' 2>/dev/null || true)
SYS_PROMPT=$(echo "${CHAT_RESP}" | jq -r '.systemPrompt // empty' 2>/dev/null || true)

if [ -z "${AGENT_NAME}" ]; then
  assert_fail "Agent chat returned no agentName: ${CHAT_RESP}"
fi

# 7c. Assert RAG context header present in system prompt
if echo "${SYS_PROMPT}" | grep -q "Relevant Document Context (RAG):"; then
  assert_ok "RAG context header present in agent system prompt"
else
  assert_fail "Missing RAG context header in system prompt"
fi

# 7d. Assert source document reference is cited
if echo "${SYS_PROMPT}" | grep -q "reports/acme-q4-2025"; then
  assert_ok "Source reference 'reports/acme-q4-2025' cited in system prompt"
else
  assert_fail "Source reference not found in system prompt"
fi

# 7e. Assert financial data from the chunk appears in context
if echo "${SYS_PROMPT}" | grep -q '\$847M'; then
  assert_ok "Financial data '\$847M' retrieved from ingested chunk"
else
  assert_fail "Financial data not found in RAG context"
fi

# 7f. Assert source format is [sourceRef #chunkIndex (sim X.XX)]
if echo "${SYS_PROMPT}" | grep -qE '\[reports/acme-q4-2025 #[0-9]+ \(sim [0-9.]+\)\]'; then
  assert_ok "Source attribution format: [sourceRef #N (sim X.XX)] verified"
else
  assert_fail "Source attribution format incorrect or missing"
fi


echo "=========================================================="
echo " 🎉 ALL E2E ACCEPTANCE & SMOKE TESTS PASSED!"
echo "=========================================================="
