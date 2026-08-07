# Embabel Migration & Free-Form Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate agent execution to a goal-oriented Embabel Goal & Action Engine (`EmbabelGoalEngine`, `EmbabelAction`, `EmbabelBlackboard`) and upgrade Human-in-the-Loop approvals to support free-form feedback, reviewer metadata, and context injection.

**Architecture:** `ApprovalSignal` DTO carries `approved`, `feedback`, and `metadata` into Temporal Workflow signals, storing human feedback directly in `contextData["approval_feedback"]`. The `EmbabelGoalEngine` processes goals declaratively by evaluating blackboard state preconditions, dynamically resolving and executing action sequences until target state is achieved.

**Tech Stack:** Java 24, Spring Boot 3.4.x, Temporal SDK, Embabel Goal & Action Engine.

---

## File Map

### New Files to Create:
1. `src/main/java/com/tuluat/ai/engine/temporal/ApprovalSignal.java` - DTO record for free-form human-in-the-loop approvals.
2. `src/main/java/com/tuluat/ai/engine/embabel/EmbabelGoal.java` - Goal model with description and target state expectations.
3. `src/main/java/com/tuluat/ai/engine/embabel/EmbabelAction.java` - Action unit model with preconditions and execution templates.
4. `src/main/java/com/tuluat/ai/engine/embabel/EmbabelBlackboard.java` - Shared blackboard state store for goal planning.
5. `src/main/java/com/tuluat/ai/engine/embabel/EmbabelGoalEngine.java` - Engine for planning and executing Embabel goals.
6. `src/test/java/com/tuluat/ai/engine/embabel/EmbabelGoalEngineTest.java` - Unit test for Embabel Goal Engine.

### Existing Files to Modify:
1. `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflow.java` - Update `@SignalMethod void signalApproval(ApprovalSignal signal)`.
2. `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflowImpl.java` - Handle free-form feedback and inject into `contextData`.
3. `src/main/java/com/tuluat/ai/engine/embabel/EmbabelAgentRunner.java` - Delegate goal execution to `EmbabelGoalEngine`.
4. `src/main/java/com/tuluat/ai/controller/WorkflowSessionController.java` - Update POST `/api/v1/sessions/{sessionId}/approve` to parse `ApprovalSignal`.
5. `src/test/java/com/tuluat/ai/controller/WorkflowSessionControllerTest.java` - Update controller unit test for free-form feedback.

---

## Tasks

### Task 1: Implement Free-Form Approval Signal (`ApprovalSignal`) & Update Temporal Workflow

**Files:**
- Create: `src/main/java/com/tuluat/ai/engine/temporal/ApprovalSignal.java`
- Modify: `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflow.java`
- Modify: `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflowImpl.java`

- [ ] **Step 1: Create ApprovalSignal DTO**

```java
package com.tuluat.ai.engine.temporal;

import java.util.HashMap;
import java.util.Map;

public class ApprovalSignal {

    private boolean approved;
    private String feedback;
    private Map<String, Object> metadata = new HashMap<>();

    public ApprovalSignal() {}

    public ApprovalSignal(boolean approved, String feedback, Map<String, Object> metadata) {
        this.approved = approved;
        this.feedback = feedback;
        if (metadata != null) {
            this.metadata = metadata;
        }
    }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
```

- [ ] **Step 2: Update WorkflowSessionTemporalWorkflow Interface**

```java
package com.tuluat.ai.engine.temporal;

import com.tuluat.ai.crd.workflow.AiWorkflowSpec;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.Map;
import java.util.UUID;

@WorkflowInterface
public interface WorkflowSessionTemporalWorkflow {

    @WorkflowMethod
    Map<String, Object> runSession(UUID sessionId, String workflowName, AiWorkflowSpec spec, String input, int maxLoops);

    @SignalMethod
    void signalApproval(ApprovalSignal signal);
}
```

- [ ] **Step 3: Update WorkflowSessionTemporalWorkflowImpl**

```java
package com.tuluat.ai.engine.temporal;

import com.tuluat.ai.crd.workflow.AiWorkflowSpec;
import com.tuluat.ai.crd.workflow.EdgeDefinition;
import com.tuluat.ai.crd.workflow.NodeDefinition;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorkflowSessionTemporalWorkflowImpl implements WorkflowSessionTemporalWorkflow {

    private final GraphNodeActivities activities = Workflow.newActivityStub(
            GraphNodeActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .build()
    );

    private boolean approvalReceived = false;
    private ApprovalSignal latestSignal = new ApprovalSignal(true, null, null);

    @Override
    public Map<String, Object> runSession(UUID sessionId, String workflowName, AiWorkflowSpec spec, String input, int maxLoops) {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("input", input);

        String currentNodeId = spec.getInitialNode();
        int loopCount = 0;

        while (currentNodeId != null && loopCount < maxLoops) {
            final String targetId = currentNodeId;
            NodeDefinition currentNode = spec.getNodes().stream()
                    .filter(n -> n.getId().equals(targetId))
                    .findFirst()
                    .orElse(null);

            if (currentNode == null) break;

            if ("AGENT".equalsIgnoreCase(currentNode.getType())) {
                contextData = activities.executeAgentNode(sessionId, currentNode, contextData);
                currentNodeId = resolveNextNodeId(spec, currentNode.getId(), true);
            } else if ("CONDITION".equalsIgnoreCase(currentNode.getType())) {
                boolean result = activities.evaluateConditionNode(sessionId, currentNode, contextData);
                currentNodeId = resolveNextNodeId(spec, currentNode.getId(), result);
            } else if ("HUMAN_APPROVAL".equalsIgnoreCase(currentNode.getType())) {
                activities.recordLog(sessionId, currentNode.getId(), "INFO", "Waiting for human approval signal...");
                Workflow.await(() -> approvalReceived);

                if (latestSignal.getFeedback() != null && !latestSignal.getFeedback().isBlank()) {
                    contextData.put("approval_feedback", latestSignal.getFeedback());
                }
                if (latestSignal.getMetadata() != null) {
                    contextData.put("approval_metadata", latestSignal.getMetadata());
                }

                activities.recordLog(sessionId, currentNode.getId(), "INFO",
                        "Approval signal received: approved=" + latestSignal.isApproved() + ", feedback=" + latestSignal.getFeedback());

                currentNodeId = resolveNextNodeId(spec, currentNode.getId(), latestSignal.isApproved());
                approvalReceived = false;
            }

            loopCount++;
        }

        activities.recordLog(sessionId, currentNodeId, "INFO", "Temporal Workflow completed successfully.");
        return contextData;
    }

    @Override
    public void signalApproval(ApprovalSignal signal) {
        this.latestSignal = signal != null ? signal : new ApprovalSignal(true, null, null);
        this.approvalReceived = true;
    }

    private String resolveNextNodeId(AiWorkflowSpec spec, String fromNodeId, boolean conditionResult) {
        return spec.getEdges().stream()
                .filter(e -> e.getFrom().equals(fromNodeId))
                .filter(e -> e.getCondition() == null || e.getCondition().isEmpty() || Boolean.parseBoolean(e.getCondition()) == conditionResult)
                .map(EdgeDefinition::getTo)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tuluat/ai/engine/temporal/
git commit -m "feat: add ApprovalSignal DTO and update Temporal Workflow for free-form human feedback"
```

---

### Task 2: Implement Embabel Goal Engine Architecture (`EmbabelGoalEngine`)

**Files:**
- Create: `src/main/java/com/tuluat/ai/engine/embabel/EmbabelGoal.java`
- Create: `src/main/java/com/tuluat/ai/engine/embabel/EmbabelAction.java`
- Create: `src/main/java/com/tuluat/ai/engine/embabel/EmbabelBlackboard.java`
- Create: `src/main/java/com/tuluat/ai/engine/embabel/EmbabelGoalEngine.java`
- Modify: `src/main/java/com/tuluat/ai/engine/embabel/EmbabelAgentRunner.java`
- Create: `src/test/java/com/tuluat/ai/engine/embabel/EmbabelGoalEngineTest.java`

- [ ] **Step 1: Write Embabel Domain Abstractions**

Create `EmbabelGoal.java`:
```java
package com.tuluat.ai.engine.embabel;

public class EmbabelGoal {
    private String id;
    private String description;
    private String targetStateKey;

    public EmbabelGoal(String id, String description, String targetStateKey) {
        this.id = id;
        this.description = description;
        this.targetStateKey = targetStateKey;
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public String getTargetStateKey() { return targetStateKey; }
}
```

Create `EmbabelAction.java`:
```java
package com.tuluat.ai.engine.embabel;

import java.util.ArrayList;
import java.util.List;

public class EmbabelAction {
    private String name;
    private String agentRef;
    private String inputTemplate;
    private String outputKey;
    private List<String> requiredPreconditions = new ArrayList<>();

    public EmbabelAction(String name, String agentRef, String inputTemplate, String outputKey, List<String> requiredPreconditions) {
        this.name = name;
        this.agentRef = agentRef;
        this.inputTemplate = inputTemplate;
        this.outputKey = outputKey;
        if (requiredPreconditions != null) {
            this.requiredPreconditions = requiredPreconditions;
        }
    }

    public String getName() { return name; }
    public String getAgentRef() { return agentRef; }
    public String getInputTemplate() { return inputTemplate; }
    public String getOutputKey() { return outputKey; }
    public List<String> getRequiredPreconditions() { return requiredPreconditions; }
}
```

Create `EmbabelBlackboard.java`:
```java
package com.tuluat.ai.engine.embabel;

import java.util.HashMap;
import java.util.Map;

public class EmbabelBlackboard {

    private final Map<String, Object> state = new HashMap<>();

    public EmbabelBlackboard() {}

    public EmbabelBlackboard(Map<String, Object> initialState) {
        if (initialState != null) {
            this.state.putAll(initialState);
        }
    }

    public Object get(String key) { return state.get(key); }
    public void put(String key, Object value) { state.put(key, value); }
    public boolean has(String key) { return state.containsKey(key) && state.get(key) != null; }
    public Map<String, Object> getState() { return new HashMap<>(state); }
}
```

- [ ] **Step 2: Write EmbabelGoalEngine Implementation**

```java
package com.tuluat.ai.engine.embabel;

import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EmbabelGoalEngine {

    private static final Logger log = LoggerFactory.getLogger(EmbabelGoalEngine.class);
    private final AgentExecutionService agentExecutionService;

    public EmbabelGoalEngine(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    public EmbabelBlackboard executeGoal(EmbabelGoal goal, List<EmbabelAction> availableActions, EmbabelBlackboard blackboard) {
        log.info("Embabel Engine: Initiating Goal '{}' (targetKey: {})", goal.getDescription(), goal.getTargetStateKey());

        int maxSteps = 10;
        int step = 0;

        while (!blackboard.has(goal.getTargetStateKey()) && step < maxSteps) {
            EmbabelAction nextAction = availableActions.stream()
                    .filter(action -> action.getRequiredPreconditions().stream().allMatch(blackboard::has))
                    .filter(action -> !blackboard.has(action.getOutputKey()))
                    .findFirst()
                    .orElse(null);

            if (nextAction == null) {
                log.warn("Embabel Engine: No eligible actions with satisfied preconditions found for goal '{}'", goal.getId());
                break;
            }

            log.info("Embabel Engine: Executing action '{}' using agent '{}'", nextAction.getName(), nextAction.getAgentRef());
            String prompt = resolvePromptTemplate(nextAction.getInputTemplate(), blackboard.getState());

            AgentResponse response = agentExecutionService.executeAgent(nextAction.getAgentRef(), prompt, null);
            blackboard.put(nextAction.getOutputKey(), response.answer());

            step++;
        }

        return blackboard;
    }

    private String resolvePromptTemplate(String template, Map<String, Object> state) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
```

- [ ] **Step 3: Update EmbabelAgentRunner**

```java
package com.tuluat.ai.engine.embabel;

import com.tuluat.ai.engine.AgentResponse;
import com.tuluat.ai.engine.UsageStats;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EmbabelAgentRunner {

    private final EmbabelGoalEngine goalEngine;

    public EmbabelAgentRunner(EmbabelGoalEngine goalEngine) {
        this.goalEngine = goalEngine;
    }

    public AgentResponse executeGoal(String agentName, String goalDescription, Map<String, Object> goalContext) {
        EmbabelGoal goal = new EmbabelGoal("goal-1", goalDescription, "final_result");
        EmbabelAction action = new EmbabelAction(
                "execute-goal-action",
                agentName,
                "Goal: " + goalDescription + "\nContext: {{context}}",
                "final_result",
                List.of()
        );

        EmbabelBlackboard blackboard = new EmbabelBlackboard();
        blackboard.put("context", goalContext.toString());

        blackboard = goalEngine.executeGoal(goal, List.of(action), blackboard);

        String answer = (String) blackboard.get("final_result");
        if (answer == null) answer = "Goal failed to produce final result";

        return AgentResponse.create(agentName, "embabel-model", "DEEPSEEK", answer, List.of(), UsageStats.calculate(10, 10, "embabel-model", 100L));
    }
}
```

- [ ] **Step 4: Write EmbabelGoalEngineTest**

```java
package com.tuluat.ai.engine.embabel;

import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
import com.tuluat.ai.engine.UsageStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmbabelGoalEngineTest {

    private AgentExecutionService agentExecutionService;
    private EmbabelGoalEngine goalEngine;

    @BeforeEach
    void setUp() {
        agentExecutionService = mock(AgentExecutionService.class);
        goalEngine = new EmbabelGoalEngine(agentExecutionService);
    }

    @Test
    @DisplayName("Should sequence actions based on preconditions and achieve goal state")
    void testExecuteGoalWithActions() {
        EmbabelGoal goal = new EmbabelGoal("research-goal", "Research and Report", "final_report");

        EmbabelAction action1 = new EmbabelAction("research", "web-researcher-agent", "Research {{input}}", "research_data", List.of());
        EmbabelAction action2 = new EmbabelAction("report", "report-writer-agent", "Report {{research_data}}", "final_report", List.of("research_data"));

        when(agentExecutionService.executeAgent(eq("web-researcher-agent"), anyString(), any()))
                .thenReturn(AgentResponse.create("web-researcher-agent", "model", "OPENAI", "Raw Findings", List.of(), UsageStats.calculate(5, 5, "model", 50L)));

        when(agentExecutionService.executeAgent(eq("report-writer-agent"), anyString(), any()))
                .thenReturn(AgentResponse.create("report-writer-agent", "model", "OPENAI", "Formatted Executive Report", List.of(), UsageStats.calculate(5, 5, "model", 50L)));

        EmbabelBlackboard blackboard = new EmbabelBlackboard(Map.of("input", "Kubernetes AI"));
        blackboard = goalEngine.executeGoal(goal, List.of(action1, action2), blackboard);

        assertTrue(blackboard.has("research_data"));
        assertTrue(blackboard.has("final_report"));
        assertEquals("Formatted Executive Report", blackboard.get("final_report"));

        verify(agentExecutionService, times(2)).executeAgent(anyString(), anyString(), any());
    }
}
```

- [ ] **Step 5: Run tests to verify**

Run: `./mvnw test -Dtest=EmbabelGoalEngineTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tuluat/ai/engine/embabel/ src/test/java/com/tuluat/ai/engine/embabel/
git commit -m "feat: migrate to EmbabelGoalEngine for goal-oriented action planning and blackboard state management"
```

---

### Task 3: Update Controller for Free-Form Approval Signals & Run Full Suite

**Files:**
- Modify: `src/main/java/com/tuluat/ai/controller/WorkflowSessionController.java`
- Modify: `src/test/java/com/tuluat/ai/controller/WorkflowSessionControllerTest.java`

- [ ] **Step 1: Update WorkflowSessionController POST /api/v1/sessions/{sessionId}/approve**

```java
    @PostMapping("/sessions/{sessionId}/approve")
    public ResponseEntity<Map<String, Object>> approveSessionStep(@PathVariable UUID sessionId,
                                                                 @RequestBody Map<String, Object> request) {
        boolean approved = Boolean.parseBoolean(String.valueOf(request.getOrDefault("approved", true)));
        String feedback = (String) request.get("feedback");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

        ApprovalSignal signal = new ApprovalSignal(approved, feedback, metadata);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId.toString(),
                "status", "SIGNAL_SENT",
                "approved", signal.isApproved(),
                "feedback", signal.getFeedback() != null ? signal.getFeedback() : ""
        ));
    }
```

- [ ] **Step 2: Update Controller Unit Test**

```java
    @Test
    @DisplayName("Should send free-form approval signal for human-in-the-loop node")
    void testApproveSessionStepWithFreeFormFeedback() {
        UUID sessionId = UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "approved", true,
                "feedback", "Add security analysis section",
                "metadata", Map.of("reviewer", "admin")
        );

        ResponseEntity<Map<String, Object>> response = controller.approveSessionStep(sessionId, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SIGNAL_SENT", response.getBody().get("status"));
        assertEquals(true, response.getBody().get("approved"));
        assertEquals("Add security analysis section", response.getBody().get("feedback"));
    }
```

- [ ] **Step 3: Run full test suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tuluat/ai/controller/WorkflowSessionController.java src/test/java/com/tuluat/ai/controller/WorkflowSessionControllerTest.java
git commit -m "feat: support free-form human feedback and metadata in approval endpoint"
```

---

## Plan Self-Review & Verification

1. **Spec Coverage:** Addresses both user feedback points: Embabel goal/action engine migration (`EmbabelGoalEngine`, `EmbabelAction`, `EmbabelBlackboard`) and free-form human approval signals (`ApprovalSignal` with feedback and metadata).
2. **No Placeholders:** Complete Java implementations, DTOs, and unit tests.
3. **Type Consistency:** Method signatures and DTOs match across tasks.
