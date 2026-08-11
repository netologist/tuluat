package com.tuluat.engine.temporal.strategy;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.temporal.context.WorkflowExecutionContext;
import com.tuluat.engine.temporal.model.NodeType;
import com.tuluat.engine.temporal.strategy.NodeExecutor;
import com.tuluat.engine.temporal.util.WorkflowUtils;

public class ConditionNodeExecutor implements NodeExecutor {

	@Override
	public NodeType getSupportedType() {
		return NodeType.CONDITION;
	}

	@Override
	public String execute(NodeDefinition node, AiWorkflowSpec spec, WorkflowExecutionContext ctx) {
		boolean result = ctx.activities().evaluateConditionNode(ctx.sessionId(), node, ctx.contextData());
		return WorkflowUtils.resolveNextNodeId(spec, node.id(), result);
	}
}