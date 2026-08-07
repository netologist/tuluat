# Kubernetes AI Operator & Workflow Runtime Platform

A Kubernetes-native AI Operator and Orchestration Runtime built with **Java 24 (Virtual Threads)**, **Spring Boot 3.4.x**, **Spring AI**, **Temporal SDK**, **Embabel Goal Engine**, and **PostgreSQL + Pgvector**.

---

## 🌟 Key Features

* **Declarative Kubernetes CRDs:** `LlmProvider`, `AiAgent`, `AiWorkflow`, `WorkflowSession`.
* **Hybrid Triggering:** Spawn sessions natively via GitOps (`WorkflowSession` CRD) or REST API (`POST /api/v1/workflows/{name}/sessions`).
* **Durable Execution Engine:** Integrated **Temporal Engine** for crash resilience, non-blocking execution, and human-in-the-loop approvals (`WAITING_APPROVAL`).
* **Goal-Oriented Action Planning:** Integrated **Embabel Engine** (`EmbabelGoalEngine`, `EmbabelBlackboard`) for dynamic goal execution.
* **Unified Short & Long Memory:** Chat history and 1536-dimensional vector embeddings stored in PostgreSQL via `pgvector`.
* **Real-time Session Logging & Telemetry:** Prometheus metrics (`/actuator/prometheus`) and REST API session logs (`GET /api/v1/sessions/{id}/logs`).

---

## 🚀 Quickstart & Kubernetes Deployment

### 1. Set Up Local Kind Kubernetes Cluster
```bash
./scripts/create-kind-cluster.sh
```

### 2. Deploy AI Operator & Infrastructure
```bash
./scripts/deploy-operator.sh default
```

---

## 🌐 Cluster Access & Port-Forwarding Guide

To access the operator, telemetry services, and database from your host machine, use the following `kubectl port-forward` commands:

| Service | Protocol / Port | Port-Forward Command | Access URL / Credentials |
| :--- | :--- | :--- | :--- |
| **Operator REST API & Telemetry** | HTTP 8080 | `kubectl port-forward svc/k8s-ai-operator-service 8080:8080 -n default` | `http://localhost:8080/actuator/health`<br/>`http://localhost:8080/actuator/prometheus` |
| **Temporal Web UI** | HTTP 8233 | `kubectl port-forward svc/temporal-ui-service 8233:8233 -n default` | `http://localhost:8233` |
| **Grafana Dashboard** | HTTP 3000 | `kubectl port-forward svc/grafana-service 3000:3000 -n default` | `http://localhost:3000`<br/>*(User: admin / Pass: admin)* |
| **Prometheus Metrics Server** | HTTP 9090 | `kubectl port-forward svc/prometheus-service 9090:9090 -n default` | `http://localhost:9090` |
| **PostgreSQL + Pgvector DB** | TCP 5432 | `kubectl port-forward svc/postgres-service 5432:5432 -n default` | `localhost:5432`<br/>*(DB: ai_operator_db, User: postgres, Pass: postgres)* |

---

## 📡 REST API Usage Examples

### 1. Trigger Workflow Session
```bash
curl -X POST http://localhost:8080/api/v1/workflows/multi-agent-researcher/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "input": "Kubernetes CRD Operator Best Practices 2026",
    "maxLoops": 5
  }'
```

### 2. Fetch Session Execution Logs
```bash
curl http://localhost:8080/api/v1/sessions/<SESSION_ID>/logs
```

### 3. Send Human-in-the-Loop Approval Signal (Free-Form Feedback)
```bash
curl -X POST http://localhost:8080/api/v1/sessions/<SESSION_ID>/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approved": true,
    "feedback": "Great initial research. Please add a security compliance section.",
    "metadata": { "reviewer": "chief-architect@company.com" }
  }'
```

---

## 📚 Documentation Portal (MkDocs)

To launch the interactive documentation portal with architecture diagrams and ADRs:

```bash
./scripts/run-docs.sh
```
Open `http://127.0.0.1:8000` in your browser.
