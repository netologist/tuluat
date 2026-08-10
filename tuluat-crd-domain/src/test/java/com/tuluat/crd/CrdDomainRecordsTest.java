package com.tuluat.crd;

import com.tuluat.crd.agent.*;
import com.tuluat.crd.mcp.McpServer;
import com.tuluat.crd.mcp.McpServerSpec;
import com.tuluat.crd.mcp.McpServerStatus;
import com.tuluat.crd.provider.*;
import com.tuluat.crd.session.WorkflowSession;
import com.tuluat.crd.session.WorkflowSessionSpec;
import com.tuluat.crd.session.WorkflowSessionStatus;
import com.tuluat.crd.workflow.*;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrdDomainRecordsTest {

	@Test
	@DisplayName("AiAgent CR construct and status factory methods")
	void testAiAgentCrAndStatus() {
		var agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName("agent-1").withNamespace("tuluat-system").build());

		var spec = new AiAgentSpec(new ProviderRef("openai-provider", "tuluat-system"), "gpt-4o", "System prompt",
				"User prompt", List.of(new SkillDefinition("calculator", "Math tool", true, Map.of())),
				List.of(new SkillSource("FOLDER", "/skills", true)),
				List.of(new McpServerRef("postgres-mcp", "tuluat-system")),
				new GuardrailsConfig(new PiiMaskingConfig(true, List.of("EMAIL"), "[REDACTED]"),
						new PromptInjectionConfig(true, "BLOCK"), new OutputValidationConfig(true, 0.8)),
				new A2aConfig(true, "http://a2a-gateway:8080"), new IngressSpec(true, "agent.example.com", "/",
						"Prefix", "nginx", Map.of(), new TlsSpec("tls-secret", List.of("agent.example.com"))),
				2);
		agent.setSpec(spec);

		assertEquals("agent-1", agent.getMetadata().getName());
		assertEquals("gpt-4o", agent.getSpec().model());
		assertEquals(2, agent.getSpec().replicas());
		assertTrue(agent.getSpec().guardrails().piiMasking().enabled());

		// Status factories
		AiAgentStatus readyStatus = AiAgentStatus.ready("http://agent.example.com", List.of("calculator"), "gpt-4o",
				"Agent active", 1L);
		assertEquals("Ready", readyStatus.phase());
		assertEquals("http://agent.example.com", readyStatus.ingressUrl());

		AiAgentStatus reconcilingStatus = AiAgentStatus.reconciling("Reconciling agent", 1L);
		assertEquals("Reconciling", reconcilingStatus.phase());

		AiAgentStatus failedStatus = AiAgentStatus.failed("Error message", 1L);
		assertEquals("Failed", failedStatus.phase());
		assertEquals("Error message", failedStatus.message());
	}

	@Test
	@DisplayName("AiWorkflow CR construct and default memory config")
	void testAiWorkflowCr() {
		var workflow = new AiWorkflow();
		workflow.setMetadata(new ObjectMetaBuilder().withName("wf-1").withNamespace("default").build());

		NodeDefinition node = new NodeDefinition("start-node", "AGENT", "agent-1", "input", "output", null, null);
		EdgeDefinition edge = new EdgeDefinition("start-node", "end-node", null);
		var spec = new AiWorkflowSpec("Test workflow", "start-node", List.of(node), List.of(edge),
				new MemoryConfig(10, true, "document_vectors"));
		workflow.setSpec(spec);

		assertEquals("start-node", workflow.getSpec().initialNode());
		assertEquals(1, workflow.getSpec().nodes().size());
		assertEquals(10, workflow.getSpec().memoryConfig().shortMemorySize());

		AiWorkflowStatus status = new AiWorkflowStatus("Ready", 1);
		workflow.setStatus(status);

		assertEquals("Ready", workflow.getStatus().state());
		assertEquals(1, workflow.getStatus().nodeCount());
	}

	@Test
	@DisplayName("LlmProvider CR construct and status factory methods")
	void testLlmProviderCr() {
		var provider = new LlmProvider();
		provider.setMetadata(new ObjectMetaBuilder().withName("openai").withNamespace("default").build());

		var spec = new LlmProviderSpec("OPENAI", "https://api.openai.com/v1",
				new SecretKeyRef("openai-secret", "api-key"), "gpt-4o", 0.7, 4096, 0.0025, 0.01,
				List.of(new ModelFallback("ollama-provider", "default", "llama3")));
		provider.setSpec(spec);

		assertEquals("OPENAI", provider.getSpec().providerType());
		assertEquals("openai-secret", provider.getSpec().apiKeySecretRef().name());

		LlmProviderStatus ready = LlmProviderStatus.ready("Connected to OpenAI API", 1L);
		assertEquals("Ready", ready.phase());

		LlmProviderStatus pending = LlmProviderStatus.pending("Waiting for secret", 1L);
		assertEquals("Pending", pending.phase());

		LlmProviderStatus error = LlmProviderStatus.error("Invalid API Key", 1L);
		assertEquals("Error", error.phase());
	}

	@Test
	@DisplayName("McpServer CR construct and status defaults")
	void testMcpServerCr() {
		var mcp = new McpServer();
		mcp.setMetadata(new ObjectMetaBuilder().withName("pg-mcp").withNamespace("default").build());

		var spec = new McpServerSpec("http://postgres-mcp:8080/sse", null, null, null, null, "Postgres tools");
		mcp.setSpec(spec);

		assertEquals("SSE", mcp.getSpec().transport()); // Default assigned by record constructor
		assertEquals("NONE", mcp.getSpec().authType()); // Default assigned by record constructor
		assertEquals(30, mcp.getSpec().timeoutSeconds()); // Default assigned by record constructor

		McpServerStatus ready = McpServerStatus.ready("Registered", 1L);
		assertEquals("Ready", ready.phase());

		McpServerStatus error = McpServerStatus.error("Unreachable", 1L);
		assertEquals("Error", error.phase());
	}

	@Test
	@DisplayName("WorkflowSession CR construct and spec parameters")
	void testWorkflowSessionCr() {
		var session = new WorkflowSession();
		session.setMetadata(new ObjectMetaBuilder().withName("session-123").withNamespace("default").build());

		WorkflowSessionSpec spec = new WorkflowSessionSpec("research-workflow", "Initial research query",
				Map.of("maxLoops", 5));
		session.setSpec(spec);

		assertEquals("research-workflow", session.getSpec().workflowRef());
		assertEquals(5, session.getSpec().parameters().get("maxLoops"));

		WorkflowSessionStatus status = new WorkflowSessionStatus("uuid-123", "COMPLETED", "report-node", null, null,
				null);
		session.setStatus(status);

		assertEquals("COMPLETED", session.getStatus().phase());
		assertEquals("uuid-123", session.getStatus().sessionId());
	}
}
