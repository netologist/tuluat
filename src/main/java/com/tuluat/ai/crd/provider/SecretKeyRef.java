package com.tuluat.ai.crd.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record representing a Kubernetes Secret key reference.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecretKeyRef(
    @JsonProperty("name") String name,
    @JsonProperty("key") String key
) {
    public SecretKeyRef {
        if (key == null || key.isBlank()) {
            key = "api-key";
        }
    }
}
