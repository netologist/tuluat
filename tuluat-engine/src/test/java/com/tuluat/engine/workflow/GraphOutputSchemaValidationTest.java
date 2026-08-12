package com.tuluat.engine.workflow;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.agent.UsageStats;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.OutputValidationFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies node-level outputSchema enforcement in the workflow engine (ADR
 * 004/007): schema-valid output advances the session; invalid output fails it.
 */
class GraphOutputSchemaValidationTest {

	private AgentExecutionService service;

	private GraphStateMachineEngine engine() {
		return new GraphStateMachineEngine(service, Optional.empty(), Optional.empty(),
				Optional.of(new GuardrailPipeline(List.of(), List.of(new OutputValidationFilter()))),
				new ObjectMapper());
	}

	private WorkflowSessionEntity session() {
		var s = new WorkflowSessionEntity();
		s.setSessionId(UUID.randomUUID());
		s.setWorkflowName("schema-workflow");
		s.setStatus(SessionStatus.RUNNING);
		s.setCurrentNodeId("writer-node");
		s.setLoopCount(0);
		return s;
	}

	private AiWorkflowSpec specWithSchema() {
		NodeDefinition writer = new NodeDefinition("writer-node", "AGENT", "writer-agent", null, "final_report", null,
				"""
						{"type":"object","required":["summary"],"properties":{"summary":{"type":"string"}}}
						""");

		NodeDefinition done = new NodeDefinition("done-node", "AGENT", "writer-agent", null, "done", null, null);

		return new AiWorkflowSpec(null, "writer-node", List.of(writer, done), List.of(edge("writer-node", "done-node")),
				null);
	}

	private EdgeDefinition edge(String from, String to) {
		return new EdgeDefinition(from, to, "true");
	}

	@Test
	void validJsonOutputAdvancesSession() {
		service = mock(AgentExecutionService.class);
		when(service.executeAgent(org.mockito.ArgumentMatchers.eq("writer-agent"),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any(UUID.class)))
				.thenReturn(AgentResponse.create("writer-agent", "m", "sys", "{\"summary\":\"ok\"}", List.of(),
						UsageStats.calculate(5, 5, "m", 10)));

		WorkflowSessionEntity result = engine().executeNextStep(specWithSchema(), session(), 10);

		assertEquals("done-node", result.getCurrentNodeId());
		assertNotNull(result.getContextData());
		assertTrue(result.getContextData().contains("\"final_report\""));
	}

	@Test
	void invalidJsonOutputFailsSession() {
		service = mock(AgentExecutionService.class);
		when(service.executeAgent(org.mockito.ArgumentMatchers.eq("writer-agent"),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any(UUID.class)))
				.thenReturn(AgentResponse.create("writer-agent", "m", "sys", "plain text, not json", List.of(),
						UsageStats.calculate(5, 5, "m", 10)));

		WorkflowSessionEntity result = engine().executeNextStep(specWithSchema(), session(), 10);

		assertEquals(SessionStatus.FAILED, result.getStatus());
	}

	@Test
	void noSchemaSkipsValidation() {
		service = mock(AgentExecutionService.class);
		when(service.executeAgent(org.mockito.ArgumentMatchers.eq("writer-agent"),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any(UUID.class)))
				.thenReturn(AgentResponse.create("writer-agent", "m", "sys", "any output", List.of(),
						UsageStats.calculate(5, 5, "m", 10)));

		AiWorkflowSpec spec = specWithSchema();
		NodeDefinition noSchemaNode = new NodeDefinition("writer-node", "AGENT", "writer-agent", null, "final_report",
				null, null);
		spec = new AiWorkflowSpec(spec.description(), spec.initialNode(), List.of(noSchemaNode, spec.nodes().get(1)),
				spec.edges(), spec.memoryConfig());

		WorkflowSessionEntity result = engine().executeNextStep(spec, session(), 10);
	}
}