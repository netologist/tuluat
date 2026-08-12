package com.tuluat.engine.temporal.strategy;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.MemoryConfig;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.temporal.GraphNodeActivities;
import com.tuluat.engine.temporal.context.WorkflowExecutionContext;
import com.tuluat.engine.temporal.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AgentNodeExecutor")
class AgentNodeExecutorTest {

	private static final UUID SESSION_ID = UUID.randomUUID();
	private AgentNodeExecutor executor;
	private GraphNodeActivities activities;

	@BeforeEach
	void setUp() {
		executor = new AgentNodeExecutor();
		activities = mock(GraphNodeActivities.class);
	}

	@Test
	@DisplayName("supports AGENT node type")
	void supportsAgentType() {
		assertThat(executor.getSupportedType()).isEqualTo(NodeType.AGENT);
	}

	@Nested
	@DisplayName("execute")
	class Execute {

		@Test
		@DisplayName("delegates to activities and merges result into context")
		void delegatesAndMergesContext() {
			var node = new NodeDefinition("agent-1", "AGENT", "researcher", "Research {{input}}", "research_data", null,
					null);
			var spec = spec(edge("agent-1", "next-node", null));
			Map<String, Object> contextData = new HashMap<>(Map.of("input", "test topic"));

			Map<String, Object> activityResult = Map.of("research_data", "some findings");
			when(activities.executeAgentNode(eq(SESSION_ID), eq(node), any())).thenReturn(activityResult);

			var ctx = new WorkflowExecutionContext(SESSION_ID, contextData, activities, () -> null, () -> false, () -> {
			});

			String next = executor.execute(node, spec, ctx);

			assertThat(next).isEqualTo("next-node");
			assertThat(ctx.contextData()).containsEntry("research_data", "some findings");
			assertThat(ctx.contextData()).containsEntry("input", "test topic"); // preserved
			verify(activities).executeAgentNode(eq(SESSION_ID), eq(node), any());
		}

		@Test
		@DisplayName("returns null when no outgoing edge exists")
		void returnsNullWhenNoEdge() {
			var node = new NodeDefinition("agent-1", "AGENT", "researcher", "prompt", "out", null, null);
			var spec = spec(); // no edges
			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> null, () -> false,
					() -> {
					});
			when(activities.executeAgentNode(any(), any(), any())).thenReturn(Map.of());

			String next = executor.execute(node, spec, ctx);

			assertThat(next).isNull();
		}

		@Test
		@DisplayName("activity result overwrites existing context keys")
		void overwritesExistingKeys() {
			var node = new NodeDefinition("a", "AGENT", "ref", "p", "key", null, null);
			var spec = spec(edge("a", "next", null));
			Map<String, Object> contextData = new HashMap<>(Map.of("key", "old-value"));
			when(activities.executeAgentNode(any(), any(), any())).thenReturn(Map.of("key", "new-value"));

			var ctx = new WorkflowExecutionContext(SESSION_ID, contextData, activities, () -> null, () -> false, () -> {
			});

			executor.execute(node, spec, ctx);
			assertThat(ctx.contextData()).containsEntry("key", "new-value");
		}
	}

	private static AiWorkflowSpec spec(EdgeDefinition... edges) {
		return new AiWorkflowSpec("test", "start", List.of(), List.of(edges), new MemoryConfig(), null);
	}

	private static EdgeDefinition edge(String from, String to, String condition) {
		return new EdgeDefinition(from, to, condition);
	}
}
