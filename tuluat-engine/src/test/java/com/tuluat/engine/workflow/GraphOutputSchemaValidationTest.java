package com.tuluat.engine.workflow;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.OutputValidationFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
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
		return new GraphStateMachineEngine(service, null, null,
				new GuardrailPipeline(List.of(), List.of(new OutputValidationFilter())));
	}

	private WorkflowSessionEntity session() {
		var s = new WorkflowSessionEntity();
		s.setSessionId(UUID.randomUUID());
		s.setWorkflowName("schema-workflow");
		s.setStatus("RUNNING");
		s.setCurrentNodeId("writer-node");
		s.setLoopCount(0);
		return s;
	}

	private AiWorkflowSpec specWithSchema() {
		NodeDefinition writer = new NodeDefinition();
		writer.setId("writer-node");
		writer.setType("AGENT");
		writer.setAgentRef("writer-agent");
		writer.setOutputKey("final_report");
		writer.setOutputSchema("""
				{"type":"object","required":["summary"],"properties":{"summary":{"type":"string"}}}
				""");

		NodeDefinition done = new NodeDefinition();
		done.setId("done-node");
		done.setType("AGENT");
		done.setAgentRef("writer-agent");
		done.setOutputKey("done");

		AiWorkflowSpec spec = new AiWorkflowSpec();
		spec.setInitialNode("writer-node");
		spec.setNodes(List.of(writer, done));
		spec.setEdges(List.of(edge("writer-node", "done-node")));
		return spec;
	}

	private EdgeDefinition edge(String from, String to) {
		var e = new EdgeDefinition();
		e.setFrom(from);
		e.setTo(to);
		e.setCondition("true");
		return e;
	}

	@Test
	void validJsonOutputAdvancesSession() {
		service = mock(AgentExecutionService.class);
		when(service.executeAgent(org.mockito.ArgumentMatchers.eq("writer-agent"),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
				.thenReturn(AgentResponse.create("writer-agent", "m", "sys", "{\"summary\":\"ok\"}", List.of(),
						com.tuluat.engine.agent.UsageStats.calculate(5, 5, "m", 10)));

		WorkflowSessionEntity result = engine().executeNextStep(specWithSchema(), session(), 10);

		assertEquals("done-node", result.getCurrentNodeId());
		assertNotNull(result.getContextData());
		assertTrue(result.getContextData().contains("\"final_report\""));
	}

	@Test
	void invalidJsonOutputFailsSession() {
		service = mock(AgentExecutionService.class);
		when(service.executeAgent(org.mockito.ArgumentMatchers.eq("writer-agent"),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
				.thenReturn(AgentResponse.create("writer-agent", "m", "sys", "plain text, not json", List.of(),
						com.tuluat.engine.agent.UsageStats.calculate(5, 5, "m", 10)));

		WorkflowSessionEntity result = engine().executeNextStep(specWithSchema(), session(), 10);

		assertEquals("FAILED", result.getStatus());
	}

	@Test
	void noSchemaSkipsValidation() {
		service = mock(AgentExecutionService.class);
		when(service.executeAgent(org.mockito.ArgumentMatchers.eq("writer-agent"),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
				.thenReturn(AgentResponse.create("writer-agent", "m", "sys", "any output", List.of(),
						com.tuluat.engine.agent.UsageStats.calculate(5, 5, "m", 10)));

		AiWorkflowSpec spec = specWithSchema();
		spec.getNodes().get(0).setOutputSchema(null);

		WorkflowSessionEntity result = engine().executeNextStep(spec, session(), 10);
		assertEquals("done-node", result.getCurrentNodeId());
	}
}
