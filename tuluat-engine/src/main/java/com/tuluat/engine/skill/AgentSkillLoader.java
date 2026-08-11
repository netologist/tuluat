package com.tuluat.engine.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Loader for <a href="https://agentskills.io">Agent Skills</a> specification.
 *
 * <p>
 * Parses {@code SKILL.md} or {@code SKILLS.md} files containing YAML
 * frontmatter and Markdown body content.
 */
@Slf4j
public final class AgentSkillLoader {

	private static final Pattern FRONTMATTER_PATTERN = Pattern
			.compile("^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?(.*)$", Pattern.DOTALL);

	private AgentSkillLoader() {
	}

	/**
	 * Loads an Agent Skill from a file path or skill directory.
	 *
	 * @param path
	 *            file path (e.g. {@code SKILL.md}) or directory containing a
	 *            {@code SKILL.md}/{@code SKILLS.md}
	 * @return Optional containing the loaded AgentSkill, if valid
	 */
	public static Optional<AgentSkill> loadFromPath(Path path) {
		if (path == null || !Files.exists(path)) {
			return Optional.empty();
		}

		if (Files.isDirectory(path)) {
			Path skillFile = findSkillFileInDirectory(path);
			if (skillFile != null) {
				return loadFromFile(skillFile, path);
			}
			return Optional.empty();
		}

		return loadFromFile(path, path.getParent());
	}

	/**
	 * Scans a folder for all Agent Skills (directories with {@code SKILL.md} /
	 * {@code SKILLS.md} or standalone Markdown skill files).
	 *
	 * @param folder
	 *            folder to scan
	 * @return list of loaded AgentSkill instances
	 */
	public static List<AgentSkill> loadFromFolder(Path folder) {
		List<AgentSkill> skills = new ArrayList<>();
		if (folder == null || !Files.isDirectory(folder)) {
			return skills;
		}

		try (Stream<Path> stream = Files.walk(folder, 3)) {
			stream.filter(p -> Files.isRegularFile(p) && isSkillFileName(p.getFileName().toString()))
					.forEach(file -> loadFromFile(file, file.getParent()).ifPresent(skills::add));
		} catch (IOException e) {
			log.warn("Failed to scan Agent Skills from folder {}: {}", folder, e.getMessage());
		}

		return skills;
	}

	/**
	 * Parses Markdown content with YAML frontmatter into an {@link AgentSkill}.
	 */
	public static Optional<AgentSkill> parseMarkdown(String content, Path skillDir) {
		if (content == null || content.isBlank()) {
			return Optional.empty();
		}

		Matcher matcher = FRONTMATTER_PATTERN.matcher(content.trim());
		if (!matcher.find()) {
			return Optional.empty();
		}

		String yamlBlock = matcher.group(1);
		String markdownBody = matcher.group(2).trim();

		Map<String, String> frontmatter = parseYamlSimple(yamlBlock);
		String name = frontmatter.get("name");
		if (name == null || name.isBlank()) {
			// Fall back to parent directory name if frontmatter name is missing
			if (skillDir != null && skillDir.getFileName() != null) {
				name = skillDir.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9-]", "-");
			} else {
				return Optional.empty();
			}
		}

		String description = frontmatter.getOrDefault("description", "");
		String license = frontmatter.get("license");
		String compatibility = frontmatter.get("compatibility");
		String allowedTools = frontmatter.getOrDefault("allowed-tools", frontmatter.get("allowedtools"));

		Map<String, String> metadata = extractMetadataSubmap(yamlBlock);

		return Optional.of(new AgentSkill(name.trim().toLowerCase(), description.trim(),
				license != null ? license.trim() : null, compatibility != null ? compatibility.trim() : null,
				allowedTools != null ? allowedTools.trim() : null, metadata, markdownBody, skillDir));
	}

	private static Optional<AgentSkill> loadFromFile(Path file, Path skillDir) {
		try {
			String content = Files.readString(file);
			Optional<AgentSkill> skill = parseMarkdown(content, skillDir);
			skill.ifPresent(s -> log.info("Loaded Agent Skill [{}] from {}", s.name(), file.getFileName()));
			return skill;
		} catch (Exception e) {
			log.warn("Failed to read Agent Skill file {}: {}", file, e.getMessage());
			return Optional.empty();
		}
	}

	private static Path findSkillFileInDirectory(Path dir) {
		Path skillMd = dir.resolve("SKILL.md");
		if (Files.exists(skillMd))
			return skillMd;

		Path skillsMd = dir.resolve("SKILLS.md");
		if (Files.exists(skillsMd))
			return skillsMd;

		Path lowerSkillMd = dir.resolve("skill.md");
		if (Files.exists(lowerSkillMd))
			return lowerSkillMd;

		Path lowerSkillsMd = dir.resolve("skills.md");
		if (Files.exists(lowerSkillsMd))
			return lowerSkillsMd;

		return null;
	}

	private static boolean isSkillFileName(String fileName) {
		String lower = fileName.toLowerCase();
		return lower.equals("skill.md") || lower.equals("skills.md");
	}

	/**
	 * Simple line-by-line YAML parser for frontmatter key-value pairs.
	 */
	private static Map<String, String> parseYamlSimple(String yaml) {
		Map<String, String> result = new HashMap<>();
		if (yaml == null)
			return result;

		String[] lines = yaml.split("\\r?\\n");
		String currentKey = null;
		StringBuilder currentValue = new StringBuilder();

		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}

			int colonIdx = line.indexOf(':');
			if (colonIdx > 0 && !line.substring(0, colonIdx).startsWith(" ")) {
				if (currentKey != null) {
					result.put(currentKey, currentValue.toString().trim());
				}
				currentKey = line.substring(0, colonIdx).trim().toLowerCase();
				currentValue = new StringBuilder(stripQuotes(line.substring(colonIdx + 1).trim()));
			} else if (currentKey != null) {
				if (currentValue.length() > 0)
					currentValue.append("\n");
				currentValue.append(trimmed);
			}
		}

		if (currentKey != null) {
			result.put(currentKey, currentValue.toString().trim());
		}

		return result;
	}

	/**
	 * Extracts sub-keys under {@code metadata:} block.
	 */
	private static Map<String, String> extractMetadataSubmap(String yaml) {
		Map<String, String> metadata = new HashMap<>();
		if (yaml == null || !yaml.contains("metadata:"))
			return metadata;

		String[] lines = yaml.split("\\r?\\n");
		boolean inMetadataBlock = false;

		for (String line : lines) {
			if (line.trim().startsWith("metadata:")) {
				inMetadataBlock = true;
				continue;
			}
			if (inMetadataBlock) {
				if (!line.startsWith(" ") && !line.startsWith("\t") && line.contains(":")) {
					break; // Exited indented metadata block
				}
				int colonIdx = line.indexOf(':');
				if (colonIdx > 0) {
					String key = line.substring(0, colonIdx).trim();
					String val = stripQuotes(line.substring(colonIdx + 1).trim());
					if (!key.isEmpty()) {
						metadata.put(key, val);
					}
				}
			}
		}

		return metadata;
	}

	private static String stripQuotes(String str) {
		if (str == null)
			return "";
		if ((str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"))) {
			return str.substring(1, str.length() - 1);
		}
		return str;
	}
}
