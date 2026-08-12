package com.tuluat.crd.session;

import java.math.BigDecimal;
import java.util.List;

public record WorkflowSessionStatus(String sessionId, String phase, String currentNode, String output, String startTime,
		String endTime, long totalTokens, long inputTokens, long outputTokens, BigDecimal costUsd,
		long durationSeconds, List<NodeExecution> nodeExecutions) {

	public static WorkflowSessionStatus pending() {
		return new WorkflowSessionStatus(null, "PENDING", null, null, null, null, 0L, 0L, 0L, BigDecimal.ZERO, 0L,
				List.of());
	}
}
