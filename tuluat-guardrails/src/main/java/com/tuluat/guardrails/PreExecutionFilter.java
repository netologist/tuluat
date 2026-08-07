package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;

/**
 * Pre-execution guardrail: applied to the prompt before it reaches the LLM provider.
 */
public interface PreExecutionFilter extends GuardrailFilter {

    /**
     * @return whether this filter is active under the given config
     */
    boolean isEnabled(GuardrailsConfig config);

    /**
     * Process (mask, sanitize, or block) the outgoing prompt.
     *
     * @param prompt the assembled prompt
     * @param config the agent's guardrails policy
     * @return result carrying the (possibly transformed) content and allow/block decision
     */
    FilterResult apply(String prompt, GuardrailsConfig config);
}
