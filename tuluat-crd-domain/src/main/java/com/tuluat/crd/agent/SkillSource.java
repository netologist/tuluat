package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Skill provisioning source: where the platform should load skills from.
 * Multiple sources may be declared; they are loaded in declaration order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkillSource(@JsonProperty("type") String type, // FOLDER, JAR, CONFIGMAP
		@JsonProperty("path") String path, // e.g. /opt/tuluat/skills or ConfigMap name
		@JsonProperty("watch") Boolean watch // hot-reload for FOLDER/JAR sources
) {
	public SkillSource {
		if (type == null || type.isBlank()) {
			type = "FOLDER";
		}
		if (watch == null) {
			watch = false;
		}
	}
}
