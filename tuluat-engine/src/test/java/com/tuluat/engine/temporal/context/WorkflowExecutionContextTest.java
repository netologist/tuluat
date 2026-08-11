package com.tuluat.engine.temporal.context;

import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.temporal.GraphNodeActivities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

@DisplayName("WorkflowExecutionContext")
class WorkflowExecutionContextTest {

	private static final UUID SESSION_ID = UUID.randomUUID();
	private final GraphNodeActivities activities = mock(GraphNodeActivities.class);

	@Nested
	@DisplayName("construction")
	class Construction {

		@Test
		@DisplayName("creates with all required fields")
		void createsWithAllFields() {
			var approvalSignal = new ApprovalSignal(true, null, null);
			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> approvalSignal,
					() -> false, () -> {
					});

			assertThat(ctx.sessionId()).isEqualTo(SESSION_ID);
			assertThat(ctx.contextData()).isEmpty();
			assertThat(ctx.activities()).isSameAs(activities);
		}

		@Test
		@DisplayName("rejects null sessionId")
		void rejectsNullSessionId() {
			assertThatNullPointerException().isThrownBy(() -> new WorkflowExecutionContext(null, new HashMap<>(),
					activities, () -> null, () -> false, () -> {
					})).withMessageContaining("sessionId");
		}

		@Test
		@DisplayName("rejects null contextData")
		void rejectsNullContextData() {
			assertThatNullPointerException().isThrownBy(
					() -> new WorkflowExecutionContext(SESSION_ID, null, activities, () -> null, () -> false, () -> {
					})).withMessageContaining("contextData");
		}

		@Test
		@DisplayName("rejects null activities")
		void rejectsNullActivities() {
			assertThatNullPointerException().isThrownBy(() -> new WorkflowExecutionContext(SESSION_ID, new HashMap<>(),
					null, () -> null, () -> false, () -> {
					})).withMessageContaining("activities");
		}

		@Test
		@DisplayName("allows null suppliers and runnables")
		void allowsNullSuppliers() {
			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, null, null, null);
			assertThat(ctx.signalSupplier()).isNull();
			assertThat(ctx.approvalReceivedSupplier()).isNull();
			assertThat(ctx.resetApprovalFlag()).isNull();
		}
	}

	@Nested
	@DisplayName("context data mutation")
	class ContextDataMutation {

		@Test
		@DisplayName("contextData map is mutable via reference")
		void contextDataIsMutable() {
			Map<String, Object> data = new HashMap<>();
			var ctx = new WorkflowExecutionContext(SESSION_ID, data, activities, () -> null, () -> false, () -> {
			});

			ctx.contextData().put("key", "value");
			assertThat(data).containsEntry("key", "value");
		}
	}

	@Nested
	@DisplayName("supplier and runnable behavior")
	class SupplierBehavior {

		@Test
		@DisplayName("signalSupplier returns provided value")
		void signalSupplierWorks() {
			var signal = new ApprovalSignal(true, "ok", Map.of());
			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities, () -> signal, () -> false,
					() -> {
					});

			assertThat(ctx.signalSupplier().get()).isSameAs(signal);
		}

		@Test
		@DisplayName("approvalReceivedSupplier returns false initially")
		void approvalReceivedSupplierWorks() {
			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities,
					() -> new ApprovalSignal(true, null, null), () -> false, () -> {
					});

			assertThat(ctx.approvalReceivedSupplier().getAsBoolean()).isFalse();
		}

		@Test
		@DisplayName("resetApprovalFlag invokes provided runnable")
		void resetApprovalFlagWorks() {
			var flag = new AtomicBoolean(false);
			var ctx = new WorkflowExecutionContext(SESSION_ID, new HashMap<>(), activities,
					() -> new ApprovalSignal(true, null, null), () -> false, () -> flag.set(true));

			ctx.resetApprovalFlag().run();
			assertThat(flag).isTrue();
		}
	}
}
