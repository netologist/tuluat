package com.tuluat.engine.temporal.strategy;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.temporal.context.WorkflowExecutionContext;
import com.tuluat.engine.temporal.model.NodeType;
import com.tuluat.engine.temporal.strategy.NodeExecutor;
import com.tuluat.engine.temporal.util.WorkflowUtils;

import java.util.Map;

public class AgentNodeExecutor implements NodeExecutor {

	@Override
	public NodeType getSupportedType() {
		return NodeType.AGENT;
	}

	@Override
	public String execute(NodeDefinition node, AiWorkflowSpec spec, WorkflowExecutionContext ctx) {
		Map<String, Object> updatedContext = ctx.activities().executeAgentNode(ctx.sessionId(), node,
				ctx.contextData());
		ctx.contextData().putAll(updatedContext);
		return WorkflowUtils.resolveNextNodeId(spec, node.id(), true);
	}
}