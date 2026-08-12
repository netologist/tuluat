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

		executionService = new AgentExecutionService(toolRegistry, Optional.of(chatModel), guardrailPipeline,
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.of(sessionMemoryManager), Optional.empty());

		when(chatModel.call(any(Prompt.class)))
				.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response with memory")))));
	}

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

		AgentResponse response = executionService.processAgentPrompt(defaultAgent("mem"), defaultProvider(),
				"Tell me the margin too", sessionId);

		assertNotNull(response);
		assertFalse(response.isBlocked());
		assertTrue(response.systemPrompt().contains("Conversation History"));
	}

	@Test
	void savesResponseToSessionMemory() {
		UUID sessionId = UUID.randomUUID();
		when(sessionMemoryManager.getShortMemory(sessionId)).thenReturn(List.of());
		executionService.processAgentPrompt(defaultAgent("save"), defaultProvider(), "Hello", sessionId);
		verify(sessionMemoryManager).saveShortMemory(eq(sessionId), eq("save"), eq("assistant"),
				eq("Response with memory"));
	}

	@Test
	void noHistoryInjectionWhenSessionIdIsNull() {
		AgentResponse response = executionService.processAgentPrompt(defaultAgent("ns"), defaultProvider(), "Hello",
				null);
		assertFalse(response.systemPrompt().contains("Conversation History"));
	}

	@Test
	void noMemorySaveWhenSessionIdIsNull() {
		executionService.processAgentPrompt(defaultAgent("ns2"), defaultProvider(), "Hello", null);
		verify(sessionMemoryManager, never()).saveShortMemory(any(), anyString(), anyString(), anyString());
	}

	@Test
	void gracefulDegradationWithoutMemoryManager() {
		var svc = new AgentExecutionService(toolRegistry, Optional.of(chatModel), guardrailPipeline, Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		AgentResponse response = svc.processAgentPrompt(defaultAgent("no-mem"), defaultProvider(), "Hello",
				UUID.randomUUID());
		assertEquals("Response with memory", response.answer());
	}

	private AiAgent defaultAgent(String name) {
		AiAgent agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("default").build());
		agent.setSpec(new AiAgentSpec(new ProviderRef("provider", "default"), null, "You are test", null, List.of(),
				List.of(), List.of(new ToolDefinition("weather", "Weather tool", true, Map.of())), List.of(), List.of(),
				new GuardrailsConfig(null, null, null), null, null, 1));
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