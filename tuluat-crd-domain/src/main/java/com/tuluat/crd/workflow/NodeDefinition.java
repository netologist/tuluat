package com.tuluat.crd.workflow;

public record NodeDefinition(String id, String type, String agentRef, String inputTemplate, String outputKey,
		String expression, String outputSchema) {
}
