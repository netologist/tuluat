package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;

/**
 * Post-execution guardrail: applied to the LLM output before it is stored or
 * passed to a downstream workflow node.
 */
public interface PostExecutionFilter extends GuardrailFilter {

    /**
     * Validate the model output.
     *
     * @param output       raw model output
     * @param config       the agent's guardrails policy
     * @param outputSchema JSON Schema (as string) the output must satisfy, or null to skip schema validation
     * @return validation result
     */
    ValidationResult validate(String output, GuardrailsConfig config, String outputSchema);
}
