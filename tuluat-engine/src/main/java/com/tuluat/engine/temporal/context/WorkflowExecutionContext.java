package com.tuluat.engine.temporal.context;

import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.temporal.GraphNodeActivities;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public record WorkflowExecutionContext(UUID sessionId, Map<String, Object> contextData, GraphNodeActivities activities,
		Supplier<ApprovalSignal> signalSupplier, BooleanSupplier approvalReceivedSupplier, Runnable resetApprovalFlag) {
	public WorkflowExecutionContext {
		Objects.requireNonNull(sessionId, "sessionId cannot be null");
		Objects.requireNonNull(contextData, "contextData cannot be null");
		Objects.requireNonNull(activities, "activities cannot be null");
	}
}