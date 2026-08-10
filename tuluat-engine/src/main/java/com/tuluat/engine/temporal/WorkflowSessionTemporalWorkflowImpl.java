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

	private final GraphNodeActivities activities = Workflow.newActivityStub(GraphNodeActivities.class,
			ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(5)).build());

	private boolean approvalReceived = false;
	private ApprovalSignal latestSignal = new ApprovalSignal(true, null, null);

	@Override
	public Map<String, Object> runSession(UUID sessionId, String workflowName, AiWorkflowSpec spec, String input,
			int maxLoops) {
		Map<String, Object> contextData = new HashMap<>();
		contextData.put("input", input);

		String currentNodeId = spec.initialNode();
		int loopCount = 0;

		while (currentNodeId != null && loopCount < maxLoops) {
			final String targetId = currentNodeId;
			NodeDefinition currentNode = spec.nodes().stream().filter(n -> n.id().equals(targetId)).findFirst()
					.orElse(null);

			if (currentNode == null)
				break;

			if ("AGENT".equalsIgnoreCase(currentNode.type())) {
				contextData = activities.executeAgentNode(sessionId, currentNode, contextData);
				currentNodeId = resolveNextNodeId(spec, currentNode.id(), true);
			} else if ("CONDITION".equalsIgnoreCase(currentNode.type())) {
				boolean result = activities.evaluateConditionNode(sessionId, currentNode, contextData);
				currentNodeId = resolveNextNodeId(spec, currentNode.id(), result);
			} else if ("HUMAN_APPROVAL".equalsIgnoreCase(currentNode.type())) {
				activities.recordLog(sessionId, currentNode.id(), "INFO", "Waiting for human approval signal...");
				Workflow.await(() -> approvalReceived);

				if (latestSignal.feedback() != null && !latestSignal.feedback().isBlank()) {
					contextData.put("approval_feedback", latestSignal.feedback());
				}
				if (latestSignal.metadata() != null) {
					contextData.put("approval_metadata", latestSignal.metadata());
				}

				activities.recordLog(sessionId, currentNode.id(), "INFO", "Approval signal received: approved="
						+ latestSignal.approved() + ", feedback=" + latestSignal.feedback());

				currentNodeId = resolveNextNodeId(spec, currentNode.id(), latestSignal.approved());
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
		return spec.edges().stream().filter(e -> e.from().equals(fromNodeId))
				.filter(e -> e.condition() == null || e.condition().isEmpty()
						|| Boolean.parseBoolean(e.condition()) == conditionResult)
				.map(EdgeDefinition::to).findFirst().orElse(null);
	}
}
