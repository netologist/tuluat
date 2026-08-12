package com.tuluat.crd.workflow;

import java.math.BigDecimal;
import java.util.List;

public record AiWorkflowStatus(String state, int nodeCount, BigDecimal costSpentUsd, BigDecimal budgetLimitUsd,
		int sessionCount, long totalTokens, long inputTokens, long outputTokens, List<String> agentNames) {

	public static AiWorkflowStatus ready() {
		return new AiWorkflowStatus("Ready", 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0L, 0L, 0L, List.of());
	}
}
