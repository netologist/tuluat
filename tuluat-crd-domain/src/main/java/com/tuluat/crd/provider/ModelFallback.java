package com.tuluat.crd.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ordered fallback model definition for the Model Gateway. Referenced from
 * {@link LlmProviderSpec#fallbacks()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelFallback(@JsonProperty("providerName") String providerName,
		@JsonProperty("namespace") String namespace, @JsonProperty("model") String model) {
}
