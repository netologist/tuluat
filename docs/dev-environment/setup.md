# Development Environment & Local Setup Guide

This guide covers local developer environment setup, KinD cluster creation, Helm deployment, cluster port-forwarding, REST API usage examples, and Web GUI portal features.

---

## 1. Quickstart & Local Cluster Deployment

### 1.1 Step 1: Create KinD Kubernetes Cluster
```bash
./scripts/create-kind-cluster.sh
```
This script creates a local KinD cluster named `tuluat-cluster` with custom ingress and container registry settings.

### 1.2 Step 2: Build Image & Deploy via Helm
```bash
# Build fat JAR & Docker image
./mvnw package -DskipTests
docker build -f Dockerfile.local -t tuluat-operator:latest .
kind load docker-image tuluat-operator:latest --name tuluat-cluster

# Deploy full stack via Helm
helm package helm/tuluat-operator -d dist
helm upgrade --install tuluat-operator dist/*.tgz \
  --namespace tuluat-system \
  --create-namespace \
  --set wiremock.enabled=true \
  --set samples.install=true \
  --wait --timeout=5m
```

### 1.3 Alternative: Single-Script Build, Deploy & E2E
```bash
./scripts/build-deploy-test.sh
```

---

## 2. Cluster Service Ports & Port-Forwarding Guide

To access the Control Portal, REST API, and infrastructure dashboards from your host machine:

| Service | Protocol / Port | Port-Forward Command | Access URL / Credentials |
| :--- | :--- | :--- | :--- |
| **Control Portal & REST API** | HTTP 8080 | `kubectl port-forward svc/tuluat-operator-service 8089:8080 -n tuluat-system` | `http://localhost:8089/`<br/>`http://localhost:8089/actuator/health` |
| **Temporal Web UI** | HTTP 8233 | `kubectl port-forward svc/temporal-ui-service 8233:8233 -n tuluat-system` | `http://localhost:8233` |
| **MinIO S3 Object Storage** | HTTP 9000/9001 | `kubectl port-forward svc/minio-service 9000:9000 9001:9001 -n tuluat-system` | API: `http://localhost:9000`<br/>Console: `http://localhost:9001`<br/>*(AccessKey: minioadmin / SecretKey: minioadmin)* |
| **Grafana Dashboard** | HTTP 3000 | `kubectl port-forward svc/grafana-service 3000:3000 -n tuluat-system` | `http://localhost:3000`<br/>*(User: admin / Pass: admin)* |
| **Prometheus Metrics** | HTTP 9090 | `kubectl port-forward svc/prometheus-service 9090:9090 -n tuluat-system` | `http://localhost:9090` |
| **PostgreSQL + Pgvector DB** | TCP 5432 | `kubectl port-forward svc/postgres-service 5432:5432 -n tuluat-system` | `localhost:5432`<br/>*(DB: ai_operator_db, User: postgres, Pass: postgres)* |

---

## 3. Control Portal Web GUI (`http://localhost:8089/`)

The embedded single-page Web GUI includes:

1. **Dashboard & Cost Analytics**: Real-time token usage, cost expenditure tracking, and active session metrics.
2. **Workflows & DAG Inspector**: Visual view of active workflow graph templates and execution states.
3. **Agents & Execution Logs**: Detailed prompt history, guardrail status, and step logs.
4. **Visual Canvas & CRD Builder**: Drag-and-drop workflow canvas for visually creating and editing `AiWorkflow` CRDs.
5. **Approval Inbox**: Dedicated review panel for human-in-the-loop nodes in `WAITING_APPROVAL` status.

---

## 4. REST API Curl Examples

### 4.1 Trigger Workflow Session
```bash
curl -X POST http://localhost:8089/api/v1/workflows/multi-agent-researcher/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "input": "Kubernetes CRD Operator Best Practices 2026",
    "maxLoops": 5
  }'
```

### 4.2 Fetch Execution Audit Logs
```bash
curl http://localhost:8089/api/v1/sessions/<SESSION_ID>/logs
```

### 4.3 Send Human-in-the-Loop Approval Signal
```bash
curl -X POST http://localhost:8089/api/v1/sessions/<SESSION_ID>/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approved": true,
    "feedback": "Approved for production rollout",
    "metadata": { "reviewer": "chief-architect@company.com" }
  }'
```
