package com.tuluat.ai.engine;

import com.tuluat.ai.crd.agent.AiAgent;
import com.tuluat.ai.crd.agent.AiAgentSpec;
import com.tuluat.ai.crd.agent.ProviderRef;
import com.tuluat.ai.crd.agent.SkillDefinition;
import com.tuluat.ai.crd.provider.LlmProvider;
import com.tuluat.ai.crd.provider.LlmProviderSpec;
import com.tuluat.ai.engine.skill.SkillRegistry;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentExecutionServiceTest {

    private SkillRegistry skillRegistry;
    private AgentExecutionService agentExecutionService;

    @BeforeEach
    void setUp() {
        skillRegistry = new SkillRegistry();
        agentExecutionService = new AgentExecutionService(skillRegistry, null);
    }

    @Test
    @DisplayName("Should process agent prompt with model override and active skills")
    void testProcessAgentPrompt() {
        // Build AiAgent CR
        var agent = new AiAgent();
        agent.setMetadata(new ObjectMetaBuilder().withName("test-agent").withNamespace("default").build());
        agent.setSpec(new AiAgentSpec(
            new ProviderRef("openai-provider", "default"),
            "gpt-4o",
            "You are a helpful test assistant.",
            "Default user query",
            List.of(new SkillDefinition("calculator", "Math", true, Map.of())),
            null,
            1
        ));

        // Build LlmProvider CR
        var provider = new LlmProvider();
        provider.setMetadata(new ObjectMetaBuilder().withName("openai-provider").withNamespace("default").build());
        provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", null, "gpt-3.5-turbo", 0.7, 2048));

        AgentResponse response = agentExecutionService.processAgentPrompt(agent, provider, "Calculate 50 + 50");

        assertNotNull(response);
        assertEquals("test-agent", response.agentName());
        assertEquals("gpt-4o", response.model()); // Spec override used over provider default
        assertTrue(response.systemPrompt().contains("You are a helpful test assistant."));
        assertEquals(1, response.executedSkills().size());
        assertEquals("calculator", response.executedSkills().get(0).skillName());
        assertTrue(response.answer().contains("Hello! I am AI Agent [test-agent]"));
    }
}
