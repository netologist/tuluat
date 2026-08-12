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
				"User prompt", List.of(new SkillDefinition("pdf-processing", "PDF skill", true, Map.of())),
				List.of(new SkillSource("FOLDER", "/skills", true)),
				List.of(new ToolDefinition("calculator", "Math tool", true, Map.of())),
				List.of(new ToolSource("FOLDER", "/tools", true)),
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
		assertEquals(1, agent.getSpec().skills().size());
		assertEquals(1, agent.getSpec().tools().size());
		assertTrue(agent.getSpec().guardrails().piiMasking().enabled());

		// Status factories
		AiAgentStatus readyStatus = AiAgentStatus.ready("http://agent.example.com", List.of("pdf-processing"),
				List.of("calculator"), "gpt-4o", "Agent active", 1L);
		assertEquals("Ready", readyStatus.phase());
		assertEquals("http://agent.example.com", readyStatus.ingressUrl());
		assertEquals(List.of("pdf-processing"), readyStatus.activeSkills());
		assertEquals(List.of("calculator"), readyStatus.activeTools());

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
		workflow.setMetadata(new ObjectMetaBuilder().withName("wf-1").withNamespace("tuluat-system").build());

		var spec = new AiWorkflowSpec("Multi-Agent Researcher", "node-1", List.of(new NodeDefinition("node-1", "AGENT", "agent-1", "Prompt", "out", null, null)), List.of(new EdgeDefinition("node-1", "node-2", "true")), new MemoryConfig(10, true, "vectors"), null);
		workflow.setSpec(spec);

		assertEquals("wf-1", workflow.getMetadata().getName());
		assertEquals(10, workflow.getSpec().memoryConfig().shortMemorySize());
		assertTrue(workflow.getSpec().memoryConfig().enableLongMemory());
	}

	@Test
	@DisplayName("LlmProvider CR construct and status factory methods")
	void testLlmProviderCr() {
		var provider = new LlmProvider();
		provider.setMetadata(new ObjectMetaBuilder().withName("openai-1").withNamespace("tuluat-system").build());

		var spec = new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", new SecretKeyRef("secret-1", "api-key"),
				"gpt-4o", 0.7, 2048, 0.0025, 0.01, List.of(new ModelFallback("backup", "tuluat-system", "llama3")));
		provider.setSpec(spec);

		assertEquals("OPENAI", provider.getSpec().providerType());
		assertEquals("gpt-4o", provider.getSpec().defaultModel());

		LlmProviderStatus status = LlmProviderStatus.ready("Provider ready", 1L);
		assertEquals("Ready", status.phase());
	}

	@Test
	@DisplayName("McpServer CR construct and status defaults")
	void testMcpServerCr() {
		var server = new McpServer();
		server.setMetadata(new ObjectMetaBuilder().withName("mcp-1").withNamespace("tuluat-system").build());
		server.setSpec(new McpServerSpec("http://mcp-server:8080/sse", "SSE", "NONE", null, 30, "Postgres MCP"));

		assertEquals("SSE", server.getSpec().transport());
		McpServerStatus status = McpServerStatus.ready("Server connected", 1L);
		assertEquals("Ready", status.phase());
	}

	@Test
	@DisplayName("WorkflowSession CR construct and spec parameters")
	void testWorkflowSessionCr() {
		var session = new WorkflowSession();
		session.setMetadata(new ObjectMetaBuilder().withName("session-1").withNamespace("tuluat-system").build());
		session.setSpec(new WorkflowSessionSpec("wf-1", "input text", Map.of()));

		assertEquals("wf-1", session.getSpec().workflowRef());
		assertEquals("input text", session.getSpec().input());

		WorkflowSessionStatus status = WorkflowSessionStatus.pending();
		assertEquals("PENDING", status.phase());
	}
}
