package com.tuluat.engine.agent;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.agent.SkillDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.engine.skill.SkillRegistry;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.OutputValidationFilter;
import com.tuluat.guardrails.PiiMaskingFilter;
import com.tuluat.guardrails.PromptInjectionFilter;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentExecutionServiceTest {

	private SkillRegistry skillRegistry;
	private AgentExecutionService agentExecutionService;

	@BeforeEach
	void setUp() {
		skillRegistry = new SkillRegistry();
		agentExecutionService = new AgentExecutionService(skillRegistry,
				Optional.empty(),
				new GuardrailPipeline(List.of(new PiiMaskingFilter(), new PromptInjectionFilter()),
						List.of(new OutputValidationFilter())),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
	}

	@Test
	@DisplayName("Should process agent prompt with model override, active skills, and token usage stats")
	void testProcessAgentPrompt() {
		var agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName("test-agent").withNamespace("default").build());
		agent.setSpec(new AiAgentSpec(new ProviderRef("openai-provider", "default"), "gpt-4o",
				"You are a helpful test assistant.", "Default user query",
				List.of(new SkillDefinition("calculator", "Math", true, Map.of())), List.of(),
				List.of(), null, null, null, 1));

		var provider = new LlmProvider();
		provider.setMetadata(new ObjectMetaBuilder().withName("openai-provider").withNamespace("default").build());
		provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", null, "gpt-3.5-turbo", 0.7, 2048,
				0.0, 0.0, List.of()));

		AgentResponse response = agentExecutionService.processAgentPrompt(agent, provider, "Calculate 50 + 50");

		assertNotNull(response);
		assertEquals("test-agent", response.agentName());
		assertEquals("gpt-4o", response.model());
		assertTrue(response.systemPrompt().contains("You are a helpful test assistant."));
		assertEquals(1, response.executedSkills().size());
		assertEquals("calculator", response.executedSkills().get(0).skillName());
		assertTrue(response.answer().contains("[Simulated response from agent 'test-agent'"));

		assertNotNull(response.usage());
		assertTrue(response.usage().outputTokens() > 0);
		assertEquals(response.usage().inputTokens() + response.usage().outputTokens(), response.usage().totalTokens());
		assertTrue(response.usage().estimatedCostUsd() >= 0.0);
		assertFalse(response.isBlocked());
	}
}
