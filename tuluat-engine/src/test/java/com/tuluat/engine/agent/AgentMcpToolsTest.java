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
import com.tuluat.protocols.McpClientRegistry;
import com.tuluat.protocols.McpClientConnection;
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

/**
 * Tests for MCP tool wiring in AgentExecutionService (ADR 013).
 */
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

		executionService = new AgentExecutionService(toolRegistry, Optional.empty(), Optional.of(chatModel),
				guardrailPipeline, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.of(mcpClientRegistry));

		when(chatModel.call(any(Prompt.class)))
				.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("MCP-informed response")))));
	}

	@DisplayName("Should invoke MCP tools declared in agent spec mcpServers")
	@Test
	void invokesMcpToolsFromAgentSpec() {
		McpServerRef mcpRef = new McpServerRef("bloomberg-mcp", null);

		when(mcpClientRegistry.findClient("bloomberg-mcp")).thenReturn(
				Optional.of(new McpClientConnection("bloomberg-mcp", "http://bloomberg:9000", "SSE", "NONE")));
		when(mcpClientRegistry.getAvailableClientNames()).thenReturn(List.of("bloomberg-mcp"));
		when(mcpClientRegistry.invokeTool(eq("bloomberg-mcp"), anyString(), anyMap()))
				.thenReturn(McpToolResult.ok("stock-price", "{\"price\": 847.50}"));

		AiAgent agent = agentWithMcp("mcp-agent", List.of(mcpRef));
		LlmProvider provider = defaultProvider();

		AgentResponse response = executionService.processAgentPrompt(agent, provider, "What is the stock price?", null);

		assertNotNull(response);
		assertFalse(response.isBlocked());
		verify(mcpClientRegistry).invokeTool(eq("bloomberg-mcp"), eq("bloomberg-mcp"), anyMap());
	}

	@DisplayName("Should include MCP tool results in tool context of system prompt")
	@Test
	void includesMcpResultsInSystemPrompt() {
		McpServerRef mcpRef = new McpServerRef("weather-mcp", null);

		when(mcpClientRegistry.findClient("weather-mcp"))
				.thenReturn(Optional.of(new McpClientConnection("weather-mcp", "http://weather:9000", "SSE", "NONE")));
		when(mcpClientRegistry.getAvailableClientNames()).thenReturn(List.of("weather-mcp"));
		when(mcpClientRegistry.invokeTool(eq("weather-mcp"), anyString(), anyMap()))
				.thenReturn(McpToolResult.ok("forecast", "Sunny, 22C"));

		AiAgent agent = agentWithMcp("weather-agent", List.of(mcpRef));
		LlmProvider provider = defaultProvider();

		AgentResponse response = executionService.processAgentPrompt(agent, provider, "What is the weather?", null);

		assertNotNull(response);
		assertTrue(response.systemPrompt().contains("Sunny, 22C"), "System prompt should contain MCP tool result");
		assertTrue(response.systemPrompt().contains("mcp:"), "System prompt should show namespaced MCP tool name");
	}

	@DisplayName("Should handle MCP tool failures gracefully and continue execution")
	@Test
	void handlesMcpToolFailureGracefully() {
		McpServerRef mcpRef = new McpServerRef("failing-mcp", null);

		when(mcpClientRegistry.findClient("failing-mcp"))
				.thenReturn(Optional.of(new McpClientConnection("failing-mcp", "http://fail:9000", "SSE", "NONE")));
		when(mcpClientRegistry.getAvailableClientNames()).thenReturn(List.of("failing-mcp"));
		when(mcpClientRegistry.invokeTool(eq("failing-mcp"), anyString(), anyMap()))
				.thenThrow(new RuntimeException("MCP connection refused"));

		AiAgent agent = agentWithMcp("resilient-agent", List.of(mcpRef));
		LlmProvider provider = defaultProvider();

		AgentResponse response = executionService.processAgentPrompt(agent, provider, "Try failing MCP", null);

		assertNotNull(response);
		assertFalse(response.isBlocked());
	}

	@DisplayName("Should skip MCP servers that are not registered")
	@Test
	void skipsUnregisteredMcpServers() {
		McpServerRef mcpRef = new McpServerRef("missing-mcp", null);

		when(mcpClientRegistry.findClient("missing-mcp")).thenReturn(Optional.empty());

		AiAgent agent = agentWithMcp("skip-agent", List.of(mcpRef));
		LlmProvider provider = defaultProvider();

		AgentResponse response = executionService.processAgentPrompt(agent, provider, "Hello", null);

		assertNotNull(response);
		verify(mcpClientRegistry, never()).invokeTool(anyString(), anyString(), anyMap());
	}

	@DisplayName("Should continue normally when McpClientRegistry is not available")
	@Test
	void gracefulDegradationWithoutMcpRegistry() {
		var service = new AgentExecutionService(toolRegistry, Optional.empty(), Optional.of(chatModel),
				guardrailPipeline, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty());

		McpServerRef mcpRef = new McpServerRef("optional-mcp", null);
		AiAgent agent = agentWithMcp("no-mcp-agent", List.of(mcpRef));
		LlmProvider provider = defaultProvider();

		AgentResponse response = service.processAgentPrompt(agent, provider, "Hello", null);

		assertNotNull(response);
		assertEquals("MCP-informed response", response.answer());
	}

	@DisplayName("Should not invoke MCP tools when mcpServers list is empty")
	@Test
	void skipsWhenMcpServersEmpty() {
		AiAgent agent = agentWithMcp("empty-mcp-agent", List.of());
		LlmProvider provider = defaultProvider();

		executionService.processAgentPrompt(agent, provider, "Hello", null);

		verify(mcpClientRegistry, never()).findClient(anyString());
		verify(mcpClientRegistry, never()).invokeTool(anyString(), anyString(), anyMap());
	}

	private AiAgent agentWithMcp(String name, List<McpServerRef> mcpServers) {
		AiAgent agent = new AiAgent();
		var meta = new ObjectMetaBuilder().withName(name).withNamespace("default").build();
		agent.setMetadata(meta);
		agent.setSpec(new AiAgentSpec(new ProviderRef("provider", "default"), null, "You are test", null, List.of(),
				List.of(), List.of(new ToolDefinition("weather", "Weather tool", true, Map.of())), List.of(),
				mcpServers, new GuardrailsConfig(null, null, null), null, null, 1));
		return agent;
	}

	private LlmProvider defaultProvider() {
		LlmProvider provider = new LlmProvider();
		var meta = new ObjectMetaBuilder().withName("provider").withNamespace("default").build();
		provider.setMetadata(meta);
		provider.setSpec(new LlmProviderSpec("OPENAI", "http://localhost", null, "deepseek-chat", 0.7, 1000, 0.0, 0.0,
				List.of()));
		return provider;
	}
}