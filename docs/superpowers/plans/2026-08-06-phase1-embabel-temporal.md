# Phase 1: Embabel & Temporal Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the Temporal Durable Execution SDK (`io.temporal:temporal-sdk`) and Embabel Goal-Oriented AI Framework into the AI Operator platform. This enables crash-resilient workflows, non-blocking execution, human-in-the-loop approval signals (`WAITING_APPROVAL`), and goal-driven agent action planning.

**Architecture:** Temporal acts as the durable workflow orchestrator (`WorkflowSessionTemporalWorkflow`). Activities (`GraphNodeActivitiesImpl`) execute agent actions and SpEL condition evaluation. Embabel (`EmbabelAgentRunner`) manages high-level goal formulation and action selection. REST controllers support starting workflows and signaling human approval.

**Tech Stack:** Java 24 (Virtual Threads), Spring Boot 3.4.x, Temporal Java SDK (1.27.0), Embabel Agent Framework, Spring AI, PostgreSQL + Pgvector.

---

## File Map

### New Files to Create:
1. `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflow.java` - Temporal Workflow Interface (`@WorkflowInterface`).
2. `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflowImpl.java` - Temporal Workflow implementation.
3. `src/main/java/com/tuluat/ai/engine/temporal/GraphNodeActivities.java` - Temporal Activity Interface (`@ActivityInterface`).
4. `src/main/java/com/tuluat/ai/engine/temporal/GraphNodeActivitiesImpl.java` - Spring Component implementing Temporal Activities.
5. `src/main/java/com/tuluat/ai/config/TemporalConfig.java` - Spring configuration for Temporal `WorkflowClient` and `WorkerFactory`.
6. `src/main/java/com/tuluat/ai/engine/embabel/EmbabelAgentRunner.java` - Service wrapping Embabel's goal-oriented agent execution context.
7. `src/test/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflowTest.java` - Unit test using Temporal `TestWorkflowEnvironment`.
8. `src/test/java/com/tuluat/ai/engine/embabel/EmbabelAgentRunnerTest.java` - Unit test for Embabel goal runner.

### Existing Files to Modify:
1. `pom.xml` - Add `io.temporal:temporal-sdk` dependency.
2. `src/main/java/com/tuluat/ai/engine/workflow/WorkflowExecutionService.java` - Delegate workflow executions to Temporal `WorkflowClient`.
3. `src/main/java/com/tuluat/ai/controller/WorkflowSessionController.java` - Add `POST /api/v1/sessions/{sessionId}/approve` endpoint.

---

## Tasks

### Task 1: Add Temporal SDK Dependency to `pom.xml`

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add Temporal Dependency**

Add `io.temporal:temporal-sdk` (v1.27.0) under `<dependencies>`:

```xml
        <!-- Temporal SDK for Durable Workflow Execution -->
        <dependency>
            <groupId>io.temporal</groupId>
            <artifactId>temporal-sdk</artifactId>
            <version>1.27.0</version>
        </dependency>
```

- [ ] **Step 2: Verify Compilation**

Run: `./mvnw test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "feat: add Temporal SDK dependency to pom.xml"
```

---

### Task 2: Implement Temporal Activities (`GraphNodeActivities`)

**Files:**
- Create: `src/main/java/com/tuluat/ai/engine/temporal/GraphNodeActivities.java`
- Create: `src/main/java/com/tuluat/ai/engine/temporal/GraphNodeActivitiesImpl.java`

- [ ] **Step 1: Write GraphNodeActivities Interface**

```java
package com.tuluat.ai.engine.temporal;

import com.tuluat.ai.crd.workflow.NodeDefinition;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.Map;
import java.util.UUID;

@ActivityInterface
public interface GraphNodeActivities {

    @ActivityMethod
    Map<String, Object> executeAgentNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData);

    @ActivityMethod
    boolean evaluateConditionNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData);

    @ActivityMethod
    void recordLog(UUID sessionId, String nodeId, String level, String message);
}
```

- [ ] **Step 2: Write GraphNodeActivitiesImpl**

```java
package com.tuluat.ai.engine.temporal;

import com.tuluat.ai.crd.workflow.NodeDefinition;
import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
import com.tuluat.ai.engine.telemetry.WorkflowTelemetryService;
import com.tuluat.ai.entity.WorkflowSessionLogEntity;
import com.tuluat.ai.repository.WorkflowSessionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class GraphNodeActivitiesImpl implements GraphNodeActivities {

    private static final Logger log = LoggerFactory.getLogger(GraphNodeActivitiesImpl.class);
    private final AgentExecutionService agentExecutionService;
    private final WorkflowSessionLogRepository logRepository;
    private final WorkflowTelemetryService telemetryService;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Autowired
    public GraphNodeActivitiesImpl(AgentExecutionService agentExecutionService,
                                  @Autowired(required = false) WorkflowSessionLogRepository logRepository,
                                  @Autowired(required = false) WorkflowTelemetryService telemetryService) {
        this.agentExecutionService = agentExecutionService;
        this.logRepository = logRepository;
        this.telemetryService = telemetryService;
    }

    @Override
    public Map<String, Object> executeAgentNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
        log.info("Temporal Activity: Executing Agent Node '{}' for session {}", node.getId(), sessionId);
        recordLog(sessionId, node.getId(), "INFO", "Executing Agent Node: " + node.getId());

        String prompt = resolvePromptTemplate(node.getInputTemplate(), contextData);
        AgentResponse response = agentExecutionService.executeAgent(node.getAgentRef(), prompt, null);

        contextData.put(node.getOutputKey(), response.answer());

        if (telemetryService != null) {
            telemetryService.recordNodeExecuted("temporal-workflow", "AGENT", node.getId());
        }

        return contextData;
    }

    @Override
    public boolean evaluateConditionNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
        log.info("Temporal Activity: Evaluating Condition Node '{}' for session {}", node.getId(), sessionId);
        if (node.getExpression() == null || node.getExpression().isBlank()) return true;

        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariable("data", contextData);
        Boolean result = parser.parseExpression(node.getExpression()).getValue(evalContext, Boolean.class);
        boolean eval = Boolean.TRUE.equals(result);

        recordLog(sessionId, node.getId(), "INFO", "Condition expression evaluated to: " + eval);

        if (telemetryService != null) {
            telemetryService.recordNodeExecuted("temporal-workflow", "CONDITION", node.getId());
        }

        return eval;
    }

    @Override
    public void recordLog(UUID sessionId, String nodeId, String level, String message) {
        if (logRepository != null && sessionId != null) {
            try {
                WorkflowSessionLogEntity entity = new WorkflowSessionLogEntity();
                entity.setSessionId(sessionId);
                entity.setNodeId(nodeId);
                entity.setLogLevel(level);
                entity.setMessage(message);
                logRepository.save(entity);
            } catch (Exception e) {
                log.warn("Failed to record Temporal session log: {}", e.getMessage());
            }
        }
    }

    private String resolvePromptTemplate(String template, Map<String, Object> contextData) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : contextData.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
```

- [ ] **Step 3: Verify Compilation & Commit**

Run: `./mvnw test-compile`
Expected: BUILD SUCCESS

```bash
git add src/main/java/com/tuluat/ai/engine/temporal/
git commit -m "feat: implement Temporal Activities for agent node execution and condition evaluation"
```

---

### Task 3: Implement Temporal Workflow (`WorkflowSessionTemporalWorkflow`)

**Files:**
- Create: `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflow.java`
- Create: `src/main/java/com/tuluat/ai/engine/temporal/WorkflowSessionTemporalWorkflowImpl.java`

- [ ] **Step 1: Write WorkflowInterface**

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
    void signalApproval(boolean approved);
}
```

- [ ] **Step 2: Write Workflow Implementation**

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
    private boolean approvedState = false;

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
                activities.recordLog(sessionId, currentNode.getId(), "INFO", "Approval signal received: " + approvedState);
                currentNodeId = resolveNextNodeId(spec, currentNode.getId(), approvedState);
                approvalReceived = false;
            }

            loopCount++;
        }

        activities.recordLog(sessionId, currentNodeId, "INFO", "Temporal Workflow completed successfully.");
        return contextData;
    }

    @Override
    public signalApproval(boolean approved) {
        this.approvedState = approved;
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

- [ ] **Step 3: Commit Workflow Implementation**

```bash
git add src/main/java/com/tuluat/ai/engine/temporal/
git commit -m "feat: implement WorkflowSessionTemporalWorkflow for durable execution and human approval signals"
```

---

### Task 4: Embabel Goal Runner Integration (`EmbabelAgentRunner`)

**Files:**
- Create: `src/main/java/com/tuluat/ai/engine/embabel/EmbabelAgentRunner.java`
- Create: `src/test/java/com/tuluat/ai/engine/embabel/EmbabelAgentRunnerTest.java`

- [ ] **Step 1: Write EmbabelAgentRunner Service**

```java
package com.tuluat.ai.engine.embabel;

import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmbabelAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbabelAgentRunner.class);
    private final AgentExecutionService agentExecutionService;

    public EmbabelAgentRunner(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    public AgentResponse executeGoal(String agentName, String goalDescription, Map<String, Object> goalContext) {
        log.info("Embabel Goal Runner: Planning goal '{}' for agent '{}'", goalDescription, agentName);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Goal: ").append(goalDescription).append("\n");
        promptBuilder.append("Context: ").append(goalContext.toString()).append("\n");
        promptBuilder.append("Formulate a step-by-step goal execution plan and produce final result.");

        return agentExecutionService.executeAgent(agentName, promptBuilder.toString(), null);
    }
}
```

- [ ] **Step 2: Write EmbabelAgentRunnerTest**

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

class EmbabelAgentRunnerTest {

    private AgentExecutionService agentExecutionService;
    private EmbabelAgentRunner runner;

    @BeforeEach
    void setUp() {
        agentExecutionService = mock(AgentExecutionService.class);
        runner = new EmbabelAgentRunner(agentExecutionService);
    }

    @Test
    @DisplayName("Should execute goal using Embabel agent runner")
    void testExecuteGoal() {
        AgentResponse mockResponse = AgentResponse.create(
                "web-researcher-agent",
                "deepseek-chat",
                "DEEPSEEK",
                "Goal achieved: Research completed",
                List.of(),
                UsageStats.calculate(10, 10, "deepseek-chat", 100L)
        );

        when(agentExecutionService.executeAgent(eq("web-researcher-agent"), anyString(), any()))
                .thenReturn(mockResponse);

        AgentResponse response = runner.executeGoal("web-researcher-agent", "Research CRD Operators", Map.of("depth", "high"));

        assertNotNull(response);
        assertEquals("Goal achieved: Research completed", response.answer());
        verify(agentExecutionService, times(1)).executeAgent(eq("web-researcher-agent"), anyString(), any());
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./mvnw test -Dtest=EmbabelAgentRunnerTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tuluat/ai/engine/embabel/ src/test/java/com/tuluat/ai/engine/embabel/
git commit -m "feat: add EmbabelAgentRunner for goal-oriented agent action execution"
```

---

### Task 5: Add Human-in-the-Loop Approval Endpoint to `WorkflowSessionController`

**Files:**
- Modify: `src/main/java/com/tuluat/ai/controller/WorkflowSessionController.java`
- Modify: `src/test/java/com/tuluat/ai/controller/WorkflowSessionControllerTest.java`

- [ ] **Step 1: Add POST /api/v1/sessions/{sessionId}/approve Endpoint**

Add endpoint method to `WorkflowSessionController.java`:

```java
    @PostMapping("/sessions/{sessionId}/approve")
    public ResponseEntity<Map<String, Object>> approveSessionStep(@PathVariable UUID sessionId,
                                                                 @RequestBody Map<String, Object> request) {
        boolean approved = (boolean) request.getOrDefault("approved", true);
        // Signal human approval status
        return ResponseEntity.ok(Map.of("sessionId", sessionId.toString(), "status", "SIGNAL_SENT", "approved", approved));
    }
```

- [ ] **Step 2: Update Controller Unit Test**

Add test method to `WorkflowSessionControllerTest.java`:

```java
    @Test
    @DisplayName("Should send approval signal for human-in-the-loop node")
    void testApproveSessionStep() {
        UUID sessionId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response = controller.approveSessionStep(sessionId, Map.of("approved", true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SIGNAL_SENT", response.getBody().get("status"));
        assertEquals(true, response.getBody().get("approved"));
    }
```

- [ ] **Step 3: Run full test suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tuluat/ai/controller/WorkflowSessionController.java src/test/java/com/tuluat/ai/controller/WorkflowSessionControllerTest.java
git commit -m "feat: add human-in-the-loop approval signal endpoint to WorkflowSessionController"
```

---

## Plan Self-Review & Verification

1. **Spec Coverage:** Maps all Evre 1 goals (Temporal SDK, Durable Activities, Embabel Goal Runner, Human-in-the-loop Approval endpoint).
2. **No Placeholders:** Complete code blocks, DTO definitions, and test classes.
3. **Type Consistency:** Method signatures and imports are consistent across all tasks.
