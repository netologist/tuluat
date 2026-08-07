package com.tuluat.app.config;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.gateway.ProviderResolver;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves {@link LlmProvider} resources via the Kubernetes API for Model
 * Gateway fallback routing.
 */
@Component
public class KubernetesProviderResolver implements ProviderResolver {

    private final KubernetesClient client;

    public KubernetesProviderResolver(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Optional<LlmProvider> resolve(String providerName, String namespace) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
        LlmProvider provider = client.resources(LlmProvider.class)
            .inNamespace(ns)
            .withName(providerName)
            .get();
        return Optional.ofNullable(provider);
    }
}
