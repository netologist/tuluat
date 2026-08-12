package com.tuluat.engine.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.McpServerRef;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.OutputValidationFilter;
import com.tuluat.guardrails.PiiMaskingFilter;
import com.tuluat.guardrails.PromptInjectionFilter;
import com.tuluat.protocols.McpClientConnection;
import com.tuluat.protocols.McpClientRegistry;
import com.tuluat.protocols.McpToolResult;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class AgentMcpToolsTest {

	private ToolRegistry toolRegistry;
	private GuardrailPipeline guardrailPipeline;
	private ChatModel chatModel;
	private McpClientRegistry mcpClientRegistry;
	private AgentExecutionService executionService;

	@BeforeEach
	void setUp() {
		toolRegistry = new ToolRegistry();
		guardrailPipeline = new GuardrailPipeline(List.of(new PiiMaskingFilter(), new PromptInjectionFilter()),
				List.of(new OutputValidationFilter()));
		chatModel = mock(ChatModel.class);
		mcpClientRegistry = mock(McpClientRegistry.class);

		executionService = new AgentExecutionService(toolRegistry, Optional.of(chatModel), guardrailPipeline,
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.of(mcpClientRegistry));

		when(chatModel.call(any(Prompt.class)))
				.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("MCP-informed response")))));
	}

	@Test
	void invokesMcpToolsFromAgentSpec() {
		McpServerRef mcpRef = new McpServerRef("bloomberg-mcp", null);
		when(mcpClientRegistry.findClient("bloomberg-mcp"))
				.thenReturn(Optional.of(new McpClientConnection("bloomberg-mcp", "http://b:9000", "SSE", "NONE")));
		when(mcpClientRegistry.getAvailableClientNames()).thenReturn(List.of("bloomberg-mcp"));
		when(mcpClientRegistry.invokeTool(eq("bloomberg-mcp"), anyString(), anyMap()))
				.thenReturn(McpToolResult.ok("stock-price", "847.50"));

		AgentResponse response = executionService.processAgentPrompt(agentWithMcp("mcp", List.of(mcpRef)),
				defaultProvider(), "What is the stock price?", null);
		assertNotNull(response);
		assertFalse(response.isBlocked());
	}

	@Test
	void includesMcpResultsInSystemPrompt() {
		McpServerRef mcpRef = new McpServerRef("weather-mcp", null);
		when(mcpClientRegistry.findClient("weather-mcp"))
				.thenReturn(Optional.of(new McpClientConnection("weather-mcp", "http://w:9000", "SSE", "NONE")));
		when(mcpClientRegistry.getAvailableClientNames()).thenReturn(List.of("weather-mcp"));
		when(mcpClientRegistry.invokeTool(eq("weather-mcp"), anyString(), anyMap()))
				.thenReturn(McpToolResult.ok("forecast", "Sunny, 22C"));

		AgentResponse response = executionService.processAgentPrompt(agentWithMcp("w", List.of(mcpRef)),
				defaultProvider(), "weather?", null);
		assertTrue(response.systemPrompt().contains("Sunny, 22C"));
	}

	@Test
	void handlesMcpToolFailureGracefully() {
		McpServerRef mcpRef = new McpServerRef("failing-mcp", null);
		when(mcpClientRegistry.findClient("failing-mcp"))
				.thenReturn(Optional.of(new McpClientConnection("failing-mcp", "http://f:9000", "SSE", "NONE")));
		when(mcpClientRegistry.getAvailableClientNames()).thenReturn(List.of("failing-mcp"));
		when(mcpClientRegistry.invokeTool(eq("failing-mcp"), anyString(), anyMap()))
				.thenThrow(new RuntimeException("MCP connection refused"));

		AgentResponse response = executionService.processAgentPrompt(agentWithMcp("r", List.of(mcpRef)),
				defaultProvider(), "Try failing MCP", null);
		assertNotNull(response);
		assertFalse(response.isBlocked());
	}

	@Test
	void skipsUnregisteredMcpServers() {
		when(mcpClientRegistry.findClient("missing-mcp")).thenReturn(Optional.empty());
		AgentResponse response = executionService.processAgentPrompt(
				agentWithMcp("skip", List.of(new McpServerRef("missing-mcp", null))), defaultProvider(), "Hello", null);
		assertNotNull(response);
		verify(mcpClientRegistry, never()).invokeTool(anyString(), anyString(), anyMap());
	}

	@Test
	void gracefulDegradationWithoutMcpRegistry() {
		var svc = new AgentExecutionService(toolRegistry, Optional.of(chatModel), guardrailPipeline, Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		AgentResponse response = svc.processAgentPrompt(agentWithMcp("no-mcp", List.of(new McpServerRef("x", null))),
				defaultProvider(), "Hello", null);
		assertEquals("MCP-informed response", response.answer());
	}

	private AiAgent agentWithMcp(String name, List<McpServerRef> mcpServers) {
		AiAgent agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("default").build());
		agent.setSpec(new AiAgentSpec(new ProviderRef("provider", "default"), null, "You are test", null, List.of(),
				List.of(), List.of(new ToolDefinition("weather", "Weather tool", true, Map.of())), List.of(),
				mcpServers, new GuardrailsConfig(null, null, null), null, null, 1));
		return agent;
	}

	private LlmProvider defaultProvider() {
		LlmProvider provider = new LlmProvider();
		provider.setMetadata(new ObjectMetaBuilder().withName("provider").withNamespace("default").build());
		provider.setSpec(new LlmProviderSpec("OPENAI", "http://localhost", null, "deepseek-chat", 0.7, 1000, 0.0, 0.0,
				List.of()));
		return provider;
	}
}