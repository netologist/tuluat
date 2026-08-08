# Maven Project Dependencies & Architectural Rationale

This document provides a comprehensive technical inventory of all Maven dependencies managed in `pom.xml` across the Tuluat multi-module reactor (`tuluat-parent`, `tuluat-crd-domain`, `tuluat-guardrails`, `tuluat-protocols`, `tuluat-engine`, `tuluat-operator`, and `tuluat-app`). 

Each dependency includes its exact version specification, declaring module, architectural rationale, and role within the platform.

---

## 1. Global & Parent Managed BOMs (`pom.xml`)

### Java & Spring Runtime Baseline
* **Java Version:** `25 LTS` (with Virtual Threads enabled via `--enable-preview`).
* **Spring Boot:** `4.1.0-SNAPSHOT` (Spring Framework 7 runtime baseline).
  * *Rationale:* Harnesses Java 25 LTS lightweight Virtual Threads for non-blocking I/O across thousands of concurrent workflow sessions without traditional reactive API complexity.

### Parent Bill of Materials (BOM) Imports
```xml
<properties>
    <java.version>25</java.version>
    <spring-ai.version>2.0.0-SNAPSHOT</spring-ai.version>
    <josdk.version>5.1.0</josdk.version>
    <fabric8.version>7.3.1</fabric8.version>
    <temporal.version>1.27.0</temporal.version>
</properties>
```

| Group ID & Artifact ID | Version | Purpose & Architectural Rationale |
| :--- | :--- | :--- |
| `io.fabric8:kubernetes-client-bom` | `7.3.1` | **Kubernetes Java Client BOM.** Guarantees version consistency across Fabric8 Kubernetes Client modules, Jackson model serializers, and mock server testing. |
| `org.springframework.ai:spring-ai-bom` | `2.0.0-SNAPSHOT` | **Spring AI 2.0 Engine BOM.** Centralizes version alignment for LLM model abstraction, chat models (OpenAI, Ollama, DeepSeek), vector stores, and prompt templates. |
| `io.javaoperatorsdk:operator-framework-bom` | `5.1.0` | **Java Operator SDK (JOSDK) BOM.** Manages reconciler lifecycle, event sources, controller execution threads, and CRD status update engines. |
| `io.temporal:temporal-sdk` | `1.27.0` | **Temporal Java SDK.** Provides stateful durable workflow execution, activities, timer cancellation, and human-in-the-loop signal handling (`WAITING_APPROVAL`). |

---

## 2. Module-by-Module Dependency Breakdown

### Module: `tuluat-crd-domain`
*Domain library declaring Kubernetes Custom Resource POJOs (`LlmProvider`, `AiAgent`, `AiWorkflow`, `WorkflowSession`, `McpServer`).*

```xml
<dependencies>
    <dependency>
        <groupId>io.fabric8</groupId>
        <artifactId>kubernetes-client-api</artifactId>
    </dependency>
    <dependency>
        <groupId>io.fabric8</groupId>
        <artifactId>kubernetes-model-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.javaoperatorsdk</groupId>
        <artifactId>operator-framework-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-annotations</artifactId>
    </dependency>
</dependencies>
```

| Dependency | Why It Exists & How It Is Used |
| :--- | :--- |
| `kubernetes-client-api` | Provides base interfaces for Fabric8 Kubernetes resources (`CustomResource`, `HasMetadata`, `ObjectMeta`). |
| `kubernetes-model-core` | Supplies standard Kubernetes model definitions (e.g. `SecretKeySelector`, `IngressSpec`, `ObjectMeta`). |
| `operator-framework-core` | Annotations (`@Group`, `@Version`, `@ShortNames`) and interfaces required for JOSDK resource identification. |
| `jackson-annotations` | `@JsonProperty`, `@JsonInclude`, `@JsonIgnoreProperties` annotations for custom JSON/YAML serialization of CRD specs. |

---

### Module: `tuluat-guardrails`
*Safety filter pipeline evaluating PII masking, prompt injection detection, and JSON schema output validation.*

```xml
<dependencies>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-crd-domain</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.networknt</groupId>
        <artifactId>json-schema-validator</artifactId>
        <version>1.5.5</version>
    </dependency>
</dependencies>
```

| Dependency | Why It Exists & How It Is Used |
| :--- | :--- |
| `tuluat-crd-domain` | Grants access to `AiAgent` guardrail spec configurations (`PiiMaskingSpec`, `PromptInjectionSpec`). |
| `spring-boot-starter` | Spring component scanning (`@Component`, `@Service`) for `GuardrailPipeline` and filter implementations. |
| `com.networknt:json-schema-validator` | **JSON Schema Validation.** High-performance JSON Schema (Draft 4/7/2019-09/2020-12) validation engine for `OutputValidationFilter`, enforcing strict output structure before continuing workflows. |

---

### Module: `tuluat-protocols`
*Adapters for Model Context Protocol (MCP) tool server registry and Agent-to-Agent (A2A) protocol negotiation.*

```xml
<dependencies>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-crd-domain</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

| Dependency | Why It Exists & How It Is Used |
| :--- | :--- |
| `tuluat-crd-domain` | Access to `McpServer` and `AiAgent` CRD domain POJOs. |
| `spring-boot-starter` | Manages `McpClientRegistryImpl` and `A2aAdapterImpl` singletons in Spring ApplicationContext. |

---

### Module: `tuluat-engine`
*Core execution engine: Spring AI LLM routing, Temporal Workflow runtime, PostgreSQL + Pgvector memory storage, and MinIO document object storage.*

```xml
<dependencies>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-crd-domain</artifactId>
    </dependency>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-guardrails</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.temporal</groupId>
        <artifactId>temporal-sdk</artifactId>
    </dependency>
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.12</version>
    </dependency>
</dependencies>
```

| Dependency | Why It Exists & How It Is Used |
| :--- | :--- |
| `spring-ai-starter-model-openai` | Connects Spring AI model gateway to OpenAI, DeepSeek, and OpenAI-compatible REST endpoints. |
| `spring-ai-starter-model-ollama` | Enables local zero-cost model execution via Ollama (e.g. `llama3.2`, `mistral`). |
| `temporal-sdk` | Temporal Java client and worker runtime for orchestrating multi-agent state machines, activity retries, and HITL signals. |
| `postgresql` | PostgreSQL JDBC driver supporting vector search operations via `pgvector` extension for long-term memory. |
| `h2` | Lightweight in-memory database driver for fast local integration testing. |
| `minio` | MinIO / AWS S3 Java SDK for chunked document object storage in RAG retrieval pipelines. |
| `micrometer-registry-prometheus` | Exposes real-time Prometheus metrics (`tuluat_workflow_executions_total`, token counts, execution latency) at `/actuator/prometheus`. |

---

### Module: `tuluat-operator`
*Kubernetes Operator containing JOSDK custom resource reconcilers.*

```xml
<dependencies>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-crd-domain</artifactId>
    </dependency>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-protocols</artifactId>
    </dependency>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-engine</artifactId>
    </dependency>
    <dependency>
        <groupId>io.javaoperatorsdk</groupId>
        <artifactId>operator-framework-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>io.fabric8</groupId>
        <artifactId>kubernetes-client</artifactId>
    </dependency>
    <dependency>
        <groupId>io.fabric8</groupId>
        <artifactId>kubernetes-server-mock</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.javaoperatorsdk</groupId>
        <artifactId>operator-framework-junit-5</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

| Dependency | Why It Exists & How It Is Used |
| :--- | :--- |
| `operator-framework-spring-boot-starter` | Auto-registers all `@ControllerConfiguration`-annotated Reconcilers (`LlmProviderReconciler`, `AiAgentReconciler`, `AiWorkflowReconciler`, `WorkflowSessionReconciler`, `McpServerReconciler`) into the Spring lifecycle. |
| `kubernetes-client` | Full Fabric8 client implementation used by reconcilers to query cluster state, create Ingresses, deploy Services, and check Secrets. |
| `kubernetes-server-mock` | **In-memory Kubernetes API Mock Server.** Used in operator unit tests to simulate Kubernetes API server responses without requiring a real KinD or Minikube cluster. |
| `operator-framework-junit-5` | Testing extension for verifying JOSDK reconciler execution, event source dispatching, and status updates. |

---

### Module: `tuluat-app`
*Executable Spring Boot application, REST API controllers, WebSocket STOMP handlers, and web portal backend.*

```xml
<dependencies>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-crd-domain</artifactId>
    </dependency>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-engine</artifactId>
    </dependency>
    <dependency>
        <groupId>com.tuluat</groupId>
        <artifactId>tuluat-operator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

| Dependency | Why It Exists & How It Is Used |
| :--- | :--- |
| `spring-boot-starter-web` | Provides Spring MVC for REST controllers (`WorkflowSessionController`, `AiAgentController`, `ApprovalController`) and WebSockets for real-time visual workflow updates. |
| `spring-boot-starter-validation` | Jakarta Validation (`@NotNull`, `@Size`, `@Valid`) for incoming REST payloads. |
| `spring-boot-starter-actuator` | Production readiness health checks (`/actuator/health`), metrics, and environment inspection endpoints. |
| `spring-boot-starter-data-jpa` | Spring Data JPA repositories for persisting workflow logs, session audit entries, and agent status execution history. |
