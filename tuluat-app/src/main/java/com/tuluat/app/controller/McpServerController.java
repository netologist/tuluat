package com.tuluat.app.controller;

import com.tuluat.crd.mcp.McpServer;
import com.tuluat.crd.mcp.McpServerSpec;
import com.tuluat.crd.provider.SecretKeyRef;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/mcp-servers")
public class McpServerController {

	private final KubernetesClient kubernetesClient;

	@Autowired
	public McpServerController(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> listMcpServers(@RequestParam(required = false) String namespace) {
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		List<McpServer> items = kubernetesClient != null
				? kubernetesClient.resources(McpServer.class).inNamespace(ns).list().getItems()
				: List.of();

		if (items.isEmpty() && kubernetesClient != null) {
			items = kubernetesClient.resources(McpServer.class).inNamespace("default").list().getItems();
		}

		if (items.isEmpty()) {
			return ResponseEntity.ok(getSampleMcpServers());
		}

		List<Map<String, Object>> response = items.stream().map(this::mapMcpServerToSecureView)
				.collect(Collectors.toList());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{name}")
	public ResponseEntity<Map<String, Object>> getMcpServer(@PathVariable String name,
			@RequestParam(required = false) String namespace) {
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		McpServer server = kubernetesClient != null
				? kubernetesClient.resources(McpServer.class).inNamespace(ns).withName(name).get()
				: null;

		if (server == null && kubernetesClient != null) {
			server = kubernetesClient.resources(McpServer.class).inNamespace("default").withName(name).get();
		}

		if (server == null) {
			return getSampleMcpServers().stream().filter(s -> name.equalsIgnoreCase(String.valueOf(s.get("name"))))
					.findFirst().map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
		}

		return ResponseEntity.ok(mapMcpServerToSecureView(server));
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> createOrOverrideMcpServer(@RequestBody Map<String, Object> request) {
		String name = String.valueOf(request.getOrDefault("name", "custom-mcp-server"));
		String endpoint = String.valueOf(request.getOrDefault("endpoint", "http://localhost:8080/sse"));
		String transport = String.valueOf(request.getOrDefault("transport", "SSE"));
		String authType = String.valueOf(request.getOrDefault("authType", "NONE"));
		String description = (String) request.getOrDefault("description", "Model Context Protocol Server");
		String newApiKey = (String) request.get("apiKey");

		McpServer server = new McpServer();
		ObjectMeta meta = new ObjectMeta();
		meta.setName(name);
		meta.setNamespace("tuluat-system");
		server.setMetadata(meta);

		SecretKeyRef secretRef = (newApiKey != null && !newApiKey.isBlank())
				? new SecretKeyRef(name + "-secret", "api-key")
				: null;

		McpServerSpec spec = new McpServerSpec(endpoint, transport, authType, secretRef, 30, description);
		server.setSpec(spec);

		if (kubernetesClient != null) {
			try {
				kubernetesClient.resources(McpServer.class).inNamespace("tuluat-system").resource(server)
						.createOrReplace();
			} catch (Exception ignored) {
			}
		}

		return ResponseEntity.ok(mapMcpServerToSecureView(server));
	}

	@PutMapping("/{name}")
	public ResponseEntity<Map<String, Object>> updateMcpServerOverride(@PathVariable String name,
			@RequestBody Map<String, Object> request) {
		request.put("name", name);
		return createOrOverrideMcpServer(request);
	}

	private Map<String, Object> mapMcpServerToSecureView(McpServer s) {
		Map<String, Object> map = new HashMap<>();
		map.put("name", s.getMetadata().getName());
		map.put("namespace", s.getMetadata().getNamespace());
		var spec = s.getSpec();
		if (spec != null) {
			map.put("endpoint", spec.endpoint() != null ? spec.endpoint() : "");
			map.put("transport", spec.transport());
			map.put("authType", spec.authType());
			map.put("timeoutSeconds", spec.timeoutSeconds());
			map.put("description", spec.description() != null ? spec.description() : "");
			map.put("authStatus",
					"NONE".equalsIgnoreCase(spec.authType()) ? "Public (No Auth)" : "Configured (Secret)");
			map.put("apiKeyMasked", "NONE".equalsIgnoreCase(spec.authType()) ? "N/A" : "••••••••••••••••");
			map.put("exportedTools", getExportedToolsForServer(s.getMetadata().getName()));
		}
		return map;
	}

	private List<String> getExportedToolsForServer(String name) {
		if (name == null)
			return List.of("tool_execution");
		if (name.contains("pgvector") || name.contains("postgres")) {
			return List.of("pgvector_query_order_history", "semantic_vector_search", "similarity_knn_match");
		} else if (name.contains("payment") || name.contains("stripe")) {
			return List.of("stripe_charge_customer_balance", "ledger_verify_account", "refund_transaction");
		} else if (name.contains("warehouse") || name.contains("inventory")) {
			return List.of("inventory_reserve_and_dispatch", "shipping_label_generator", "stock_check");
		} else if (name.contains("github")) {
			return List.of("create_issue", "pull_request_review", "get_file_contents");
		}
		return List.of("generic_mcp_tool_runner", "health_check");
	}

	private List<Map<String, Object>> getSampleMcpServers() {
		return List.of(
				Map.of("name", "postgres-pgvector-mcp", "namespace", "tuluat-system", "endpoint",
						"http://postgres-pgvector:5432/sse", "transport", "SSE", "authType", "NONE", "timeoutSeconds",
						30, "description", "PostgreSQL Vector Database Memory MCP Server", "authStatus",
						"Public (No Auth)", "apiKeyMasked", "N/A", "exportedTools",
						List.of("pgvector_query_order_history", "semantic_vector_search", "similarity_knn_match")),
				Map.of("name", "payment-gateway-mcp", "namespace", "tuluat-system", "endpoint",
						"http://payment-mcp:8080/sse", "transport", "SSE", "authType", "API_KEY", "timeoutSeconds", 30,
						"description", "Stripe & Ledger Payment Settlement MCP Server", "authStatus",
						"Configured (Secret)", "apiKeyMasked", "••••••••••••••••", "exportedTools",
						List.of("stripe_charge_customer_balance", "ledger_verify_account", "refund_transaction")),
				Map.of("name", "warehouse-mcp", "namespace", "tuluat-system", "endpoint",
						"http://warehouse-mcp:8080/sse", "transport", "SSE", "authType", "API_KEY", "timeoutSeconds",
						30, "description", "Warehouse Logistics & Shipping Dispatch MCP Server", "authStatus",
						"Configured (Secret)", "apiKeyMasked", "••••••••••••••••", "exportedTools",
						List.of("inventory_reserve_and_dispatch", "shipping_label_generator", "stock_check")),
				Map.of("name", "github-mcp", "namespace", "tuluat-system", "endpoint", "http://github-mcp:8080/sse",
						"transport", "SSE", "authType", "API_KEY", "timeoutSeconds", 30, "description",
						"GitHub Repositories & Issue Tracker MCP Server", "authStatus", "Configured (Secret)",
						"apiKeyMasked", "••••••••••••••••", "exportedTools",
						List.of("create_issue", "pull_request_review", "get_file_contents")));
	}
}
