package com.tuluat.engine.temporal.util;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;

public final class WorkflowUtils {

	private WorkflowUtils() {
	}

	public static String resolveNextNodeId(AiWorkflowSpec spec, String fromNodeId, boolean conditionResult) {
		return spec.edges().stream().filter(edge -> edge.from().equals(fromNodeId))
				.filter(edge -> isConditionMatched(edge, conditionResult)).map(EdgeDefinition::to).findFirst()
				.orElse(null);
	}

	private static boolean isConditionMatched(EdgeDefinition edge, boolean conditionResult) {
		String cond = edge.condition();
		if (cond == null || cond.isEmpty()) {
			return true;
		}
		return Boolean.parseBoolean(cond) == conditionResult;
	}
}