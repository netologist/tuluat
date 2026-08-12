package com.tuluat.app.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Centralizes the "resolve namespace, then fall back to {@code default}" read
 * pattern repeated across the REST controllers. Write operations stay on the
 * caller via {@link #createOrReplace(Class, HasMetadata)}.
 */
@Component
public class KubernetesResourceResolver {

	public static final String DEFAULT_NAMESPACE = "tuluat-system";
	private static final String FALLBACK_NAMESPACE = "default";

	private final KubernetesClient client;

	public KubernetesResourceResolver(KubernetesClient client) {
		this.client = client;
	}

	public String resolveNamespace(String requested) {
		return (requested != null && !requested.isBlank()) ? requested : DEFAULT_NAMESPACE;
	}

	public <T extends HasMetadata> List<T> list(Class<T> type, String namespace) {
		String ns = resolveNamespace(namespace);
		List<T> items = client.resources(type).inNamespace(ns).list().getItems();
		if (items.isEmpty() && !FALLBACK_NAMESPACE.equals(ns)) {
			items = client.resources(type).inNamespace(FALLBACK_NAMESPACE).list().getItems();
		}
		return items;
	}

	public <T extends HasMetadata> T get(Class<T> type, String namespace, String name) {
		String ns = resolveNamespace(namespace);
		T item = client.resources(type).inNamespace(ns).withName(name).get();
		if (item == null && !FALLBACK_NAMESPACE.equals(ns)) {
			item = client.resources(type).inNamespace(FALLBACK_NAMESPACE).withName(name).get();
		}
		return item;
	}

	public <T extends HasMetadata> T createOrReplace(Class<T> type, T resource) {
		return client.resources(type).resource(resource).createOrReplace();
	}
}
