package com.tuluat.engine.gateway;

import com.tuluat.crd.provider.LlmProvider;

import java.util.Optional;

/**
 * Resolves an {@link LlmProvider} by name for Model Gateway fallback routing.
 * Implementations typically look up the provider via the Kubernetes API.
 */
public interface ProviderResolver {

	Optional<LlmProvider> resolve(String providerName, String namespace);
}
