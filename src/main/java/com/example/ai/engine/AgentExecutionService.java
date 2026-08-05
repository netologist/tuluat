package com.example.ai.engine;

import com.example.ai.crd.agent.AiAgent;
import com.example.ai.crd.provider.LlmProvider;
import com.example.ai.engine.skill.SkillRegistry;
import com.example.ai.engine.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core engine service for executing AI Agent prompts with Spring AI and Skills.
 */
@Service
public class AgentExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final SkillRegistry skillRegistry;
    private final ChatModel chatModel; // Nullable if running without live API key

    @Autowired
    public AgentExecutionService(SkillRegistry skillRegistry, @Autowired(required = false) ChatModel chatModel) {
        this.skillRegistry = skillRegistry;
        this.chatModel = chatModel;
    }

    /**
     * Executes an AI Agent prompt based on manifest specification.
     */
    public AgentResponse processAgentPrompt(AiAgent agent, LlmProvider provider, String customInput) {
        long startTime = System.currentTimeMillis();
        var spec = agent.getSpec();
        String agentName = agent.getMetadata().getName();

        // Determine effective model (agent spec model > provider spec model > default)
        String model = (spec.model() != null && !spec.model().isBlank())
            ? spec.model()
            : (provider != null && provider.getSpec() != null && provider.getSpec().defaultModel() != null)
                ? provider.getSpec().defaultModel()
                : "gpt-4o";

        // Determine user input (request input or manifest default user prompt)
        String query = (customInput != null && !customInput.isBlank())
            ? customInput
            : (spec.userPrompt() != null) ? spec.userPrompt() : "Hello AI Agent";

        // Step 1: Execute active skills concurrently using Virtual Threads & Streams
        log.info("Executing skills for Agent '{}' on Virtual Thread", agentName);
        Map<String, SkillResult> skillResultsMap = skillRegistry.executeActiveSkills(spec.skills(), query);
        List<SkillResult> skillResults = new ArrayList<>(skillResultsMap.values());

        // Step 2: Build enhanced System Prompt including skill outputs
        String skillContext = skillResults.stream()
            .map(res -> String.format("[%s]: %s", res.skillName(), res.output()))
            .collect(Collectors.joining("\n"));

        String baseSystemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : "You are a helpful AI assistant.";
        String effectiveSystemPrompt = skillContext.isBlank()
            ? baseSystemPrompt
            : baseSystemPrompt + "\n\nAvailable Context from Tools/Skills:\n" + skillContext;

        // Step 3: Invoke LLM via Spring AI (or simulated engine if no Spring AI model bean present)
        String aiAnswer;
        if (chatModel != null) {
            try {
                log.info("Calling Spring AI ChatModel [{}] for agent '{}'", model, agentName);
                var systemMsg = new SystemMessage(effectiveSystemPrompt);
                var userMsg = new UserMessage(query);
                var prompt = new Prompt(List.of(systemMsg, userMsg));
                aiAnswer = chatModel.call(prompt).getResult().getOutput().getText();
            } catch (Exception e) {
                log.warn("Spring AI call failed, falling back to simulated execution: {}", e.getMessage());
                aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, query, skillResults);
            }
        } else {
            log.info("Spring AI ChatModel bean not bound. Generating simulated response for agent '{}'", agentName);
            aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, query, skillResults);
        }

        long latency = System.currentTimeMillis() - startTime;
        return AgentResponse.create(agentName, model, effectiveSystemPrompt, aiAnswer, skillResults, latency);
    }

    private String generateSimulatedResponse(String agentName, String model, String systemPrompt, String query, List<SkillResult> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Hello! I am AI Agent [%s] running model [%s].\n", agentName, model));
        sb.append(String.format("I processed your input: \"%s\"\n\n", query));
        if (!skills.isEmpty()) {
            sb.append("Skills Executed:\n");
            skills.forEach(s -> sb.append(String.format("- Skill '%s' (success=%b): %s\n", s.skillName(), s.success(), s.output())));
            sb.append("\n");
        }
        sb.append("System Prompt Applied:\n").append(systemPrompt);
        return sb.toString();
    }
}
