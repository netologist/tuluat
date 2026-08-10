package com.tuluat.engine.temporal;

import com.tuluat.crd.workflow.NodeDefinition;
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
