package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.crd.mcp.McpServer;
import com.tuluat.crd.mcp.McpServerSpec;
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

class McpServerControllerTest {

	private KubernetesClient kubernetesClient;
	private McpServerController controller;

	private MixedOperation mcpMock;
	private NonNamespaceOperation mcpNsMock;
	private Resource mcpResMock;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		kubernetesClient = mock(KubernetesClient.class);
		mcpMock = mock(MixedOperation.class);
		mcpNsMock = mock(NonNamespaceOperation.class);
		mcpResMock = mock(Resource.class);

		when(kubernetesClient.resources(McpServer.class)).thenReturn(mcpMock);
		when(mcpMock.inNamespace(anyString())).thenReturn(mcpNsMock);
		when(mcpNsMock.withName(anyString())).thenReturn(mcpResMock);

		controller = new McpServerController(new KubernetesResourceResolver(kubernetesClient));
	}

	@Test
	@DisplayName("Should list McpServers from Kubernetes namespace or fallback to sample list")
	void testListMcpServers() {
		var server = new McpServer();
		server.setMetadata(new ObjectMetaBuilder().withName("pg-mcp").withNamespace("tuluat-system").build());
		server.setSpec(new McpServerSpec("http://postgres-mcp:8080/sse", "SSE", "NONE", null, 30, "PostgreSQL MCP"));

		var listMock = mock(io.fabric8.kubernetes.api.model.KubernetesResourceList.class);
		when(listMock.getItems()).thenReturn(List.of(server));
		when(mcpNsMock.list()).thenReturn(listMock);

		ResponseEntity<List<Map<String, Object>>> response = controller.listMcpServers("tuluat-system");

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertFalse(response.getBody().isEmpty());
		assertEquals("pg-mcp", response.getBody().get(0).get("name"));
	}

	@Test
	@DisplayName("Should return single McpServer details by name")
	void testGetMcpServer() {
		var server = new McpServer();
		server.setMetadata(new ObjectMetaBuilder().withName("github-mcp").withNamespace("tuluat-system").build());
		server.setSpec(new McpServerSpec("http://github-mcp:8080/sse", "SSE", "API_KEY", null, 30, "GitHub MCP"));

		when(mcpResMock.get()).thenReturn(server);

		ResponseEntity<Map<String, Object>> response = controller.getMcpServer("github-mcp", "tuluat-system");

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals("github-mcp", response.getBody().get("name"));
		assertEquals("SSE", response.getBody().get("transport"));
	}

	@Test
	@DisplayName("Should return 404 when McpServer CR is missing")
	void testGetMcpServerNotFound() {
		when(mcpResMock.get()).thenReturn(null);

		ResponseEntity<Map<String, Object>> response = controller.getMcpServer("missing-mcp", "tuluat-system");

		assertEquals(404, response.getStatusCode().value());
	}
}
