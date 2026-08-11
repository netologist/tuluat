package com.tuluat.engine.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.engine.gateway.ModelGateway;
import com.tuluat.engine.rag.RagService;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.OutputValidationFilter;
import com.tuluat.guardrails.PiiMaskingFilter;
import com.tuluat.guardrails.PromptInjectionFilter;
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

class AgentExecutionServiceTest {

	private ToolRegistry toolRegistry;
	private GuardrailPipeline guardrailPipeline;
	private AgentExecutionService service;

	private static AiAgent agent(String name, GuardrailsConfig guardrails) {
		var a = new AiAgent();
		a.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("default").build());
		a.setSpec(new AiAgentSpec(new ProviderRef("p", "ns"), "gpt-4o", "You are helpful.", "Hello",
				List.of(), List.of(), List.of(new ToolDefinition("calc", "Math", true, Map.of())), List.of(), List.of(), guardrails, null,
				null, 1));
		return a;
	}

	private static LlmProvider provider(String name, String defaultModel) {
		var p = new LlmProvider();
		p.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("default").build());
		p.setSpec(
				new LlmProviderSpec("OPENAI", "http://localhost", null, defaultModel, 0.7, 1000, 0.0, 0.0, List.of()));
		return p;
	}

	@BeforeEach
	void setUp() {
		toolRegistry = new ToolRegistry();
		guardrailPipeline = new GuardrailPipeline(List.of(new PiiMaskingFilter(), new PromptInjectionFilter()),
				List.of(new OutputValidationFilter()));
		service = new AgentExecutionService(toolRegistry, Optional.empty(), guardrailPipeline, Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty());
	}

	@Test
	@DisplayName("processAgentPrompt uses fallback answer when chatModel is not configured")
	void processAgentPrompt_Fallback() {
		var a = agent("test-agent", null);
		var response = service.processAgentPrompt(a, null, "What is 2+2?");

		assertFalse(response.isBlocked());
		assertEquals("test-agent", response.agentName());
		assertEquals("gpt-4o", response.model());
		assertTrue(response.answer().contains("Simulated response"));
		assertTrue(response.usage().totalTokens() > 0);
	}

	@Test
	@DisplayName("processAgentPrompt passes through custom query")
	void processAgentPrompt_CustomInput() {
		var a = agent("test-agent", null);
		var response = service.processAgentPrompt(a, null, "Custom user prompt text");

		assertTrue(response.answer().contains("Custom user prompt text"));
	}

	@Test
	@DisplayName("processAgentPrompt respects model override from spec over provider default")
	void processAgentPrompt_ModelSelection() {
		var a = agent("test-agent", null);
		var p = provider("prov", "provider-default-model");
		var response = service.processAgentPrompt(a, p, null);

		assertEquals("gpt-4o", response.model());
	}

	@Test
	@DisplayName("processAgentPrompt falls back to provider defaultModel when spec.model is null")
	void processAgentPrompt_ProviderDefaultModel() {
		var a = new AiAgent();
		a.setMetadata(new ObjectMetaBuilder().withName("a").withNamespace("default").build());
		a.setSpec(new AiAgentSpec(new ProviderRef("p", "ns"), null, "Prompt", "Hello", List.of(), List.of(), List.of(), List.of(),
				List.of(), null, null, null, 1));

		var p = provider("prov", "provider-model");
		var response = service.processAgentPrompt(a, p, null);

		assertEquals("provider-model", response.model());
	}

	@Test
	@DisplayName("processAgentPrompt calls Spring AI ChatModel when available")
	void processAgentPrompt_ViaChatModel() {
		var mockChatModel = mock(ChatModel.class);
		var mockResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Spring AI answer"))));
		when(mockChatModel.call(any(Prompt.class))).thenReturn(mockResponse);

		var svc = new AgentExecutionService(toolRegistry, Optional.of(mockChatModel), guardrailPipeline,
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		var a = agent("my-agent", null);

		var response = svc.processAgentPrompt(a, null, "Hi");

		assertFalse(response.isBlocked());
		assertEquals("Spring AI answer", response.answer());
	}

	@Test
	@DisplayName("executeAgent returns default response for unresolved agentRef")
	void executeAgent_UnresolvedRef() {
		var response = service.executeAgent("unknown-agent", "Perform task", "default");

		assertFalse(response.isBlocked());
		assertEquals("unknown-agent", response.agentName());
		assertTrue(response.answer().contains("Execution completed for: Perform task"));
	}

	@Test
	@DisplayName("executeAgent resolves agent and executes via ChatModel")
	void executeAgent_ResolvedViaResolver() {
		var mockAgentResolver = mock(AgentResolver.class);
		var mockChatModel = mock(ChatModel.class);
		var mockResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Agent answer"))));
		when(mockChatModel.call(any(Prompt.class))).thenReturn(mockResponse);

		var a = agent("my-agent", null);
		when(mockAgentResolver.resolve("my-agent", "default")).thenReturn(Optional.of(a));

		var svc = new AgentExecutionService(toolRegistry, Optional.of(mockChatModel), guardrailPipeline,
				Optional.empty(), Optional.empty(), Optional.of(mockAgentResolver), Optional.empty());

		var response = svc.executeAgent("my-agent", "Task input", "default");

		assertFalse(response.isBlocked());
		assertEquals("my-agent", response.agentName());
		assertEquals("Agent answer", response.answer());
	}

	@Test
	@DisplayName("executeAgent via ModelGateway with primary provider and fallback")
	void executeAgent_ViaModelGateway() {
		var mockAgentResolver = mock(AgentResolver.class);
		var mockModelGateway = mock(ModelGateway.class);
		var mockChatModel = mock(ChatModel.class);

		var a = agent("gw-agent", null);
		when(mockAgentResolver.resolve(eq("gw-agent"), anyString())).thenReturn(Optional.of(a));

		var gatewayResult = new ModelGateway.GatewayCallResult("Gateway Answer", "gpt-4o", 20, 30, 0.001, false);
		when(mockModelGateway.invoke(any(), any(), anyString(), any(), any(), eq("gw-agent")))
				.thenReturn(gatewayResult);

		var svc = new AgentExecutionService(toolRegistry, Optional.of(mockChatModel), guardrailPipeline,
				Optional.of(mockModelGateway), Optional.empty(), Optional.of(mockAgentResolver), Optional.empty());

		var response = svc.executeAgent("gw-agent", "Gateway query", "ns1");

		assertFalse(response.isBlocked());
		assertEquals("Gateway Answer", response.answer());
		assertEquals(20, response.usage().inputTokens());
		assertEquals(30, response.usage().outputTokens());
	}

	@Test
	@DisplayName("executeAgent falls back to simulation if ModelGateway throws exception")
	void executeAgent_GatewayException_Fallback() {
		var mockAgentResolver = mock(AgentResolver.class);
		var mockModelGateway = mock(ModelGateway.class);
		var mockChatModel = mock(ChatModel.class);

		var a = agent("gw-fail-agent", null);
		when(mockAgentResolver.resolve(eq("gw-fail-agent"), anyString())).thenReturn(Optional.of(a));
		when(mockModelGateway.invoke(any(), any(), anyString(), any(), any(), anyString()))
				.thenThrow(new ModelGateway.ModelGatewayException("All routes failed"));

		var svc = new AgentExecutionService(toolRegistry, Optional.of(mockChatModel), guardrailPipeline,
				Optional.of(mockModelGateway), Optional.empty(), Optional.of(mockAgentResolver), Optional.empty());

		var response = svc.executeAgent("gw-fail-agent", "Fail query", "ns1");

		assertFalse(response.isBlocked());
		assertTrue(response.answer().contains("Simulated response"));
	}

	@Test
	@DisplayName("processAgentPrompt integrates RagService prompt context")
	void processAgentPrompt_WithRagService() {
		var mockRagService = mock(RagService.class);
		when(mockRagService.retrieveAsPrompt("What is RAG?", 3))
				.thenReturn("\n\nContext from Knowledge Base:\n- RAG improves LLM context");

		var svc = new AgentExecutionService(toolRegistry, Optional.empty(), guardrailPipeline, Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.of(mockRagService));

		var a = agent("rag-agent", null);
		var response = svc.processAgentPrompt(a, null, "What is RAG?");

		assertTrue(response.systemPrompt().contains("RAG improves LLM context"));
	}
}
