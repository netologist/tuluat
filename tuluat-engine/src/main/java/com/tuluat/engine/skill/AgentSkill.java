package com.tuluat.engine.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Represents a Markdown-based Agent Skill compliant with the
 * <a href="https://agentskills.io/specification">Agent Skills
 * Specification</a>.
 *
 * <p>
 * Loaded from a {@code SKILL.md} or {@code SKILLS.md} file containing YAML
 * frontmatter (name, description, license, compatibility, metadata,
 * allowed-tools) followed by Markdown instructions.
 */
public record AgentSkill(String name, String description, String license, String compatibility, String allowedTools,
		Map<String, String> metadata, String instructions, Path skillDir) {

	public AgentSkill {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Agent skill name cannot be null or blank");
		}
		if (description == null) {
			description = "";
		}
		if (metadata == null) {
			metadata = Map.of();
		}
		if (instructions == null) {
			instructions = "";
		}
	}

	/**
	 * Resolves a referenced sub-file (e.g. {@code references/guide.md}) relative to
	 * the skill directory.
	 */
	public String readReferenceFile(String relativePath) {
		if (relativePath != null && !relativePath.isBlank() && skillDir != null) {
			Path targetFile = skillDir.resolve(relativePath.trim()).normalize();
			if (targetFile.startsWith(skillDir) && Files.exists(targetFile) && Files.isRegularFile(targetFile)) {
				try {
					return Files.readString(targetFile);
				} catch (IOException e) {
					return "Error reading " + relativePath + ": " + e.getMessage();
				}
			}
		}
		return null;
	}
}
