# Testing Strategy & Quality Assurance Guide

This document outlines the testing architecture, quality assurance tools, unit testing patterns, ArchUnit rules, and automated KinD E2E acceptance suite.

---

## 1. Testing Pyramid & Verification Levels

```
                     / \
                    /   \  KinD Cluster E2E Suite
                   / E2E \ (scripts/e2e-acceptance-test.sh)
                  /-------\
                 /         \  Integration Tests
                /  Integr.  \ (H2, Mock K8s Server, WireMock)
               /-------------\
              /               \  Unit & ArchUnit Tests
             /   Unit Tests    \ (JUnit 5, Mockito, Spotless, Checkstyle)
            /-------------------\
```

---

## 2. Automated Test Execution Commands

### 2.1 Run Unit & Module Test Suite
```bash
./mvnw test -Denable-preview
```

### 2.2 Run ArchUnit Architecture Rules
```bash
./mvnw test -pl tuluat-app -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false -Denable-preview
```

### 2.3 Run Spotless Formatting & Checkstyle Analysis
```bash
./mvnw spotless:check
./mvnw checkstyle:check
```

To automatically format code to project standards:
```bash
./mvnw spotless:apply
```

### 2.4 Run KinD E2E Acceptance Test Suite
```bash
./scripts/build-deploy-test.sh
```

---

## 3. KinD Cluster E2E Acceptance Test Suite

The E2E test suite validates the full stack against a live KinD Kubernetes cluster in CI (`scripts/e2e-acceptance-test.sh`):

1. **Health Check**: Asserts Spring Boot Actuator `/actuator/health` returns `UP` (and PostgreSQL DB is reachable).
2. **Telemetry Check**: Asserts `/actuator/prometheus` exposes JVM and custom metrics.
3. **CRD Reconciliation Check**: Verifies `LlmProvider`, `AiAgent`, and `AiWorkflow` CRs exist and reconcile to `Ready`.
4. **Session Execution**: Triggers `/api/v1/workflows/multi-agent-researcher/sessions` and verifies `COMPLETED` execution state.
5. **Audit Logs Verification**: Asserts execution audit log entries are persisted in PostgreSQL.
6. **HITL Signal Test**: Sends approval payload to `/api/v1/sessions/{id}/approve` and verifies signal receipt (`SIGNAL_SENT`).

---

## 4. ArchUnit Architecture Rules (`ArchitectureTest`)

Located in `tuluat-app/src/test/java/com/tuluat/app/architecture/ArchitectureTest.java`:
- **Package Isolation**: Engine classes cannot depend on `tuluat-app` web controllers.
- **Guardrails Isolation**: Guardrails module must not depend on JOSDK operator classes or web controllers.
- **CRD Domain Independence**: `tuluat-crd-domain` module must have zero dependencies on Spring web or execution engines.
