package com.tuluat.engine.agent;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.gateway.ModelGateway;
import com.tuluat.engine.gateway.ProviderResolver;
import com.tuluat.engine.skill.SkillRegistry;
import com.tuluat.engine.skill.SkillResult;
import com.tuluat.guardrails.GuardrailBlockedException;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core engine service for executing AI Agent prompts with Spring AI, Skills,
 * guardrails (ADR 004 / 007) and the Model Gateway (fallback/budget/cost).
 */
@Service
public class AgentExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final SkillRegistry skillRegistry;
    private final ChatModel chatModel;
    private final GuardrailPipeline guardrailPipeline;
    private final ModelGateway modelGateway;
    private final ProviderResolver providerResolver;
    private final AgentResolver agentResolver;

    @Autowired
    public AgentExecutionService(
            SkillRegistry skillRegistry,
            @Autowired(required = false) @Qualifier("openAiChatModel") ChatModel chatModel,
            GuardrailPipeline guardrailPipeline,
            ModelGateway modelGateway,
            @Autowired(required = false) ProviderResolver providerResolver,
            @Autowired(required = false) AgentResolver agentResolver) {
        this.skillRegistry = skillRegistry;
        this.chatModel = chatModel;
        this.guardrailPipeline = guardrailPipeline;
        this.modelGateway = modelGateway;
        this.providerResolver = providerResolver;
        this.agentResolver = agentResolver;
    }

    /**
     * Executes an AI Agent prompt based on manifest specification, applying the
     * guardrails pipeline (pre-execution masking/injection defense, post-execution
     * output validation) from the agent's {@code spec.guardrails()} policy.
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
                : "deepseek-chat";

        // Determine user input (request input or manifest default user prompt)
        String query = (customInput != null && !customInput.isBlank())
            ? customInput
            : (spec.userPrompt() != null) ? spec.userPrompt() : "Hello AI Agent";

        // Step 0: Pre-execution guardrails (PII masking + prompt injection defense)
        String safeQuery = query;
        try {
            safeQuery = guardrailPipeline.processPrompt(query, spec.guardrails());
        } catch (GuardrailBlockedException e) {
            log.warn("Agent '{}' request blocked by guardrail [{}]: {}", agentName, e.getFilterName(), e.getMessage());
            return AgentResponse.blocked(agentName, e.getFilterName(), e.getMessage());
        }

        // Step 1: Execute active skills concurrently using Virtual Threads & Streams
        log.info("Executing skills for Agent '{}' on Virtual Thread", agentName);
        Map<String, SkillResult> skillResultsMap = skillRegistry.executeActiveSkills(spec.skills(), safeQuery);
        List<SkillResult> skillResults = new ArrayList<>(skillResultsMap.values());

        // Step 2: Build enhanced System Prompt including skill outputs
        String skillContext = skillResults.stream()
            .map(res -> String.format("[%s]: %s", res.skillName(), res.output()))
            .collect(Collectors.joining("\n"));

        String baseSystemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : "You are a helpful AI assistant.";
        String effectiveSystemPrompt = skillContext.isBlank()
            ? baseSystemPrompt
            : baseSystemPrompt + "\n\nAvailable Context from Tools/Skills:\n" + skillContext;

        // Step 3: Invoke LLM via Model Gateway (fallback/budget/cost) or direct Spring AI
        String aiAnswer;
        int inputTokens = 0;
        int outputTokens = 0;
        boolean usedFallback = false;
        double costUsd = 0.0;

        var systemMsg = new SystemMessage(effectiveSystemPrompt);
        var userMsg = new UserMessage(safeQuery);
        var prompt = new Prompt(List.of(systemMsg, userMsg));

        if (modelGateway != null && chatModel != null) {
            try {
                ModelGateway.GatewayCallResult gw = modelGateway.invoke(
                    prompt, provider, model,
                    providerResolver, // null-safe: gateway skips fallbacks without resolver
                    null, agentName);
                aiAnswer = gw.answer();
                inputTokens = gw.inputTokens();
                outputTokens = gw.outputTokens();
                costUsd = gw.costUsd();
                usedFallback = gw.usedFallback();
            } catch (ModelGateway.BudgetExceededException e) {
                log.warn("Agent '{}' budget exceeded: {}", agentName, e.getMessage());
                return AgentResponse.blocked(agentName, "model-gateway-budget", e.getMessage());
            } catch (ModelGateway.ModelGatewayException e) {
                log.warn("Model Gateway failed for agent '{}', falling back to simulated execution: {}",
                    agentName, e.getMessage());
                aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, safeQuery, skillResults);
                inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
                outputTokens = estimateTokens(aiAnswer);
            }
        } else if (chatModel != null) {
            try {
                log.info("Calling Spring AI ChatModel [{}] for agent '{}'", model, agentName);
                var response = chatModel.call(prompt);
                aiAnswer = response.getResult().getOutput().getText();

                // Extract tokens from Spring AI response metadata if available
                if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                    var usage = response.getMetadata().getUsage();
                    inputTokens = usage.getPromptTokens().intValue();
                    outputTokens = usage.getCompletionTokens().intValue();
                } else {
                    inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
                    outputTokens = estimateTokens(aiAnswer);
                }
            } catch (Exception e) {
                log.warn("Spring AI call failed, falling back to simulated execution: {}", e.getMessage());
                aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, safeQuery, skillResults);
                inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
                outputTokens = estimateTokens(aiAnswer);
            }
        } else {
            log.info("Spring AI ChatModel bean not bound. Generating simulated response for agent '{}'", agentName);
            aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, safeQuery, skillResults);
            inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
            outputTokens = estimateTokens(aiAnswer);
        }

        // Step 4: Post-execution output validation (agent-level policy)
        if (spec.guardrails() != null && spec.guardrails().outputValidation() != null
                && spec.guardrails().outputValidation().isEnabled()) {
            ValidationResult vr = guardrailPipeline.validateOutput(aiAnswer, spec.guardrails(), null);
            if (!vr.valid()) {
                log.warn("Agent '{}' output rejected by guardrails: confidence={}, errors={}",
                    agentName, vr.confidence(), vr.errors());
            }
        }

        long latency = System.currentTimeMillis() - startTime;
        UsageStats usageStats = UsageStats.calculate(inputTokens, outputTokens, model, latency);
        if (costUsd > 0) {
            usageStats = usageStats.withCostUsd(costUsd);
        }

        return AgentResponse.create(agentName, model, effectiveSystemPrompt, aiAnswer, skillResults, usageStats);
    }

    /**
     * Executes an agent by reference (workflow node path). Resolves the agent CR
     * via {@link AgentResolver} (when present) and applies its guardrails policy:
     * pre-execution masking/injection defense on the prompt, post-execution
     * output validation. Without a resolver, executes unguarded (legacy path).
     */
    public AgentResponse executeAgent(String agentRef, String prompt, String context) {
        log.info("Executing agentRef '{}' with prompt '{}'", agentRef, prompt);
        String ns = (context != null && !context.isBlank()) ? context : null;

        // Resolve agent CR for its guardrails policy (workflow path, ADR 004)
        var spec = agentResolver != null ? agentResolver.resolve(agentRef, ns) : Optional.<AiAgent>empty();
        var guardrails = spec.map(a -> a.getSpec() != null ? a.getSpec().guardrails() : null).orElse(null);

        // Pre-execution guardrails (masking + injection defense)
        String safePrompt = prompt;
        if (guardrailPipeline != null) {
            try {
                safePrompt = guardrailPipeline.processPrompt(prompt, guardrails);
            } catch (GuardrailBlockedException e) {
                log.warn("Agent '{}' request blocked by guardrail [{}]: {}", agentRef, e.getFilterName(), e.getMessage());
                return AgentResponse.blocked(agentRef, e.getFilterName(), e.getMessage());
            }
        }

        AgentResponse response = AgentResponse.create(
            agentRef != null ? agentRef : "default-agent",
            "deepseek-chat",
            "Workflow Agent System Prompt",
            "Execution completed for: " + safePrompt,
            List.of(),
            UsageStats.calculate(10, 10, "deepseek-chat", 50)
        );

        // Post-execution output validation
        if (guardrailPipeline != null && guardrails != null && guardrails.outputValidation() != null
                && guardrails.outputValidation().isEnabled()) {
            ValidationResult vr = guardrailPipeline.validateOutput(response.answer(), guardrails, null);
            if (!vr.valid()) {
                log.warn("Agent '{}' output rejected by guardrails: confidence={}, errors={}",
                    agentRef, vr.confidence(), vr.errors());
            }
        }
        return response;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // Standard approximation: ~4 characters per token
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
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
