package com.tuluat.engine.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class WorkflowTelemetryService {

	private final Optional<MeterRegistry> meterRegistry;

	public WorkflowTelemetryService(Optional<MeterRegistry> meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void recordSessionCreated(String workflowName) {
		meterRegistry.ifPresent(registry -> Counter.builder("ai.workflow.session.created.total")
				.tag("workflow", workflowName)
				.description("Total workflow sessions created")
				.register(registry)
				.increment());
	}

	public void recordSessionCompleted(String workflowName, String status) {
		meterRegistry.ifPresent(registry -> Counter.builder("ai.workflow.session.completed.total")
				.tag("workflow", workflowName)
				.tag("status", status)
				.description("Total workflow sessions completed")
				.register(registry)
				.increment());
	}

	public void recordNodeExecuted(String workflowName, String nodeType, String nodeId) {
		meterRegistry.ifPresent(registry -> Counter.builder("ai.workflow.node.executed.total")
				.tag("workflow", workflowName)
				.tag("node_type", nodeType)
				.tag("node_id", nodeId)
				.description("Total workflow node executions")
				.register(registry)
				.increment());
	}
}
