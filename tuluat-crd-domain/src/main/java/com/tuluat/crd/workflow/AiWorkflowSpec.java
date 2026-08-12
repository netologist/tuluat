package com.tuluat.crd.workflow;

import java.util.ArrayList;
import java.util.List;

import java.math.BigDecimal;

public record AiWorkflowSpec(String description, String initialNode, List<NodeDefinition> nodes,
		List<EdgeDefinition> edges, MemoryConfig memoryConfig, BigDecimal budgetLimitUsd) {

	public AiWorkflowSpec {
		if (nodes == null)
			nodes = new ArrayList<>();
		if (edges == null)
			edges = new ArrayList<>();
		if (memoryConfig == null)
			memoryConfig = new MemoryConfig();
		if (budgetLimitUsd == null)
			budgetLimitUsd = BigDecimal.ZERO;
	}
}
