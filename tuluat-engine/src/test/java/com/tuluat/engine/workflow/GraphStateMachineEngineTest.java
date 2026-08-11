package com.tuluat.engine.workflow;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.NodeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GraphStateMachineEngineTest {

	private GraphStateMachineEngine engine;

	@BeforeEach
	void setUp() {
		engine = new GraphStateMachineEngine(null, Optional.empty(), Optional.empty(), Optional.empty());
	}

	@Test
	@DisplayName("Should evaluate condition node expression correctly using SpEL")
	void shouldEvaluateConditionNodeExpression() {
		NodeDefinition condNode = new NodeDefinition("check-result", "CONDITION", null, null, null, "#status == 'OK'",
				null);

		Map<String, Object> context = new HashMap<>();
		context.put("status", "OK");

		boolean result = engine.evaluateCondition(condNode.expression(), context);
		assertTrue(result);
	}

	@Test
	@DisplayName("Should evaluate #data map access used by workflow CRD samples")
	void shouldEvaluateDataVariableCondition() {
		NodeDefinition condNode = new NodeDefinition("check-result", "CONDITION", null, null, null,
				"#data['status'] == 'OK'", null);

		Map<String, Object> context = new HashMap<>();
		context.put("status", "OK");

		boolean result = engine.evaluateCondition(condNode.expression(), context);
		assertTrue(result);
	}

	@Test
	@DisplayName("Should evaluate #data null check from multi-agent-researcher sample")
	void shouldEvaluateDataNullCheckCondition() {
		Map<String, Object> context = new HashMap<>();
		context.put("research_data", "some findings");

		assertTrue(engine.evaluateCondition("#data['research_data'] != null", context));
		assertFalse(engine.evaluateCondition("#data['missing_key'] != null", context));
	}

	@Test
	@DisplayName("Should find next node ID based on edge conditions")
	void shouldFindNextNode() {
		EdgeDefinition edge1 = new EdgeDefinition("check-result", "success-node", "true");
		EdgeDefinition edge2 = new EdgeDefinition("check-result", "retry-node", "false");

		AiWorkflowSpec spec = new AiWorkflowSpec(null, null, null, List.of(edge1, edge2), null);

		String nextNode = engine.resolveNextNodeId(spec, "check-result", true);
		assertEquals("success-node", nextNode);

		String failNode = engine.resolveNextNodeId(spec, "check-result", false);
		assertEquals("retry-node", failNode);
	}
}
