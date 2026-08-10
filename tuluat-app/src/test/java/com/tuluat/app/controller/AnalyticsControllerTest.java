package com.tuluat.app.controller;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AnalyticsControllerTest {

	private KubernetesClient kubernetesClient;
	private WorkflowSessionRepository sessionRepository;
	private WorkflowSessionLogRepository logRepository;
	private AnalyticsController controller;

	private MixedOperation providersMock;
	private NonNamespaceOperation providerNsMock;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		kubernetesClient = mock(KubernetesClient.class);
		sessionRepository = mock(WorkflowSessionRepository.class);
		logRepository = mock(WorkflowSessionLogRepository.class);

		providersMock = mock(MixedOperation.class);
		providerNsMock = mock(NonNamespaceOperation.class);

		when(kubernetesClient.resources(LlmProvider.class)).thenReturn(providersMock);
		when(providersMock.inNamespace(anyString())).thenReturn(providerNsMock);

		controller = new AnalyticsController(kubernetesClient, sessionRepository, logRepository);
	}

	@Test
	@DisplayName("Should return analytics providers list")
	void testListProviders() {
		var provider = new LlmProvider();
		provider.setMetadata(
				new ObjectMetaBuilder().withName("openai-provider").withNamespace("tuluat-system").build());
		provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", null, "gpt-4o", 0.7, 2048, 0.0025,
				0.01, List.of()));

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
	@DisplayName("Should return analytics overview metrics")
	void testGetAnalyticsOverview() {
		WorkflowSessionEntity s1 = new WorkflowSessionEntity();
		s1.setSessionId(UUID.randomUUID());
		s1.setStatus("COMPLETED");

		when(sessionRepository.findAll()).thenReturn(List.of(s1));

		ResponseEntity<Map<String, Object>> response = controller.getAnalyticsOverview();

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().containsKey("totalSessions"));
		assertEquals(1L, response.getBody().get("totalSessions"));
	}
}
