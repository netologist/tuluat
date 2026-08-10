package com.tuluat.crd.workflow;

public record AiWorkflowStatus(String state, int nodeCount) {

	public static AiWorkflowStatus ready() {
		return new AiWorkflowStatus("Ready", 0);
	}
}
