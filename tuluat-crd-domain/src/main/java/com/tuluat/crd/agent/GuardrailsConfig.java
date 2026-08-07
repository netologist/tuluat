package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Guardrails policy block for an AiAgent (optional; platform defaults apply when absent).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailsConfig(
    @JsonProperty("piiMasking") PiiMaskingConfig piiMasking,
    @JsonProperty("promptInjection") PromptInjectionConfig promptInjection,
    @JsonProperty("outputValidation") OutputValidationConfig outputValidation
) {
}
