#!/usr/bin/env bash
set -euo pipefail

# Waits until the PostgreSQL instance is actually accepting connections for the
# target user/database (not merely "Running"). Run before Flyway migrations so
# the migration Job never starts against a half-initialized database.
#
# Usage: wait-for-postgres.sh [namespace] [timeout_seconds]

NAMESPACE="${1:-tuluat-system}"
TIMEOUT_SECONDS="${2:-180}"
POD_SELECTOR="app=postgres-pgvector"
DB_NAME="ai_operator_db"
DB_USER="postgres"

echo "Waiting for PostgreSQL to become healthy (namespace=${NAMESPACE}, timeout=${TIMEOUT_SECONDS}s)..."

# 1. Wait until the Deployment is Available so a Running pod exists.
kubectl wait --for=condition=available deployment/postgres-pgvector \
  -n "${NAMESPACE}" --timeout="${TIMEOUT_SECONDS}s"

# 2. Resolve the Running pod and actively poll pg_isready. pg_isready exits 0
#    only when the server accepts connections for the given user + database.
POD="$(kubectl get pods -n "${NAMESPACE}" -l "${POD_SELECTOR}" \
  -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}' | cut -d' ' -f1)"

if [ -z "${POD}" ]; then
  echo "ERROR: no Running PostgreSQL pod found with selector '${POD_SELECTOR}'" >&2
  exit 1
fi

DEADLINE=$(( $(date +%s) + TIMEOUT_SECONDS ))
until kubectl exec -n "${NAMESPACE}" "${POD}" -- \
  pg_isready -U "${DB_USER}" -d "${DB_NAME}" >/dev/null 2>&1; do
  if [ "$(date +%s)" -ge "${DEADLINE}" ]; then
    echo "ERROR: PostgreSQL did not become ready within ${TIMEOUT_SECONDS}s" >&2
    echo "--- recent PostgreSQL logs ---" >&2
    kubectl logs -n "${NAMESPACE}" "${POD}" --tail=50 >&2 || true
    exit 1
  fi
  sleep 3
done

echo "PostgreSQL is healthy and accepting connections."
