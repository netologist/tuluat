package com.tuluat.crd.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status record for McpServer Custom Resource.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerStatus(
    @JsonProperty("phase") String phase,             // Ready, Error, Pending
    @JsonProperty("message") String message,
    @JsonProperty("observedGeneration") Long observedGeneration,
    @JsonProperty("lastUpdated") String lastUpdated
) {
    public static McpServerStatus ready(String message, Long gen) {
        return new McpServerStatus("Ready", message, gen, java.time.Instant.now().toString());
    }

    public static McpServerStatus error(String message, Long gen) {
        return new McpServerStatus("Error", message, gen, java.time.Instant.now().toString());
    }

    public static McpServerStatus pending(String message, Long gen) {
        return new McpServerStatus("Pending", message, gen, java.time.Instant.now().toString());
    }
}
