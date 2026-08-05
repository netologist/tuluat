package com.tuluat.ai.controller;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record representing an incoming HTTP chat request.
 */
public record ChatRequest(
    @JsonProperty("prompt") String prompt,
    @JsonProperty("namespace") String namespace
) {
    public ChatRequest {
        if (namespace == null || namespace.isBlank()) {
            namespace = "default";
        }
    }
}
