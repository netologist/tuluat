package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.PromptInjectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Pre-execution prompt injection defense. Detects common injection patterns
 * (instruction overrides, system prompt extraction, jailbreak phrases) and
 * either blocks the request (BLOCK) or neutralizes the offending tokens
 * (SANITIZE).
 */
@Component
public class PromptInjectionFilter implements PreExecutionFilter {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionFilter.class);

    public static final String NAME = "prompt-injection";

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts|messages)"),
        Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|prior)\\s+instructions"),
        Pattern.compile("(?i)reveal\\s+(your\\s+)?(system\\s+)?prompt"),
        Pattern.compile("(?i)you\\s+are\\s+now\\s+(dan|jailbroken|unrestricted|god)"),
        Pattern.compile("(?i)system\\s*:.*(override|bypass)"),
        Pattern.compile("(?i)forget\\s+(everything|all)\\s+(above|previous)"),
        Pattern.compile("(?i)act\\s+as\\s+if\\s+you\\s+have\\s+no\\s+(rules|restrictions|safety)"),
        Pattern.compile("(?i)simulate\\s+being\\s+without\\s+(guardrails|safety)")
    );

    private static final String SANITIZE_TOKEN = "[INJECTION-REDACTED]";

    @Override
    public String getFilterName() {
        return NAME;
    }

    @Override
    public boolean isEnabled(GuardrailsConfig config) {
        return config != null && config.promptInjection() != null && config.promptInjection().isEnabled();
    }

    @Override
    public FilterResult apply(String prompt, GuardrailsConfig config) {
        if (prompt == null || prompt.isBlank()) {
            return FilterResult.allow(prompt, NAME);
        }
        PromptInjectionConfig policy = config.promptInjection();
        String strategy = policy.strategy() == null ? "BLOCK" : policy.strategy().toUpperCase();

        boolean detected = false;
        String sanitized = prompt;
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(sanitized).find()) {
                detected = true;
                if (strategy.equals("SANITIZE")) {
                    sanitized = p.matcher(sanitized).replaceAll(SANITIZE_TOKEN);
                } else {
                    break; // BLOCK: first hit is enough
                }
            }
        }

        if (!detected) {
            return FilterResult.allow(prompt, NAME);
        }
        if (strategy.equals("SANITIZE")) {
            log.warn("PromptInjectionFilter sanitized injection patterns");
            return FilterResult.allow(sanitized, NAME);
        }
        log.warn("PromptInjectionFilter blocked prompt containing injection patterns");
        return FilterResult.block(sanitized, "Prompt injection pattern detected", NAME);
    }
}
