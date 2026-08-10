package com.tuluat.engine.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTelemetryService {

	private final MeterRegistry meterRegistry;

	public WorkflowTelemetryService(@Autowired(required = false) MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void recordSessionCreated(String workflowName) {
		if (meterRegistry != null) {
			Counter.builder("ai.workflow.session.created.total").tag("workflow", workflowName)
					.description("Total workflow sessions created").register(meterRegistry).increment();
		}
	}

	public void recordSessionCompleted(String workflowName, String status) {
		if (meterRegistry != null) {
			Counter.builder("ai.workflow.session.completed.total").tag("workflow", workflowName).tag("status", status)
					.description("Total workflow sessions completed").register(meterRegistry).increment();
		}
	}

	public void recordNodeExecuted(String workflowName, String nodeType, String nodeId) {
		if (meterRegistry != null) {
			Counter.builder("ai.workflow.node.executed.total").tag("workflow", workflowName).tag("node_type", nodeType)
					.tag("node_id", nodeId).description("Total workflow node executions").register(meterRegistry)
					.increment();
		}
	}
}
