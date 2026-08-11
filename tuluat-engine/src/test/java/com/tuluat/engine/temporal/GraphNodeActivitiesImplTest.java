package com.tuluat.engine.temporal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tuluat.crd.workflow.NodeDefinition;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GraphNodeActivitiesImplTest {

	private GraphNodeActivitiesImpl activities;

	@BeforeEach
	void setUp() {
		activities = new GraphNodeActivitiesImpl(null, Optional.empty(), Optional.empty(), Optional.empty());
	}

	@Test
	@DisplayName("Condition node supports top-level context variables")
	void evaluatesTopLevelVariable() {
		NodeDefinition node = new NodeDefinition("check", "CONDITION", null, null, null, "#status == 'OK'", null);

		Map<String, Object> context = new HashMap<>();
		context.put("status", "OK");

		assertTrue(activities.evaluateConditionNode(UUID.randomUUID(), node, context));
	}

	@Test
	@DisplayName("Condition node supports #data map access used by workflow CRD samples")
	void evaluatesDataVariable() {
		NodeDefinition node = new NodeDefinition("check", "CONDITION", null, null, null,
				"#data['research_data'] != null", null);

		Map<String, Object> context = new HashMap<>();
		context.put("research_data", "findings");

		assertTrue(activities.evaluateConditionNode(UUID.randomUUID(), node, context));
		assertFalse(activities.evaluateConditionNode(UUID.randomUUID(), node, new HashMap<>()));
	}
}
