package com.tuluat.app.controller;

import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.NodeDefinition;
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

class WorkflowControllerTest {

	private KubernetesClient kubernetesClient;
	private WorkflowController controller;

	private MixedOperation workflowsMock;
	private NonNamespaceOperation workflowNsMock;
	private Resource workflowResMock;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		kubernetesClient = mock(KubernetesClient.class);
		workflowsMock = mock(MixedOperation.class);
		workflowNsMock = mock(NonNamespaceOperation.class);
		workflowResMock = mock(Resource.class);

		when(kubernetesClient.resources(AiWorkflow.class)).thenReturn(workflowsMock);
		when(workflowsMock.inNamespace(anyString())).thenReturn(workflowNsMock);
		when(workflowNsMock.withName(anyString())).thenReturn(workflowResMock);

		controller = new WorkflowController(kubernetesClient);
	}

	@Test
	@DisplayName("Should list AiWorkflows from Kubernetes namespace")
	void testListWorkflows() {
		var workflow = new AiWorkflow();
		workflow.setMetadata(new ObjectMetaBuilder().withName("research-wf").withNamespace("tuluat-system").build());
		var spec = new AiWorkflowSpec("Research workflow", "node-1",
				List.of(new NodeDefinition("node-1", null, null, null, null, null, null)), null, null, null);
		workflow.setSpec(spec);

		var listMock = mock(io.fabric8.kubernetes.api.model.KubernetesResourceList.class);
		when(listMock.getItems()).thenReturn(List.of(workflow));
		when(workflowNsMock.list()).thenReturn(listMock);

		ResponseEntity<List<Map<String, Object>>> response = controller.listWorkflows("tuluat-system");

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertFalse(response.getBody().isEmpty());
		assertEquals("research-wf", response.getBody().get(0).get("name"));
	}

	@Test
	@DisplayName("Should return 404 when AiWorkflow is not found")
	void testGetWorkflowNotFound() {
		when(workflowResMock.get()).thenReturn(null);

		ResponseEntity<Map<String, Object>> response = controller.getWorkflow("missing-wf", "tuluat-system");

		assertEquals(404, response.getStatusCode().value());
	}
}
