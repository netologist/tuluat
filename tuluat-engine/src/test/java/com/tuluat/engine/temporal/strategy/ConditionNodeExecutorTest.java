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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ConditionNodeExecutor")
class ConditionNodeExecutorTest {

	private static final UUID SESSION_ID = UUID.randomUUID();
	private ConditionNodeExecutor executor;
	private GraphNodeActivities activities;

	@BeforeEach
	void setUp() {
		executor = new ConditionNodeExecutor();
		activities = mock(GraphNodeActivities.class);
	}

	@Test
	@DisplayName("supports CONDITION node type")
	void supportsConditionType() {
		assertThat(executor.getSupportedType()).isEqualTo(NodeType.CONDITION);
	}

	@Nested
	@DisplayName("execute")
	class Execute {

		@Test
		@DisplayName("follows true edge when evaluation returns true")
		void followsTrueEdge() {
			var node = new NodeDefinition("cond-1", "CONDITION", null, null, null, "#data.key != null", null);
			var spec = spec(edge("cond-1", "true-path", "true"), edge("cond-1", "false-path", "false"));
			var ctx = ctx();

			when(activities.evaluateConditionNode(eq(SESSION_ID), eq(node), any())).thenReturn(true);

			String next = executor.execute(node, spec, ctx);

			assertThat(next).isEqualTo("true-path");
			verify(activities).evaluateConditionNode(eq(SESSION_ID), eq(node), any());
		}

		@Test
		@DisplayName("follows false edge when evaluation returns false")
		void followsFalseEdge() {
			var node = new NodeDefinition("cond-1", "CONDITION", null, null, null, "#data.key == null", null);
			var spec = spec(edge("cond-1", "true-path", "true"), edge("cond-1", "false-path", "false"));
			var ctx = ctx();

			when(activities.evaluateConditionNode(any(), any(), any())).thenReturn(false);

			String next = executor.execute(node, spec, ctx);

			assertThat(next).isEqualTo("false-path");
		}

		@Test
		@DisplayName("returns null when evaluation is true but only false edge exists")
		void returnsNullWhenTrueEdgeMissing() {
			var node = new NodeDefinition("cond-1", "CONDITION", null, null, null, "expr", null);
			var spec = spec(edge("cond-1", "only-false", "false"));
			var ctx = ctx();

			when(activities.evaluateConditionNode(any(), any(), any())).thenReturn(true);

			assertThat(executor.execute(node, spec, ctx)).isNull();
		}

		@Test
		@DisplayName("follows unconditional edge regardless of evaluation result")
		void followsUnconditionalEdge() {
			var node = new NodeDefinition("cond-1", "CONDITION", null, null, null, "expr", null);
			var spec = spec(edge("cond-1", "always", null));

			var ctx = ctx();
			when(activities.evaluateConditionNode(any(), any(), any())).thenReturn(false);
			assertThat(executor.execute(node, spec, ctx)).isEqualTo("always");

			when(activities.evaluateConditionNode(any(), any(), any())).thenReturn(true);
			assertThat(executor.execute(node, spec, ctx)).isEqualTo("always");
		}
	}

	private WorkflowExecutionContext ctx() {
		return new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> null, () -> false, () -> {
		});
	}

	private static AiWorkflowSpec spec(EdgeDefinition... edges) {
		return new AiWorkflowSpec("test", "start", List.of(), List.of(edges), new MemoryConfig(), null);
	}

	private static EdgeDefinition edge(String from, String to, String condition) {
		return new EdgeDefinition(from, to, condition);
	}
}
