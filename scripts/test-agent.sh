#!/usr/bin/env bash
set -euo pipefail

AGENT_NAME=${1:-customer-support-agent}
NAMESPACE=${2:-default}
HOST=${3:-"localhost:8080"}

echo "=========================================================="
echo " Testing Public Ingress Endpoint for AiAgent: $AGENT_NAME"
echo "=========================================================="

echo "1. Fetching AiAgent details from API..."
curl -s "http://${HOST}/api/v1/agents/${AGENT_NAME}?namespace=${NAMESPACE}" | jq . || true
echo -e "\n"

echo "2. Sending Chat Request to Public Agent Endpoint..."
curl -s -X POST "http://${HOST}/api/v1/agents/${AGENT_NAME}/chat" \
  -H "Content-Type: application/json" \
  -d '{
        "namespace": "'"${NAMESPACE}"'",
        "prompt": "Calculate 42 * 100 and check weather in Istanbul."
      }' | jq .

echo -e "\nAgent chat test completed."
