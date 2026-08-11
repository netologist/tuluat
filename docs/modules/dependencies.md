# Maven Dependencies & Architectural Rationale

This document provides a technical inventory of all Maven dependencies managed in `pom.xml` across the Tuluat reactor (`tuluat-parent`, `tuluat-crd-domain`, `tuluat-guardrails`, `tuluat-protocols`, `tuluat-engine`, `tuluat-operator`, and `tuluat-app`). 

---

## 1. Parent Managed BOMs (`pom.xml`)

### Java & Spring Runtime Baseline
* **Java Version:** `25 LTS` (Virtual Threads enabled via `--enable-preview`).
* **Spring Boot:** `4.1.0-SNAPSHOT` (Spring Framework 7 runtime baseline).

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

## 2. Module Dependency Breakdown

### `tuluat-crd-domain`
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

### `tuluat-guardrails`
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

### `tuluat-protocols`
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

### `tuluat-engine`
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

### `tuluat-operator`
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

### `tuluat-app`
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
