package com.tuluat.app.controller;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiAgentControllerTest {

	private KubernetesClient kubernetesClient;
	private WorkflowSessionLogRepository logRepository;
	private AiAgentController controller;

	private MixedOperation agentsMock;
	private NonNamespaceOperation agentNsMock;
	private Resource agentResMock;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		kubernetesClient = mock(KubernetesClient.class);
		logRepository = mock(WorkflowSessionLogRepository.class);

		agentsMock = mock(MixedOperation.class);
		agentNsMock = mock(NonNamespaceOperation.class);
		agentResMock = mock(Resource.class);

		when(kubernetesClient.resources(AiAgent.class)).thenReturn(agentsMock);
		when(agentsMock.inNamespace(anyString())).thenReturn(agentNsMock);
		when(agentNsMock.withName(anyString())).thenReturn(agentResMock);

		controller = new AiAgentController(kubernetesClient, logRepository);
	}

	@Test
	@DisplayName("Should list AiAgents from Kubernetes namespace")
	void testListAgents() {
		var agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName("researcher-agent").withNamespace("tuluat-system").build());
		agent.setSpec(new AiAgentSpec(new ProviderRef("openai-provider", "tuluat-system"), "gpt-4o", "Research prompt",
				"Input", List.of(), List.of(), List.of(), null, null, null, 1));

		var listMock = mock(io.fabric8.kubernetes.api.model.KubernetesResourceList.class);
		when(listMock.getItems()).thenReturn(List.of(agent));
		when(agentNsMock.list()).thenReturn(listMock);

		ResponseEntity<List<Map<String, Object>>> response = controller.listAgents("tuluat-system");

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertFalse(response.getBody().isEmpty());
		assertEquals("researcher-agent", response.getBody().get(0).get("name"));
	}

	@Test
	@DisplayName("Should return agent logs for given agent name")
	void testGetAgentLogs() {
		WorkflowSessionLogEntity logEntity = new WorkflowSessionLogEntity();
		logEntity.setSessionId(UUID.randomUUID());
		logEntity.setNodeId("research-node");
		logEntity.setMessage("Executing Agent [research-node] prompt: test");
		when(logRepository.findAll()).thenReturn(List.of(logEntity));

		ResponseEntity<List<Map<String, Object>>> response = controller.getAgentLogs("research-node");

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertFalse(response.getBody().isEmpty());
	}
}
