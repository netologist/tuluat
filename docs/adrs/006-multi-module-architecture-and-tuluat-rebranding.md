# ADR 006: Multi-Module Architecture and Tuluat System Rebranding

* **Status:** Accepted  
* **Date:** 2026-08-07  
* **Deciders:** Software Architecture Team  

---

## Context and Problem Statement
The project initially started as a single Maven module (`k8s-crd-ai-operator`) under package `com.tuluat.ai`. As the system evolves across Evre 1 (Core & Engine), Evre 2 (MCP Protocols, Guardrails & A2A), and Evre 3 (Web UI & Approval Inbox), keeping all code in a single module creates monolith creep, tight coupling between K8s Operator controllers and core AI engines, and makes independent deployment or SDK distribution difficult.

Furthermore, naming conventions needed a unified identity representing the creative/spontaneous nature of AI agents ("Tuluat") across Maven artifacts, Java package roots, and Kubernetes system namespaces.

## Decision Drivers
* **Strict Decoupling:** Separate K8s CRD models, core AI engine (Temporal/Embabel), safety guardrails, MCP/A2A protocols, operator reconcilers, and REST/Web UI applications.
* **Unified Brand Identity:** Rename root domain and package namespaces to `com.tuluat.*` and `tuluat-*` Maven sub-modules.
* **Kubernetes Namespace Standards:** Migrate resources from `default` to dedicated `tuluat-system` namespace.
* **Future Productization & SDK Distribution:** `tuluat-crd-domain` can be published independently as a lightweight Java client model without Spring/Temporal dependencies.

## Considered Options
1. **Single-Module Monolith (Status Quo):** Easy to maintain in early stages, but leads to circular dependency risks, bloated JAR sizes, and mixed concerns as Evre 2 and 3 features are added.
2. **Multi-Module Maven Architecture with Tuluat Rebranding:** Split into 6 explicit Maven sub-modules (`tuluat-crd-domain`, `tuluat-engine`, `tuluat-guardrails`, `tuluat-protocols`, `tuluat-operator`, `tuluat-app`), adopt `com.tuluat.*` package roots, and deploy to `tuluat-system` namespace.

## Decision Outcome
**Chosen Option:** **Option 2 (Multi-Module Maven Architecture with Tuluat Rebranding)**.

### Sub-Module Architecture Definition

| Sub-Module (`artifactId`) | Java Package | Primary Responsibilities |
| :--- | :--- | :--- |
| **`tuluat-crd-domain`** | `com.tuluat.crd.*` | Pure Fabric8 / JOSDK Custom Resource definitions (`AiWorkflow`, `WorkflowSession`, `AiAgent`, `LlmProvider`). |
| **`tuluat-engine`** | `com.tuluat.engine.*` | Temporal workflows & activities, Embabel Goal Engine, pgvector unified short/long memory, skills registry, telemetry. |
| **`tuluat-guardrails`** | `com.tuluat.guardrails.*` | Pre-execution PII masking & Prompt Injection defense, post-execution JSON Schema verification. |
| **`tuluat-protocols`** | `com.tuluat.protocols.*` | Model Context Protocol (MCP) client registry & Agent-to-Agent (A2A) remote execution adapters. |
| **`tuluat-operator`** | `com.tuluat.operator.*` | Java Operator SDK Reconcilers (`WorkflowSessionReconciler`, `AiAgentReconciler`, etc.) and K8s dynamic resource lifecycle logic. |
| **`tuluat-app`** | `com.tuluat.app.*` | Spring Boot main entrypoint (`TuluatOperatorApplication`), REST API controllers, Web UI static assets & backend. |

### Kubernetes Namespace Allocation
* Operator, services, temporal workers, and telemetry will deploy under `tuluat-system` namespace.

### Positives
* **Modular Safety:** Clear dependency graph prevents circular dependencies.
* **Granular Testing:** Each layer can be tested in isolation (`./mvnw test` per module).
* **Prepared for Evre 2 & 3:** Clear home for Guardrails, MCP, A2A, and Web UI components without cluttering operator code.

### Negatives
* Initial refactoring cost to split packages, update Maven POMs, imports, and Kubernetes manifests.
