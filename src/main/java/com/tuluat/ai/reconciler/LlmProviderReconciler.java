package com.tuluat.ai.reconciler;

import com.tuluat.ai.crd.provider.LlmProvider;
import com.tuluat.ai.crd.provider.LlmProviderStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JOSDK Reconciler for managing LlmProvider Custom Resources.
 */
@Component
@ControllerConfiguration(name = "llmprovider-reconciler")
public class LlmProviderReconciler implements Reconciler<LlmProvider> {
    private static final Logger log = LoggerFactory.getLogger(LlmProviderReconciler.class);

    private final KubernetesClient client;

    @Autowired
    public LlmProviderReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<LlmProvider> reconcile(LlmProvider resource, Context<LlmProvider> context) {
        String name = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();
        log.info("Reconciling LlmProvider resource: {}/{}", ns, name);

        try {
            var spec = resource.getSpec();
            if (spec == null) {
                resource.setStatus(LlmProviderStatus.error("Spec cannot be null", resource.getMetadata().getGeneration()));
                return UpdateControl.patchStatus(resource);
            }

            // Validate API Key secret if specified
            if (spec.apiKeySecretRef() != null) {
                String secretName = spec.apiKeySecretRef().name();
                var secret = client.secrets().inNamespace(ns).withName(secretName).get();
                if (secret == null) {
                    log.warn("Referenced secret '{}' not found in namespace '{}'", secretName, ns);
                    resource.setStatus(LlmProviderStatus.pending(
                        "Waiting for Secret '" + secretName + "' to be created",
                        resource.getMetadata().getGeneration()
                    ));
                    return UpdateControl.patchStatus(resource);
                }
            }

            String msg = String.format("LLM Provider [%s] is Ready (type: %s, defaultModel: %s)",
                name, spec.providerType(), spec.defaultModel());
            resource.setStatus(LlmProviderStatus.ready(msg, resource.getMetadata().getGeneration()));
            log.info("LlmProvider successfully reconciled: {}", msg);

            return UpdateControl.patchStatus(resource);
        } catch (Exception e) {
            log.error("Error reconciling LlmProvider {}", name, e);
            resource.setStatus(LlmProviderStatus.error("Reconciliation error: " + e.getMessage(), resource.getMetadata().getGeneration()));
            return UpdateControl.patchStatus(resource);
        }
    }
}
