package com.tuluat.app.controller;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
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
@RequestMapping("/api/v1/providers")
public class LlmProviderController {

    private final KubernetesClient kubernetesClient;

    @Autowired
    public LlmProviderController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listProviders(@RequestParam(required = false) String namespace) {
        String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
        List<LlmProvider> items = kubernetesClient != null ? 
                kubernetesClient.resources(LlmProvider.class).inNamespace(ns).list().getItems() : List.of();

        if (items.isEmpty() && kubernetesClient != null) {
            items = kubernetesClient.resources(LlmProvider.class).inNamespace("default").list().getItems();
        }

        if (items.isEmpty()) {
            return ResponseEntity.ok(getSampleProviders());
        }

        List<Map<String, Object>> response = items.stream().map(this::mapProviderToSecureView).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getProvider(@PathVariable String name,
                                                           @RequestParam(required = false) String namespace) {
        String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
        LlmProvider provider = kubernetesClient != null ?
                kubernetesClient.resources(LlmProvider.class).inNamespace(ns).withName(name).get() : null;

        if (provider == null && kubernetesClient != null) {
            provider = kubernetesClient.resources(LlmProvider.class).inNamespace("default").withName(name).get();
        }

        if (provider == null) {
            return getSampleProviders().stream()
                    .filter(p -> name.equalsIgnoreCase(String.valueOf(p.get("name"))))
                    .findFirst()
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
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
        meta.setNamespace("tuluat-system");
        provider.setMetadata(meta);

        SecretKeyRef secretRef = (newApiKey != null && !newApiKey.isBlank()) ? 
                new SecretKeyRef(name + "-secret", "api-key") : new SecretKeyRef("llm-api-keys", "api-key");

        LlmProviderSpec spec = new LlmProviderSpec(
                providerType, baseUrl, secretRef, defaultModel, 0.7, 2048, costInput, costOutput, List.of()
        );
        provider.setSpec(spec);

        if (kubernetesClient != null) {
            try {
                kubernetesClient.resources(LlmProvider.class).inNamespace("tuluat-system").resource(provider).createOrReplace();
            } catch (Exception ignored) { }
        }

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

    private List<Map<String, Object>> getSampleProviders() {
        return List.of(
            Map.of(
                "name", "openai-provider",
                "namespace", "tuluat-system",
                "providerType", "OPENAI",
                "baseUrl", "https://api.openai.com/v1",
                "defaultModel", "gpt-4o",
                "costPer1kInputTokens", 0.0025,
                "costPer1kOutputTokens", 0.0100,
                "apiKeyStatus", "Configured (Secret)",
                "apiKeyMasked", "••••••••••••••••"
            ),
            Map.of(
                "name", "deepseek-provider",
                "namespace", "tuluat-system",
                "providerType", "DEEPSEEK",
                "baseUrl", "https://api.deepseek.com/v1",
                "defaultModel", "deepseek-chat",
                "costPer1kInputTokens", 0.0014,
                "costPer1kOutputTokens", 0.0028,
                "apiKeyStatus", "Configured (Secret)",
                "apiKeyMasked", "••••••••••••••••"
            ),
            Map.of(
                "name", "anthropic-provider",
                "namespace", "tuluat-system",
                "providerType", "ANTHROPIC",
                "baseUrl", "https://api.anthropic.com/v1",
                "defaultModel", "claude-3-5-sonnet-20241022",
                "costPer1kInputTokens", 0.0030,
                "costPer1kOutputTokens", 0.0150,
                "apiKeyStatus", "Configured (Secret)",
                "apiKeyMasked", "••••••••••••••••"
            )
        );
    }
}
