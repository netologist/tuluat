package com.tuluat.engine.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.engine.entity.SessionShortMemoryEntity;
import com.tuluat.engine.memory.SessionMemoryManager;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.OutputValidationFilter;
import com.tuluat.guardrails.PiiMaskingFilter;
import com.tuluat.guardrails.PromptInjectionFilter;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Tests for session memory wiring in AgentExecutionService (ADR 013).
 */
class AgentSessionMemoryTest {

	private ToolRegistry toolRegistry;
	private GuardrailPipeline guardrailPipeline;
	private ChatModel chatModel;
	private SessionMemoryManager sessionMemoryManager;
	private AgentExecutionService executionService;

	@BeforeEach
	void setUp() {
		toolRegistry = new ToolRegistry();
		guardrailPipeline = new GuardrailPipeline(List.of(new PiiMaskingFilter(), new PromptInjectionFilter()),
				List.of(new OutputValidationFilter()));
		chatModel = mock(ChatModel.class);
		sessionMemoryManager = mock(SessionMemoryManager.class);

		executionService = new AgentExecutionService(toolRegistry, Optional.empty(), Optional.of(chatModel),
				guardrailPipeline, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.of(sessionMemoryManager), Optional.empty());

		when(chatModel.call(any(Prompt.class)))
				.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response with memory")))));
	}

	@DisplayName("Should inject prior conversation history into system prompt when sessionId is provided")
	@Test
	void injectsConversationHistoryIntoSystemPrompt() {
		UUID sessionId = UUID.randomUUID();

		SessionShortMemoryEntity mem1 = new SessionShortMemoryEntity();
		mem1.setSessionId(sessionId);
		mem1.setRole("user");
		mem1.setContent("What is our Q4 revenue?");

		SessionShortMemoryEntity mem2 = new SessionShortMemoryEntity();
		mem2.setSessionId(sessionId);
		mem2.setRole("assistant");
		mem2.setContent("Q4 revenue was $847M.");

		when(sessionMemoryManager.getShortMemory(sessionId)).thenReturn(List.of(mem1, mem2));

		AiAgent agent = defaultAgent("memory-agent");
		LlmProvider provider = defaultProvider();

		AgentResponse response = executionService.processAgentPrompt(agent, provider, "Tell me the margin too",
				sessionId);

		assertNotNull(response);
		assertFalse(response.isBlocked());
		assertTrue(response.systemPrompt().contains("Conversation History"),
				"System prompt should contain conversation history header");
		assertTrue(response.systemPrompt().contains("What is our Q4 revenue?"),
				"System prompt should contain prior user message");
		assertTrue(response.systemPrompt().contains("Q4 revenue was $847M"),
				"System prompt should contain prior assistant message");
	}

	@DisplayName("Should save assistant response to session memory after successful LLM invocation")
	@Test
	void savesResponseToSessionMemory() {
		UUID sessionId = UUID.randomUUID();
		when(sessionMemoryManager.getShortMemory(sessionId)).thenReturn(List.of());

		AiAgent agent = defaultAgent("save-agent");
		LlmProvider provider = defaultProvider();

		executionService.processAgentPrompt(agent, provider, "Hello", sessionId);

		verify(sessionMemoryManager).saveShortMemory(eq(sessionId), eq("save-agent"), eq("assistant"),
				eq("Response with memory"));
	}

	@DisplayName("Should not inject history when sessionId is null")
	@Test
	void noHistoryInjectionWhenSessionIdIsNull() {
		AiAgent agent = defaultAgent("no-session-agent");
		LlmProvider provider = defaultProvider();

		AgentResponse response = executionService.processAgentPrompt(agent, provider, "Hello", null);

		assertNotNull(response);
		assertFalse(response.systemPrompt().contains("Conversation History"),
				"No history should appear when sessionId is null");
	}

	@DisplayName("Should not save memory when sessionId is null")
	@Test
	void noMemorySaveWhenSessionIdIsNull() {
		AiAgent agent = defaultAgent("null-session-agent");
		LlmProvider provider = defaultProvider();

		executionService.processAgentPrompt(agent, provider, "Hello", null);

		verify(sessionMemoryManager, never()).saveShortMemory(any(), anyString(), anyString(), anyString());
	}

	@DisplayName("Should truncate memory to window size to avoid context overflow")
	@Test
	void truncatesMemoryToWindowSize() {
		UUID sessionId = UUID.randomUUID();

		java.util.ArrayList<SessionShortMemoryEntity> memory = new java.util.ArrayList<>();
		for (int i = 0; i < 15; i++) {
			SessionShortMemoryEntity m = new SessionShortMemoryEntity();
			m.setSessionId(sessionId);
			m.setRole(i % 2 == 0 ? "user" : "assistant");
			m.setContent("Message " + i);
			memory.add(m);
		}
		when(sessionMemoryManager.getShortMemory(sessionId)).thenReturn(memory);

		AiAgent agent = defaultAgent("overflow-agent");
		LlmProvider provider = defaultProvider();

		AgentResponse response = executionService.processAgentPrompt(agent, provider, "Latest query", sessionId);

		assertNotNull(response);
		assertFalse(response.systemPrompt().contains("Message 0"), "Messages beyond window size should not appear");
		assertFalse(response.systemPrompt().contains("Message 4"), "Messages beyond window size should not appear");
		assertTrue(response.systemPrompt().contains("Message 5"), "Messages within window should appear");
	}

	@DisplayName("Should continue normally when SessionMemoryManager is not available")
	@Test
	void gracefulDegradationWithoutMemoryManager() {
		var service = new AgentExecutionService(toolRegistry, Optional.empty(), Optional.of(chatModel),
				guardrailPipeline, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty());

		AiAgent agent = defaultAgent("no-memory-agent");
		LlmProvider provider = defaultProvider();

		AgentResponse response = service.processAgentPrompt(agent, provider, "Hello", UUID.randomUUID());

		assertNotNull(response);
		assertEquals("Response with memory", response.answer());
	}

	private AiAgent defaultAgent(String name) {
		AiAgent agent = new AiAgent();
		var meta = new ObjectMetaBuilder().withName(name).withNamespace("default").build();
		agent.setMetadata(meta);
		agent.setSpec(new AiAgentSpec(new ProviderRef("provider", "default"), null, "You are test", null, List.of(),
				List.of(), List.of(new ToolDefinition("weather", "Weather tool", true, Map.of())), List.of(), List.of(),
				new GuardrailsConfig(null, null, null), null, null, 1));
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