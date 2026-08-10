# ADR 010: GraalVM Native Image Transition — Technical Debt & Cold-Start Optimization

* **Status:** Accepted (Technical Debt logged; transition deferred)
* **Date:** 2026-08-09
* **Deciders:** Software Architecture Team + Core Operator Maintainers

---

## Context and Problem Statement

The **Tuluat AI Operator** currently runs on standard OpenJDK 25 (`eclipse-temurin:25-jre`) as a executable Fat JAR (`java --enable-preview -Dspring.threads.virtual.enabled=true -jar app.jar`).

While Java 25 Virtual Threads (Project Loom) provide exceptional runtime concurrency and throughput, JVM JIT initialization introduces a **3.0 to 5.0 second cold-start latency**. In Kubernetes Scale-to-Zero and rapid event-driven reconciliation scenarios, this startup delay impacts responsiveness.

Transitioning to **GraalVM Native Image AOT (Ahead-Of-Time) compilation** can reduce startup times to **50–150ms** and lower memory footprint from ~250MB to ~70MB. However, doing so immediately faces technical friction with current dependencies.

---

## Technical Barriers & Why Transition Is Deferred (Tech Debt)

1. **Java 25 Preview Features (`--enable-preview`)**:
   * The platform relies on Java 25 preview APIs. GraalVM AOT compilation with preview features is not yet fully stable across all toolchains.
2. **JOSDK & Fabric8 Kubernetes Client Reflection**:
   * Kubernetes custom resource deserialization, watch events, and JOSDK controllers use dynamic proxies, Jackson serialization, and heavy reflection. AOT requires explicit GraalVM `reflect-config.json` or Spring `RuntimeHintsRegistrar` metadata.
3. **Spring AI Snapshot Metadata**:
   * `spring-ai.version` (2.0.0-SNAPSHOT) has experimental GraalVM native image reachability metadata.

---

## Decision Outcome

**Decision:** Formally record the **GraalVM Native Image transition as Technical Debt**. Retain standard JVM 25 execution for the current PoC phase, and execute a structured migration once Spring AI 2.0 and JOSDK Native hints stabilize.

### Action Plan for Future Migration Phase:

1. **Build Tooling**:
   * Add `org.graalvm.buildtools:native-maven-plugin` to `tuluat-app/pom.xml` under a `native` Maven profile.
2. **Runtime Hints**:
   * Implement `@ImportRuntimeHints` for Fabric8 CRD records (`AiAgent`, `AiWorkflow`, `LlmProvider`, `McpServer`, `WorkflowSession`).
3. **Containerization**:
   * Provide `Dockerfile.native` based on `container-registry.oracle.com/graalvm/native-image:21` multi-stage build.
4. **Helm Chart Integration**:
   * Expose `operator.image.native: true` flag in `helm/tuluat-operator/values.yaml`.

---

## Expected Target Metrics

| Metric | Current JVM JIT | Target GraalVM AOT |
|---|---|---|
| **Startup / Cold-Start** | ~3.5s | **~80ms** |
| **Container Size** | ~280MB | **~90MB** |
| **Memory Footprint (Idle)** | ~220MB | **~70MB** |
