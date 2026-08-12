package com.tuluat.engine.temporal.strategy;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.MemoryConfig;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.temporal.GraphNodeActivities;
import com.tuluat.engine.temporal.context.WorkflowExecutionContext;
import com.tuluat.engine.temporal.model.NodeType;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("HumanApprovalNodeExecutor")
class HumanApprovalNodeExecutorTest {

	private static final UUID SESSION_ID = UUID.randomUUID();
	private HumanApprovalNodeExecutor executor;
	private GraphNodeActivities activities;
	private MockedStatic<Workflow> workflowMock;

	@BeforeEach
	void setUp() {
		executor = new HumanApprovalNodeExecutor();
		activities = mock(GraphNodeActivities.class);
		workflowMock = mockStatic(Workflow.class);
	}

	@AfterEach
	void tearDown() {
		workflowMock.close();
	}

	@Test
	@DisplayName("supports HUMAN_APPROVAL node type")
	void supportsHumanApprovalType() {
		assertThat(executor.getSupportedType()).isEqualTo(NodeType.HUMAN_APPROVAL);
	}

	@Nested
	@DisplayName("execute")
	class Execute {

		@Test
		@DisplayName("blocks until approval and processes approved signal")
		void processesApprovedSignal() {
			var node = new NodeDefinition("approval-1", "HUMAN_APPROVAL", null, null, null, null, null);
			var spec = spec(edge("approval-1", "approved-path", "true"), edge("approval-1", "rejected-path", "false"));
			var resetCalled = new AtomicBoolean(false);
			var signal = new ApprovalSignal(true, "Looks good!", Map.of("reviewer", "alice"));

			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> signal, () -> true,
					() -> resetCalled.set(true));

			String next = executor.execute(node, spec, ctx);

			assertThat(next).isEqualTo("approved-path");
			assertThat(ctx.contextData()).containsEntry("approval_feedback", "Looks good!");
			assertThat(ctx.contextData()).containsEntry("approval_metadata", Map.of("reviewer", "alice"));
			assertThat(resetCalled).isTrue();
			verify(activities, times(2)).recordLog(eq(SESSION_ID), eq("approval-1"), eq("INFO"), any());
		}

		@Test
		@DisplayName("follows rejection edge when not approved")
		void followsRejectionEdge() {
			var node = new NodeDefinition("approval-1", "HUMAN_APPROVAL", null, null, null, null, null);
			var spec = spec(edge("approval-1", "approved", "true"), edge("approval-1", "rejected", "false"));
			var signal = new ApprovalSignal(false, "Needs revision", null);

			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> signal, () -> true,
					() -> {
					});

			String next = executor.execute(node, spec, ctx);
			assertThat(next).isEqualTo("rejected");
		}

		@Test
		@DisplayName("handles null feedback and metadata gracefully")
		void handlesNullFeedbackAndMetadata() {
			var node = new NodeDefinition("approval-1", "HUMAN_APPROVAL", null, null, null, null, null);
			var spec = spec(edge("approval-1", "next", null));
			var signal = new ApprovalSignal(true, null, null);

			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> signal, () -> true,
					() -> {
					});

			String next = executor.execute(node, spec, ctx);
			assertThat(next).isEqualTo("next");
			assertThat(ctx.contextData()).doesNotContainKey("approval_feedback");
			assertThat(ctx.contextData()).doesNotContainKey("approval_metadata");
		}

		@Test
		@DisplayName("ignores blank feedback")
		void ignoresBlankFeedback() {
			var node = new NodeDefinition("approval-1", "HUMAN_APPROVAL", null, null, null, null, null);
			var spec = spec(edge("approval-1", "next", null));
			var signal = new ApprovalSignal(true, "   ", Map.of());

			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> signal, () -> true,
					() -> {
					});

			executor.execute(node, spec, ctx);
			assertThat(ctx.contextData()).doesNotContainKey("approval_feedback");
			assertThat(ctx.contextData()).containsEntry("approval_metadata", Map.of());
		}

		@Test
		@DisplayName("returns null when no matching edge for approval result")
		void returnsNullWhenEdgeMissing() {
			var node = new NodeDefinition("approval-1", "HUMAN_APPROVAL", null, null, null, null, null);
			var spec = spec(edge("approval-1", "only-when-true", "true"));
			var signal = new ApprovalSignal(false, null, null);

			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> signal, () -> true,
					() -> {
					});

			assertThat(executor.execute(node, spec, ctx)).isNull();
		}
	}

	private static AiWorkflowSpec spec(EdgeDefinition... edges) {
		return new AiWorkflowSpec("test", "start", List.of(), List.of(edges), new MemoryConfig(), null);
	}

	private static EdgeDefinition edge(String from, String to, String condition) {
		return new EdgeDefinition(from, to, condition);
	}
}
