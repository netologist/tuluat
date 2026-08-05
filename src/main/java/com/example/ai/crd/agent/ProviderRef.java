package com.example.ai.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reference to an LlmProvider resource.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderRef(
    @JsonProperty("name") String name,
    @JsonProperty("namespace") String namespace
) {
}
