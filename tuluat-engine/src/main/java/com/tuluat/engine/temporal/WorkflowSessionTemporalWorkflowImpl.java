package com.tuluat.engine.temporal;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.temporal.context.WorkflowExecutionContext;
import com.tuluat.engine.temporal.factory.NodeExecutorFactory;
import com.tuluat.engine.temporal.model.NodeType;
import com.tuluat.engine.temporal.strategy.NodeExecutor;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WorkflowSessionTemporalWorkflowImpl implements WorkflowSessionTemporalWorkflow {

	private static final Duration DEFAULT_ACTIVITY_TIMEOUT = Duration.ofMinutes(5);

	private final GraphNodeActivities activities = Workflow.newActivityStub(GraphNodeActivities.class,
			ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_ACTIVITY_TIMEOUT).build());

	private final NodeExecutorFactory executorFactory = new NodeExecutorFactory();

	private boolean approvalReceived = false;
	private ApprovalSignal latestSignal = new ApprovalSignal(true, null, null);

	@Override
	public Map<String, Object> runSession(UUID sessionId, String workflowName, AiWorkflowSpec spec, String input,
			int maxLoops) {
		Objects.requireNonNull(spec, "AiWorkflowSpec cannot be null");
		Objects.requireNonNull(sessionId, "Session ID cannot be null");

		Map<String, Object> contextData = new HashMap<>();
		contextData.put("input", input);

		// Map tabanlı O(1) indeksleme
		Map<String, NodeDefinition> nodeIndex = spec.nodes().stream()
				.collect(Collectors.toMap(NodeDefinition::id, Function.identity()));

		// Temporal durumu ve bağımlılıkları taşıyan context
		WorkflowExecutionContext execContext = new WorkflowExecutionContext(sessionId, contextData, activities,
				() -> this.latestSignal, () -> this.approvalReceived, () -> this.approvalReceived = false);

		String currentNodeId = spec.initialNode();
		int loopCount = 0;

		while (currentNodeId != null && loopCount < maxLoops) {
			NodeDefinition currentNode = nodeIndex.get(currentNodeId);
			if (currentNode == null) {
				break;
			}

			NodeType nodeType = NodeType.from(currentNode.type());
			Optional<NodeExecutor> executorOpt = executorFactory.getExecutor(nodeType);

			if (executorOpt.isPresent()) {
				currentNodeId = executorOpt.get().execute(currentNode, spec, execContext);
			} else {
				activities.recordLog(sessionId, currentNode.id(), "WARN",
						"Unsupported node type: " + currentNode.type());
				break;
			}

			loopCount++;
		}

		activities.recordLog(sessionId, currentNodeId, "INFO", "Temporal Workflow completed successfully.");
		return Map.copyOf(contextData);
	}

	@Override
	public void signalApproval(ApprovalSignal signal) {
		this.latestSignal = Optional.ofNullable(signal).orElseGet(() -> new ApprovalSignal(true, null, null));
		this.approvalReceived = true;
	}
}