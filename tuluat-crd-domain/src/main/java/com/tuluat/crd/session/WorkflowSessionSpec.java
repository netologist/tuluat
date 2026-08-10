package com.tuluat.crd.session;

import java.util.HashMap;
import java.util.Map;

public record WorkflowSessionSpec(String workflowRef, String input, Map<String, Object> parameters) {

	public WorkflowSessionSpec {
		if (parameters == null)
			parameters = new HashMap<>();
	}

	public WorkflowSessionSpec() {
		this(null, null, new HashMap<>());
	}
}
