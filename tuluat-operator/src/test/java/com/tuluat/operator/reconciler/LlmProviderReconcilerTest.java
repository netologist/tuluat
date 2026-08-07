package com.tuluat.operator.reconciler;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.crd.provider.SecretKeyRef;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmProviderReconcilerTest {

    private KubernetesClient client;
    private LlmProviderReconciler reconciler;
    private MixedOperation secretsMock;
    private NonNamespaceOperation secretsInNsMock;
    private Resource secretResourceMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(KubernetesClient.class);
        reconciler = new LlmProviderReconciler(client);

        secretsMock = mock(MixedOperation.class);
        secretsInNsMock = mock(NonNamespaceOperation.class);
        secretResourceMock = mock(Resource.class);

        doReturn(secretsMock).when(client).secrets();
        doReturn(secretsInNsMock).when(secretsMock).inNamespace(anyString());
        doReturn(secretResourceMock).when(secretsInNsMock).withName(anyString());
    }

    @Test
    @DisplayName("Should update status to Ready when referenced Secret exists")
    void testReconcileSuccess() {
        Secret secret = new SecretBuilder()
            .withNewMetadata().withName("openai-secret").withNamespace("default").endMetadata()
            .withStringData(Map.of("api-key", "secret-val"))
            .build();
        doReturn(secret).when(secretResourceMock).get();

        var provider = new LlmProvider();
        provider.setMetadata(new ObjectMetaBuilder().withName("openai-provider").withNamespace("default").withGeneration(1L).build());
        provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", new SecretKeyRef("openai-secret", "api-key"), "gpt-4o", 0.7, 2048, 0.0, 0.0, List.of()));

        UpdateControl<LlmProvider> control = reconciler.reconcile(provider, null);

        assertNotNull(control);
        assertNotNull(provider.getStatus());
        assertEquals("Ready", provider.getStatus().phase());
        assertTrue(provider.getStatus().message().contains("OPENAI"));
    }

    @Test
    @DisplayName("Should update status to Pending when referenced Secret is missing")
    void testReconcileMissingSecret() {
        doReturn(null).when(secretResourceMock).get();

        var provider = new LlmProvider();
        provider.setMetadata(new ObjectMetaBuilder().withName("missing-sec-provider").withNamespace("default").withGeneration(1L).build());
        provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", new SecretKeyRef("non-existent-secret", "api-key"), "gpt-4o", 0.7, 2048, 0.0, 0.0, List.of()));

        UpdateControl<LlmProvider> control = reconciler.reconcile(provider, null);

        assertNotNull(control);
        assertEquals("Pending", provider.getStatus().phase());
        assertTrue(provider.getStatus().message().contains("Waiting for Secret"));
    }
}
