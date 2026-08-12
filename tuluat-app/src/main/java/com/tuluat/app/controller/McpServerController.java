package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.crd.mcp.McpServer;
import com.tuluat.crd.mcp.McpServerSpec;
import com.tuluat.crd.provider.SecretKeyRef;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/mcp-servers")
public class McpServerController {

	private final KubernetesResourceResolver resolver;

	public McpServerController(KubernetesResourceResolver resolver) {
		this.resolver = resolver;
	}

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> listMcpServers(@RequestParam(required = false) String namespace) {
		List<Map<String, Object>> response = resolver.list(McpServer.class, namespace).stream()
				.map(this::mapMcpServerToSecureView).collect(Collectors.toList());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{name}")
	public ResponseEntity<Map<String, Object>> getMcpServer(@PathVariable String name,
			@RequestParam(required = false) String namespace) {
		McpServer server = resolver.get(McpServer.class, namespace, name);
		if (server == null) {
			return ResponseEntity.notFound().build();
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
		meta.setNamespace(KubernetesResourceResolver.DEFAULT_NAMESPACE);
		server.setMetadata(meta);

		SecretKeyRef secretRef = (newApiKey != null && !newApiKey.isBlank())
				? new SecretKeyRef(name + "-secret", "api-key")
				: null;

		McpServerSpec spec = new McpServerSpec(endpoint, transport, authType, secretRef, 30, description);
		server.setSpec(spec);

		resolver.createOrReplace(McpServer.class, server);
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
		}
		return map;
	}
}
