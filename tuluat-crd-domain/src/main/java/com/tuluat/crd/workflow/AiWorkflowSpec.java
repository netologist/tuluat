package com.tuluat.crd.workflow;

import java.util.ArrayList;
import java.util.List;

public record AiWorkflowSpec(
		String description,
		String initialNode,
		List<NodeDefinition> nodes,
		List<EdgeDefinition> edges,
		MemoryConfig memoryConfig) {

	public AiWorkflowSpec {
		if (nodes == null) nodes = new ArrayList<>();
		if (edges == null) edges = new ArrayList<>();
		if (memoryConfig == null) memoryConfig = new MemoryConfig();
	}
}
