# Tuluat — JDK 25 / Modern Java Modernization Report

> **Scope:** tuluat-crd-domain · tuluat-guardrails · tuluat-protocols · tuluat-engine · tuluat-operator · tuluat-app  
> **Analysis:** Read-only. Zero files modified.

---

## 1. Classes Recommended for Java Record Conversion

The codebase is already largely record-first in `tuluat-crd-domain` — all `*Spec` / `*Status` types in the `agent/` and `provider/` sub-packages are records. The remaining **non-record plain classes** that are pure value/DTO objects with no mutable lifecycle requirements are listed below.

### 1.1 `tuluat-crd-domain` — workflow & session packages

| Class | File | Reason |
|---|---|---|
| `AiWorkflowSpec` | `tuluat-crd-domain/src/main/java/com/tuluat/crd/workflow/AiWorkflowSpec.java` | Pure DTO with five mutable fields (`description`, `initialNode`, `nodes`, `edges`, `memoryConfig`) — all getters. Uses `ArrayList` default-initialization which a compact-constructor can replicate. Since `LlmProviderSpec` is already a record and Fabric8 deserializes it correctly, this is safe. Convert to `record AiWorkflowSpec(String description, String initialNode, List<NodeDefinition> nodes, List<EdgeDefinition> edges, MemoryConfig memoryConfig)` with a compact constructor that supplies empty-list defaults. |
| `AiWorkflowStatus` | `tuluat-crd-domain/src/main/java/com/tuluat/crd/workflow/AiWorkflowStatus.java` | Two fields (`state`, `nodeCount`), all getters/setters, no logic. Record: `record AiWorkflowStatus(String state, int nodeCount)`. Move `state = "Ready"` default to a factory method. |
| `NodeDefinition` | `tuluat-crd-domain/src/main/java/com/tuluat/crd/workflow/NodeDefinition.java` | Seven String fields, all getters/setters, no validation. Textbook record: `record NodeDefinition(String id, String type, String agentRef, String inputTemplate, String outputKey, String expression, String outputSchema)`. |
| `EdgeDefinition` | `tuluat-crd-domain/src/main/java/com/tuluat/crd/workflow/EdgeDefinition.java` | Three String fields, all getters/setters. Pure data carrier: `record EdgeDefinition(String from, String to, String condition)`. |
| `MemoryConfig` | `tuluat-crd-domain/src/main/java/com/tuluat/crd/workflow/MemoryConfig.java` | Three fields with primitive defaults. Record with compact-constructor defaults: `record MemoryConfig(int shortMemorySize, boolean enableLongMemory, String vectorTableName)`. |
| `WorkflowSessionSpec` | `tuluat-crd-domain/src/main/java/com/tuluat/crd/session/WorkflowSessionSpec.java` | Three fields, `HashMap` default for `parameters`. Record: `record WorkflowSessionSpec(String workflowRef, String input, Map<String, Object> parameters)` with compact-constructor null-guard. |
| `WorkflowSessionStatus` | `tuluat-crd-domain/src/main/java/com/tuluat/crd/session/WorkflowSessionStatus.java` | Six String fields, all getters/setters, `phase = "PENDING"` default. Clean record: `record WorkflowSessionStatus(String sessionId, String phase, String currentNode, String output, String startTime, String endTime)`. |

### 1.2 `tuluat-engine` — embabel package

| Class | File | Reason |
|---|---|---|
| `EmbabelGoal` | `tuluat-engine/src/main/java/com/tuluat/engine/embabel/EmbabelGoal.java` | Three String fields, explicit all-args constructor, three getters, no mutation after construction. Record eliminates all boilerplate: `record EmbabelGoal(String id, String description, String targetStateKey)`. All callers (`EmbabelGoalEngine`) use only `goal.getDescription()`, `goal.getTargetStateKey()`, `goal.getId()` — record accessors serve directly. |

### 1.3 `tuluat-engine` — temporal package

| Class | File | Reason |
|---|---|---|
| `ApprovalSignal` | `tuluat-engine/src/main/java/com/tuluat/engine/temporal/ApprovalSignal.java` | Three fields, full getter/setter set, explicit no-arg + all-args constructors. Temporal SDK serializes via Jackson which handles records. The mutation (`setApproved`, etc.) is not needed — `WorkflowSessionTemporalWorkflowImpl` replaces the signal wholesale with `this.latestSignal = signal != null ? signal : new ApprovalSignal(...)`. Convert to `record ApprovalSignal(boolean approved, String feedback, Map<String, Object> metadata)`. Jackson deserialisation requires `-parameters` compiler flag (already active for Lombok). |

---

## 2. Classes Recommended for Lombok Annotations

### 2.1 Explicit `Logger` field → `@Slf4j` (16 classes)

The most widespread boilerplate across all modules is the repeated pattern:
```java
private static final Logger log = LoggerFactory.getLogger(SomeClass.class);
```
Replace with `@Slf4j` annotation on the class (references in method bodies switch from `log.*` to `log.*` — identical field name).

| Class | File |
|---|---|
| `AgentExecutionService` | `tuluat-engine/.../agent/AgentExecutionService.java` |
| `SkillRegistry` | `tuluat-engine/.../skill/SkillRegistry.java` |
| `ModelGateway` | `tuluat-engine/.../gateway/ModelGateway.java` |
| `GuardrailPipeline` | `tuluat-guardrails/.../GuardrailPipeline.java` |
| `McpClientRegistryImpl` | `tuluat-protocols/.../McpClientRegistryImpl.java` |
| `A2aAdapterImpl` | `tuluat-protocols/.../A2aAdapterImpl.java` |
| `RagService` | `tuluat-engine/.../rag/RagService.java` |
| `InMemoryRetriever` | `tuluat-engine/.../rag/InMemoryRetriever.java` |
| `PgVectorRetriever` | `tuluat-engine/.../rag/PgVectorRetriever.java` |
| `EmbabelGoalEngine` | `tuluat-engine/.../embabel/EmbabelGoalEngine.java` |
| `GraphStateMachineEngine` | `tuluat-engine/.../workflow/GraphStateMachineEngine.java` |
| `WorkflowExecutionService` | `tuluat-engine/.../workflow/WorkflowExecutionService.java` |
| `TemporalConfig` | `tuluat-engine/.../config/TemporalConfig.java` |
| `AiAgentReconciler` | `tuluat-operator/.../reconciler/AiAgentReconciler.java` |
| `AgentChatController` | `tuluat-app/.../controller/AgentChatController.java` |
| `WorkflowEventPublisher` | `tuluat-app/.../websocket/WorkflowEventPublisher.java` |

### 2.2 `@RequiredArgsConstructor` — services with all-final field constructors (8 classes)

These Spring `@Service` / `@Component` classes have only `final` fields and provide a manual all-args constructor whose body is pure field assignment.

| Class | File | Notes |
|---|---|---|
| `SessionMemoryManager` | `tuluat-engine/.../memory/SessionMemoryManager.java` | One `final` field |
| `GuardrailPipeline` | `tuluat-guardrails/.../GuardrailPipeline.java` | Two `final` list fields |
| `PgVectorRetriever` | `tuluat-engine/.../rag/PgVectorRetriever.java` | One `final JdbcTemplate` |
| `EmbabelGoalEngine` | `tuluat-engine/.../embabel/EmbabelGoalEngine.java` | One `final` field |
| `EmbabelAgentRunner` | `tuluat-engine/.../embabel/EmbabelAgentRunner.java` | One `final` field |
| `AiAgentReconciler` | `tuluat-operator/.../reconciler/AiAgentReconciler.java` | One `final KubernetesClient`; Spring 4.3+ infers `@Autowired` on a single constructor, so `@RequiredArgsConstructor` eliminates both the constructor and the `@Autowired` annotation |
| `WorkflowController` | `tuluat-app/.../controller/WorkflowController.java` | One `final KubernetesClient` |
| `RagService` | `tuluat-engine/.../rag/RagService.java` | Four `final` fields |

### 2.3 `EmbabelBlackboard` — explicit no-arg constructor → `@NoArgsConstructor`

`EmbabelBlackboard` (`tuluat-engine/.../embabel/EmbabelBlackboard.java`) has an explicit empty no-arg constructor body. Replace with `@NoArgsConstructor`. The two-arg constructor with the `putAll` guard must stay explicit (non-trivial logic). This class cannot become a record because it holds mutable `Map` state.

### 2.4 Entity classes — `@Getter @Setter` is already correct

All three entities already use Lombok's `@Getter` and `@Setter`. Do **not** replace them with `@Data` — `@Data` generates `equals`/`hashCode` on all fields which is incorrect for JPA entities (should be based on `@Id` only). The current split is idiomatic for JPA. Consider adding `@ToString(exclude = {"contextData"})` to prevent large JSON blobs in logs.

---

## 3. Entity / Repository JPA Review

### 3.1 `WorkflowSessionEntity` — Findings

**File:** `tuluat-engine/src/main/java/com/tuluat/engine/entity/WorkflowSessionEntity.java`

| Aspect | Finding | Severity |
|---|---|---|
| **`@GeneratedValue` missing on UUID PK** | `@Id UUID sessionId` has no `@GeneratedValue`. Callers manually call `session.setSessionId(UUID.randomUUID())`. Any code path that forgets this will produce a null-PK insert. Add `@GeneratedValue(strategy = GenerationType.UUID)` (JPA 3.1 / Hibernate 6+) and remove manual UUID assignment from `WorkflowExecutionService.startSession()`. | MEDIUM |
| **`@UpdateTimestamp` missing on `updatedAt`** | `updatedAt` is set manually via `session.setUpdatedAt(OffsetDateTime.now())` in `GraphStateMachineEngine` — but only in SOME code paths. The `COMPLETED`/`WAITING_APPROVAL` transitions update it; others do not. Add `@UpdateTimestamp` (Hibernate) or `@LastModifiedDate` (Spring Data Auditing) and remove the field initializer and all manual `setUpdatedAt` calls. | HIGH |
| **Redundant `@CreationTimestamp` + field initializer** | `createdAt` is annotated with `@CreationTimestamp` AND initialized to `OffsetDateTime.now()`. Hibernate overwrites the initializer on INSERT — the initializer is dead code. Remove `= OffsetDateTime.now()`. | LOW |
| **Redundant `loopCount = 0` initializer** | `int` primitive defaults to `0`; the explicit `= 0` is noise. | INFO |
| **Missing `@Version` for optimistic locking** | `sendApprovalSignal()` in `WorkflowExecutionService` does `findById` + mutate + `save` without any lock. Under concurrent HTTP approval signals, last-write wins. Add `@Version private Long version;`. | MEDIUM |
| **No `@Table` index annotations** | Queries on `workflow_name` and `status` fire frequently but no `@Index` annotations document them. Add them to `@Table` for tooling visibility. | LOW |
| **`contextData` as raw JSON String** | Storing and manually constructing JSON with string concatenation in `WorkflowExecutionService` (lines 66–74) is brittle and injection-prone. The entity already declares `@JdbcTypeCode(SqlTypes.JSON)` — use `ObjectMapper` for all read/write instead of raw string surgery. | HIGH |

### 3.2 `SessionShortMemoryEntity` — Findings

**File:** `tuluat-engine/src/main/java/com/tuluat/engine/entity/SessionShortMemoryEntity.java`

| Aspect | Finding | Severity |
|---|---|---|
| **`@GeneratedValue(IDENTITY)` on `Long` PK** | Correct for PostgreSQL SERIAL/BIGSERIAL. ✅ | — |
| **Redundant `@CreationTimestamp` + field initializer** | Same as above. Remove `= OffsetDateTime.now()`. | LOW |
| **No `@NotNull` on `sessionId`** | Column is `nullable = false` in `@Column` but no Jakarta Validation `@NotNull` guard at the Java layer. | LOW |
| **Missing `@Index` on `session_id`** | Primary query path is `findBySessionIdOrderByCreatedAtAsc`. Add index annotation. | LOW |

### 3.3 `WorkflowSessionLogEntity` — Findings

**File:** `tuluat-engine/src/main/java/com/tuluat/engine/entity/WorkflowSessionLogEntity.java`

| Aspect | Finding | Severity |
|---|---|---|
| **`@GeneratedValue(IDENTITY)` on `Long` PK** | Correct. ✅ | — |
| **Redundant `@CreationTimestamp` + field initializer** | Remove `= OffsetDateTime.now()`. | LOW |
| **`logLevel` as `String`** | Values are effectively an enum (`INFO`, `WARN`, `ERROR`). Use `enum LogLevel` mapped with `@Enumerated(EnumType.STRING)` for type safety. | MEDIUM |
| **No `@NotNull` on required columns** | `sessionId`, `logLevel`, `message` are `nullable = false` in DB but lack Jakarta Validation. | LOW |
| **Missing `@Index` on `session_id`** | Same as `SessionShortMemoryEntity`. | LOW |

### 3.4 Repository Interface Review

| Repository | Findings |
|---|---|
| `WorkflowSessionRepository` | ✅ Correct. `@Repository` is redundant on a `JpaRepository` subinterface (harmless). `findAll()` in `AnalyticsController` loads ALL sessions — add `count*` derived queries instead. Consider `Page<WorkflowSessionEntity>` overloads. |
| `SessionShortMemoryRepository` | ✅ Correct. `deleteBySessionId` is a modifying query — Spring Data generates the DELETE. Consider adding explicit `@Modifying @Transactional` for documentation clarity. |
| `WorkflowSessionLogRepository` | ✅ Correct. Same unbounded-list concern for `findBySessionIdOrderByCreatedAtAsc` — pageable overloads recommended for long sessions. |

### 3.5 JPA Compliance Summary

| Rule | Status |
|---|---|
| No `java.util.Date` / `Calendar` | ✅ All entities use `java.time.OffsetDateTime` |
| `@CreationTimestamp` on `createdAt` | ✅ Present (but redundant field initializers need removal) |
| `@UpdateTimestamp` on `updatedAt` | ❌ **Missing in `WorkflowSessionEntity`** — manual mutation only, incomplete |
| UUID PK with `@GeneratedValue` | ❌ `WorkflowSessionEntity` uses manual UUID assignment |
| Optimistic lock `@Version` | ❌ Missing on `WorkflowSessionEntity` |
| Enum-typed status/phase columns | ❌ All `String` — no type safety |
| Jakarta `@NotNull` on non-null FK fields | ❌ Missing on child entity `sessionId` columns |
| `@Column(length)` on VARCHARs | ⚠️ Undeclared (defaults to 255) — acceptable but undocumented |

---

## 4. General Modernization Suggestions

### 4.1 Inconsistent CRD Domain — Records vs Plain POJOs

The `agent/` and `provider/` sub-packages use Java Records consistently. The `workflow/` and `session/` sub-packages use mutable JavaBeans. This is the single largest style inconsistency in the project. Applying the conversions in Section 1 resolves it entirely and establishes immutable value semantics across the whole domain module.

### 4.2 Raw `Map<String, Object>` Controller Responses → Typed Record DTOs

Four controllers build inline `HashMap<String, Object>` response maps with string key literals:

```java
Map<String, Object> map = new HashMap<>();
map.put("sessionId", session.getSessionId());
// ...
return ResponseEntity.ok(map);
```

This is refactoring-hostile (renaming `sessionId` doesn't compile-check), invisible to OpenAPI/Swagger, and untestable without string-key assertions. Replace with typed response records per endpoint:

```java
public record ApprovalDetailResponse(UUID sessionId, String workflowName,
    String currentNode, String contextData, String phase, String startTime) {}
```

**Affected files:**
- `tuluat-app/.../controller/ApprovalController.java`
- `tuluat-app/.../controller/AnalyticsController.java`
- `tuluat-app/.../controller/WorkflowController.java`
- `tuluat-app/.../controller/WorkflowSessionController.java`

### 4.3 Raw JSON String Manipulation → Jackson ObjectMapper

`WorkflowExecutionService` constructs JSON by raw string concatenation (lines 66–74, 93–96):

```java
contextJson = contextJson.substring(0, contextJson.length() - 1) +
    ",\"approvalStatus\":\"" + statusVal + "\",\"approvalFeedback\":\"" + escapedFeedback + "\"}";
```

This is brittle and injection-prone (the `feedback.replace('"', '\\"')` escape is incomplete for all JSON special characters). `GraphStateMachineEngine` already has an `ObjectMapper` — inject one into `WorkflowExecutionService` and use `objectMapper.readValue` / `objectMapper.writeValueAsString` with a typed `Map<String,Object>`.

**File:** `tuluat-engine/.../workflow/WorkflowExecutionService.java`

### 4.4 `AiWorkflow` CRD — Missing `@Kind`, `@Plural`, `@ShortNames` Annotations

```java
// AiWorkflow.java — current state
@Group("ai.tuluat.com")
@Version("v1alpha1")
public class AiWorkflow extends CustomResource<...> implements Namespaced {}
```

Unlike `AiAgent` (`@Kind("AiAgent") @Plural("aiagents") @ShortNames("agent")`) and `LlmProvider`, `AiWorkflow` omits `@Kind`, `@Plural`, and `@ShortNames`. Fabric8 infers `Kind` from the class name, but the established convention in this codebase is to declare them explicitly. Add the three annotations.

**File:** `tuluat-crd-domain/.../workflow/AiWorkflow.java`

### 4.5 `collect(Collectors.toList())` → `.toList()` (Java 16+)

Several methods still use the verbose terminal:

```java
.collect(Collectors.toList())  // verbose, returns mutable list
```

Replace with `.toList()` which is available since Java 16, returns an unmodifiable list, and is idiomatic in JDK 25.

**Affected lines (non-exhaustive):**
- `AnalyticsController.java` line 57
- `WorkflowController.java` line 45
- `ApprovalController.java` lines 46, 57

> **Caveat:** `.toList()` returns an unmodifiable list. Verify no call site mutates the result before switching.

### 4.6 `AnalyticsController` — Unbounded `findAll()` → Count Queries

`getAnalyticsOverview()` calls `sessionRepository.findAll()` and loads every `WorkflowSessionEntity` into heap to count statuses. This will OOM or time out at scale.

```java
// Current (bad)
List<WorkflowSessionEntity> allSessions = sessionRepository.findAll();
long completed = allSessions.stream().filter(...).count();

// Recommended
long total = sessionRepository.count();
long completed = sessionRepository.countByStatus("COMPLETED");
long waitingApprovals = sessionRepository.countByStatus("WAITING_APPROVAL");
long failed = sessionRepository.countByStatus("FAILED");
```

Add `countByStatus(String status)` to `WorkflowSessionRepository`.

**File:** `tuluat-app/.../controller/AnalyticsController.java` (lines 64–70)

### 4.7 `SkillRegistry` — Executor Not Closed on Shutdown (Resource Leak)

`SkillRegistry` creates `Executors.newVirtualThreadPerTaskExecutor()` and holds it in a `final` field, but never shuts it down when the Spring context closes. Add:

```java
@PreDestroy
public void shutdown() {
    virtualThreadExecutor.shutdown();
}
```

**File:** `tuluat-engine/.../skill/SkillRegistry.java`

### 4.8 Session Status Strings → `enum SessionStatus`

The status values `"RUNNING"`, `"COMPLETED"`, `"FAILED"`, `"WAITING_APPROVAL"`, `"REJECTED"`, `"APPROVED"` appear as bare string literals in at least five classes with `equalsIgnoreCase` comparisons throughout. Introduce:

```java
public enum SessionStatus {
    RUNNING, COMPLETED, FAILED, WAITING_APPROVAL, REJECTED, APPROVED;
    public boolean matches(String s) { return name().equalsIgnoreCase(s); }
}
```

Use `@Enumerated(EnumType.STRING)` in `WorkflowSessionEntity` and `switch` expressions at call sites for exhaustiveness checking at compile time.

### 4.9 `ModelGateway.BudgetState` — Replace `AtomicReference<Double>` with `DoubleAdder`

The inner `BudgetState` class uses `AtomicReference<Double>` with `accumulateAndGet(amount, Double::sum)`. Each call boxes a `double` to `Double`. On JDK 25, `DoubleAdder` (from `java.util.concurrent.atomic`) is the correct primitive-safe alternative for the concurrent-accumulation pattern:

```java
private static final class BudgetState {
    private final DoubleAdder spent = new DoubleAdder();
    double spentUsd() { return spent.sum(); }
    void add(double amount) { spent.add(amount); }
}
```

### 4.10 `EmbabelBlackboard` — Thread-Safety Documentation

`EmbabelBlackboard` wraps a plain `HashMap`. Its `getState()` method returns a defensive copy — good. But the internal `state` has no thread-safety guarantees. Since `EmbabelGoalEngine.executeGoal()` is single-threaded today, this is not an immediate bug. However, if goal execution is ever parallelized (virtual threads are in scope for this project), this becomes a race condition. Add a Javadoc warning: `// NOT thread-safe. Access from a single thread only.` or switch the backing map to `ConcurrentHashMap`.

### 4.11 `WorkflowEventPublisher` WebSocket Payloads → Typed Records

`WorkflowEventPublisher` builds `Map<String, Object>` STOMP payloads inline (four methods, each constructing a HashMap). These are pure outbound events with a fixed shape. Define sealed event records:

```java
public sealed interface WorkflowEvent permits
    SessionStateChangedEvent, ApprovalRequestEvent, ApprovalResolvedEvent, LogEmittedEvent {}

public record SessionStateChangedEvent(String type, String sessionId,
    String workflowName, String phase, String currentNode, Object output, String timestamp)
    implements WorkflowEvent {}
// etc.
```

This makes the WebSocket contract explicit, self-documenting, and testable.

**File:** `tuluat-app/.../websocket/WorkflowEventPublisher.java`

---

## Priority Summary

| Category | Count | Priority |
|---|---|---|
| Record conversions (`workflow/` + `session/` packages, `EmbabelGoal`, `ApprovalSignal`) | 9 classes | **HIGH** — consistency + immutability |
| `@UpdateTimestamp` on `WorkflowSessionEntity.updatedAt` | 1 entity | **HIGH** — data correctness risk |
| Raw JSON string manipulation in `WorkflowExecutionService` | 1 service | **HIGH** — correctness / injection risk |
| `Map<String, Object>` responses → typed Record DTOs | 4 controllers | **HIGH** — type safety, API contract |
| `@Slf4j` replacements | 16 classes | **MEDIUM** — eliminates boilerplate |
| `@Version` optimistic lock on `WorkflowSessionEntity` | 1 entity | **MEDIUM** — concurrency risk |
| `@GeneratedValue(UUID)` on `WorkflowSessionEntity` | 1 entity | **MEDIUM** — defensive programming |
| Unbounded `findAll()` in `AnalyticsController` | 1 controller | **MEDIUM** — performance risk |
| Executor shutdown in `SkillRegistry` | 1 service | **MEDIUM** — resource leak |
| `SessionStatus` enum | cross-cutting | **MEDIUM** — type safety |
| `@RequiredArgsConstructor` candidates | 8 classes | **LOW** — reduces boilerplate |
| `AiWorkflow` missing `@Kind`/`@Plural`/`@ShortNames` | 1 class | **LOW** — convention alignment |
| Redundant `@CreationTimestamp` field initializers | 3 entities | **LOW** — dead code |
| `collect(Collectors.toList())` → `.toList()` | 3+ files | **LOW** — style |
| `ModelGateway.BudgetState` boxing | 1 class | **LOW** — micro-optimization |
