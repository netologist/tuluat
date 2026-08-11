package com.tuluat.engine.temporal.strategy;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.temporal.context.WorkflowExecutionContext;
import com.tuluat.engine.temporal.model.NodeType;
import com.tuluat.engine.temporal.strategy.NodeExecutor;
import com.tuluat.engine.temporal.util.WorkflowUtils;
import io.temporal.workflow.Workflow;

public class HumanApprovalNodeExecutor implements NodeExecutor {

	@Override
	public NodeType getSupportedType() {
		return NodeType.HUMAN_APPROVAL;
	}

	@Override
	public String execute(NodeDefinition node, AiWorkflowSpec spec, WorkflowExecutionContext ctx) {
		ctx.activities().recordLog(ctx.sessionId(), node.id(), "INFO", "Waiting for human approval signal...");

		// Temporal blocking await
		Workflow.await(ctx.approvalReceivedSupplier()::getAsBoolean);

		ApprovalSignal latestSignal = ctx.signalSupplier().get();

		if (latestSignal.feedback() != null && !latestSignal.feedback().isBlank()) {
			ctx.contextData().put("approval_feedback", latestSignal.feedback());
		}
		if (latestSignal.metadata() != null) {
			ctx.contextData().put("approval_metadata", latestSignal.metadata());
		}

		ctx.activities().recordLog(ctx.sessionId(), node.id(), "INFO",
				"Approval signal received: approved=%s, feedback=%s".formatted(latestSignal.approved(),
						latestSignal.feedback()));

		boolean isApproved = latestSignal.approved();
		ctx.resetApprovalFlag().run();

		return WorkflowUtils.resolveNextNodeId(spec, node.id(), isApproved);
	}
}