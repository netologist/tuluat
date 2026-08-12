package com.tuluat.operator.event;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * Best-effort recorder of core/v1 {@link Event}s against reconciled resources.
 * Events surface through {@code kubectl describe} and {@code kubectl get
 * events}. Failures to record an event are logged but never propagate, so event
 * recording can never break reconciliation.
 */
@Component
@Slf4j
public class KubernetesEventRecorder {

	public static final String TYPE_NORMAL = "Normal";
	public static final String TYPE_WARNING = "Warning";

	private final KubernetesClient client;

	public KubernetesEventRecorder(KubernetesClient client) {
		this.client = client;
	}

	public void record(HasMetadata resource, String type, String reason, String message) {
		if (resource == null || resource.getMetadata() == null) {
			return;
		}
		try {
			String namespace = resource.getMetadata().getNamespace();
			String now = Instant.now().toString();
			Event event = new EventBuilder().withNewMetadata().withGenerateName(resource.getMetadata().getName() + "-")
					.withNamespace(namespace).endMetadata().withType(type).withReason(reason).withMessage(message)
					.withNewInvolvedObject().withKind(resource.getKind()).withApiVersion(resource.getApiVersion())
					.withName(resource.getMetadata().getName()).withNamespace(namespace)
					.withUid(resource.getMetadata().getUid()).endInvolvedObject().withFirstTimestamp(now)
					.withLastTimestamp(now).withCount(1).build();
			client.v1().events().inNamespace(namespace).resource(event).create();
		} catch (Exception e) {
			log.warn("Failed to record event {} for {}/{}: {}", reason, resource.getMetadata().getNamespace(),
					resource.getMetadata().getName(), e.getMessage());
		}
	}
}
