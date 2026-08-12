package com.tuluat.crd.session;

import java.math.BigDecimal;

/**
 * Per-node execution record capturing agent call metrics within a workflow
 * session.
 */
public record NodeExecution(String nodeId, String agentName, String provider, String model, String input, String output,
		String startTime, String endTime, long durationMs, long totalTokens, long inputTokens, long outputTokens,
		BigDecimal costUsd, String status) {
}
