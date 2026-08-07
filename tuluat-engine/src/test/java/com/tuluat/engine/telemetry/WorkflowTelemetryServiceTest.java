package com.tuluat.engine.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowTelemetryServiceTest {

    private MeterRegistry meterRegistry;
    private WorkflowTelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        telemetryService = new WorkflowTelemetryService(meterRegistry);
    }

    @Test
    @DisplayName("Should increment metrics counters when recording sessions and nodes")
    void testRecordMetrics() {
        telemetryService.recordSessionCreated("my-workflow");
        telemetryService.recordNodeExecuted("my-workflow", "AGENT", "node-1");
        telemetryService.recordSessionCompleted("my-workflow", "COMPLETED");

        assertNotNull(meterRegistry.find("ai.workflow.session.created.total").counter());
        assertEquals(1.0, meterRegistry.find("ai.workflow.session.created.total").counter().count());

        assertNotNull(meterRegistry.find("ai.workflow.node.executed.total").counter());
        assertEquals(1.0, meterRegistry.find("ai.workflow.node.executed.total").counter().count());

        assertNotNull(meterRegistry.find("ai.workflow.session.completed.total").counter());
        assertEquals(1.0, meterRegistry.find("ai.workflow.session.completed.total").counter().count());
    }
}
