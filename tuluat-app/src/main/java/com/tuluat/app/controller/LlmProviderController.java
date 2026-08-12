package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
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
@RequestMapping("/api/v1/providers")
public class LlmProviderController {

	private final KubernetesResourceResolver resolver;

	public LlmProviderController(KubernetesResourceResolver resolver) {
		this.resolver = resolver;
	}

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> listProviders(@RequestParam(required = false) String namespace) {
		List<Map<String, Object>> response = resolver.list(LlmProvider.class, namespace).stream()
				.map(this::mapProviderToSecureView).collect(Collectors.toList());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{name}")
	public ResponseEntity<Map<String, Object>> getProvider(@PathVariable String name,
			@RequestParam(required = false) String namespace) {
		LlmProvider provider = resolver.get(LlmProvider.class, namespace, name);
		if (provider == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(mapProviderToSecureView(provider));
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> createOrOverrideProvider(@RequestBody Map<String, Object> request) {
		String name = String.valueOf(request.getOrDefault("name", "custom-provider"));
		String providerType = String.valueOf(request.getOrDefault("providerType", "OPENAI"));
		String baseUrl = (String) request.get("baseUrl");
		String defaultModel = String.valueOf(request.getOrDefault("defaultModel", "deepseek-chat"));
		double costInput = Double.parseDouble(String.valueOf(request.getOrDefault("costPer1kInputTokens", 0.0015)));
		double costOutput = Double.parseDouble(String.valueOf(request.getOrDefault("costPer1kOutputTokens", 0.0030)));
		String newApiKey = (String) request.get("apiKey");

		LlmProvider provider = new LlmProvider();
		ObjectMeta meta = new ObjectMeta();
		meta.setName(name);
		meta.setNamespace(KubernetesResourceResolver.DEFAULT_NAMESPACE);
		provider.setMetadata(meta);

		SecretKeyRef secretRef = (newApiKey != null && !newApiKey.isBlank())
				? new SecretKeyRef(name + "-secret", "api-key")
				: new SecretKeyRef("llm-api-keys", "api-key");

		LlmProviderSpec spec = new LlmProviderSpec(providerType, baseUrl, secretRef, defaultModel, 0.7, 2048, costInput,
				costOutput, List.of());
		provider.setSpec(spec);

		resolver.createOrReplace(LlmProvider.class, provider);
		return ResponseEntity.ok(mapProviderToSecureView(provider));
	}

	@PutMapping("/{name}")
	public ResponseEntity<Map<String, Object>> updateProviderOverride(@PathVariable String name,
			@RequestBody Map<String, Object> request) {
		request.put("name", name);
		return createOrOverrideProvider(request);
	}

	private Map<String, Object> mapProviderToSecureView(LlmProvider p) {
		Map<String, Object> map = new HashMap<>();
		map.put("name", p.getMetadata().getName());
		map.put("namespace", p.getMetadata().getNamespace());
		var spec = p.getSpec();
		if (spec != null) {
			map.put("providerType", spec.providerType());
			map.put("baseUrl", spec.baseUrl() != null ? spec.baseUrl() : "");
			map.put("defaultModel", spec.defaultModel());
			map.put("costPer1kInputTokens", spec.costPer1kInputTokens());
			map.put("costPer1kOutputTokens", spec.costPer1kOutputTokens());
			map.put("fallbacks", spec.fallbacks());
			map.put("apiKeyStatus", spec.apiKeySecretRef() != null ? "Configured (Secret)" : "Not Set");
			map.put("apiKeyMasked", "••••••••••••••••");
		}
		return map;
	}
}
