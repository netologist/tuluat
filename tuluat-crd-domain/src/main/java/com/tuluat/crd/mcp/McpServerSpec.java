package com.tuluat.crd.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tuluat.crd.provider.SecretKeyRef;

/**
 * Spec record for McpServer Custom Resource.
 *
 * <p>Declares an external Model Context Protocol server (PostgreSQL, GitHub, Slack, ...)
 * that agents may reference via {@code spec.mcpServers} on AiAgent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerSpec(
    @JsonProperty("endpoint") String endpoint,
    @JsonProperty("transport") String transport,           // SSE, STDIO, HTTP
    @JsonProperty("authType") String authType,             // NONE, API_KEY, OAUTH2
    @JsonProperty("apiKeySecretRef") SecretKeyRef apiKeySecretRef,
    @JsonProperty("timeoutSeconds") Integer timeoutSeconds,
    @JsonProperty("description") String description
) {
    public McpServerSpec {
        if (transport == null || transport.isBlank()) {
            transport = "SSE";
        }
        if (authType == null || authType.isBlank()) {
            authType = "NONE";
        }
        if (timeoutSeconds == null) {
            timeoutSeconds = 30;
        }
    }
}
