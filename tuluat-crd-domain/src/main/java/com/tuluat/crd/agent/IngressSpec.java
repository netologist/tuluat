package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Spec for exposing the AiAgent publicly via Kubernetes Ingress.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngressSpec(
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("host") String host,
    @JsonProperty("path") String path,
    @JsonProperty("pathType") String pathType,
    @JsonProperty("ingressClassName") String ingressClassName,
    @JsonProperty("annotations") Map<String, String> annotations,
    @JsonProperty("tls") TlsSpec tls
) {
    public IngressSpec {
        if (enabled == null) {
            enabled = true;
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }
        if (pathType == null || pathType.isBlank()) {
            pathType = "Prefix";
        }
        if (annotations == null) {
            annotations = Map.of();
        }
    }
}
