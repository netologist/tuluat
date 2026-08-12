package com.tuluat.engine.temporal.util;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.MemoryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkflowUtils")
class WorkflowUtilsTest {

	@Nested
	@DisplayName("resolveNextNodeId")
	class ResolveNextNodeId {

		@Test
		@DisplayName("follows unconditional edge when conditionResult is true")
		void followsUnconditionalEdgeTrue() {
			var spec = spec(edge("a", "b", null));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", true)).isEqualTo("b");
		}

		@Test
		@DisplayName("follows unconditional edge when conditionResult is false")
		void followsUnconditionalEdgeFalse() {
			var spec = spec(edge("a", "b", null));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", false)).isEqualTo("b");
		}

		@Test
		@DisplayName("follows conditional edge when result matches")
		void followsMatchingConditionalEdge() {
			var spec = spec(edge("a", "b-true", "true"), edge("a", "b-false", "false"));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", true)).isEqualTo("b-true");
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", false)).isEqualTo("b-false");
		}

		@Test
		@DisplayName("skips mismatched conditional and falls through to unconditional")
		void fallsThroughToUnconditional() {
			var spec = spec(edge("a", "b-conditional", "false"), edge("a", "b-default", null));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", true)).isEqualTo("b-default");
		}

		@Test
		@DisplayName("returns null when no edge matches from-node")
		void returnsNullWhenNoMatchingEdge() {
			var spec = spec(edge("x", "y", null));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", true)).isNull();
		}

		@Test
		@DisplayName("returns null when all conditional edges mismatch and no fallback")
		void returnsNullWhenAllConditionsMismatch() {
			var spec = spec(edge("a", "b", "false"));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", true)).isNull();
		}

		@Test
		@DisplayName("returns null when edges list is empty")
		void returnsNullForEmptyEdges() {
			var spec = spec();
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", true)).isNull();
		}

		@Test
		@DisplayName("picks the first unconditional edge when multiple exist")
		void picksFirstUnconditional() {
			var spec = spec(edge("a", "first", null), edge("a", "second", null));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", true)).isEqualTo("first");
		}

		@Test
		@DisplayName("empty string condition is unconditional")
		void emptyConditionIsUnconditional() {
			var spec = spec(edge("a", "b", ""));
			assertThat(WorkflowUtils.resolveNextNodeId(spec, "a", false)).isEqualTo("b");
		}
	}

	private static AiWorkflowSpec spec(EdgeDefinition... edges) {
		return new AiWorkflowSpec("test-workflow", "start", List.of(), List.of(edges), new MemoryConfig(), null);
	}

	private static EdgeDefinition edge(String from, String to, String condition) {
		return new EdgeDefinition(from, to, condition);
	}
}
