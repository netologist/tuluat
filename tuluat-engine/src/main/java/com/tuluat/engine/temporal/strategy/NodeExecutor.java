package com.tuluat.engine.temporal.strategy;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.temporal.context.WorkflowExecutionContext;
import com.tuluat.engine.temporal.model.NodeType;

public interface NodeExecutor {
	NodeType getSupportedType();
	String execute(NodeDefinition node, AiWorkflowSpec spec, WorkflowExecutionContext ctx);
}