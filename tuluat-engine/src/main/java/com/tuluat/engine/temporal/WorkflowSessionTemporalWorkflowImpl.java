package com.tuluat.engine.temporal;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.NodeDefinition;
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
