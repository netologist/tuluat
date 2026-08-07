package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reference to an McpServer resource (mirrors {@link ProviderRef}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerRef(
    @JsonProperty("name") String name,
    @JsonProperty("namespace") String namespace
) {
}
