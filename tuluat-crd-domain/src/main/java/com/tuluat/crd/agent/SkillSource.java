package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Skill provisioning source: folder or ConfigMap containing SKILL.md /
 * SKILLS.md specification files.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkillSource(@JsonProperty("type") String type, // FOLDER, CONFIGMAP
		@JsonProperty("path") String path, // e.g. /opt/tuluat/skills or ConfigMap name
		@JsonProperty("watch") Boolean watch // hot-reload for FOLDER sources
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
