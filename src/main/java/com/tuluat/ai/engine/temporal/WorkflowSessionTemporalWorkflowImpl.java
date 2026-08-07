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
    public void signalApproval(boolean approved) {
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
