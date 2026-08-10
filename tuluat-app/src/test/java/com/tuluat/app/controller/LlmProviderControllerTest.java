package com.tuluat.app.controller;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LlmProviderControllerTest {

	private KubernetesClient kubernetesClient;
	private LlmProviderController controller;

	private MixedOperation providersMock;
	private NonNamespaceOperation providerNsMock;
	private Resource providerResMock;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		kubernetesClient = mock(KubernetesClient.class);
		providersMock = mock(MixedOperation.class);
		providerNsMock = mock(NonNamespaceOperation.class);
		providerResMock = mock(Resource.class);

		when(kubernetesClient.resources(LlmProvider.class)).thenReturn(providersMock);
		when(providersMock.inNamespace(anyString())).thenReturn(providerNsMock);
		when(providerNsMock.withName(anyString())).thenReturn(providerResMock);

		controller = new LlmProviderController(kubernetesClient);
	}

	@Test
	@DisplayName("Should list LlmProviders from Kubernetes namespace")
	void testListProviders() {
		var provider = new LlmProvider();
		provider.setMetadata(
				new ObjectMetaBuilder().withName("openai-provider").withNamespace("tuluat-system").build());
		provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", null, "gpt-4o", 0.7, 2048, 0.0, 0.0,
				List.of()));

		var listMock = mock(io.fabric8.kubernetes.api.model.KubernetesResourceList.class);
		when(listMock.getItems()).thenReturn(List.of(provider));
		when(providerNsMock.list()).thenReturn(listMock);

		ResponseEntity<List<Map<String, Object>>> response = controller.listProviders("tuluat-system");

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertFalse(response.getBody().isEmpty());
		assertEquals("openai-provider", response.getBody().get(0).get("name"));
	}

	@Test
	@DisplayName("Should return 404 when requested LlmProvider is missing")
	void testGetProviderNotFound() {
		when(providerResMock.get()).thenReturn(null);

		ResponseEntity<Map<String, Object>> response = controller.getProvider("missing-provider", "tuluat-system");

		assertEquals(404, response.getStatusCode().value());
	}
}
