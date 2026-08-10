package com.tuluat.crd.session;

public record WorkflowSessionStatus(String sessionId, String phase, String currentNode, String output, String startTime,
		String endTime) {

	public static WorkflowSessionStatus pending() {
		return new WorkflowSessionStatus(null, "PENDING", null, null, null, null);
	}
}
