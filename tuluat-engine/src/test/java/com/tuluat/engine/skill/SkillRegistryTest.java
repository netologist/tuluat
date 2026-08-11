package com.tuluat.engine.skill;

import com.tuluat.crd.agent.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillRegistry")
class SkillRegistryTest {

	private SkillRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new SkillRegistry();
	}

	@Nested
	@DisplayName("registerSkill and findSkill")
	class RegisterAndFind {

		@Test
		@DisplayName("registers and finds AgentSkill by name case-insensitively")
		void registersAndFindsSkill() {
			AgentSkill skill = new AgentSkill("pdf-parser", "Parses PDF files", null, null, null, Map.of(), "Instructions", null);
			registry.registerSkill(skill);

			Optional<AgentSkill> found = registry.findSkill("PDF-PARSER");
			assertThat(found).isPresent();
			assertThat(found.get().description()).isEqualTo("Parses PDF files");
		}

		@Test
		@DisplayName("returns empty for null or missing skill")
		void handlesNullAndMissing() {
			assertThat(registry.findSkill(null)).isEmpty();
			assertThat(registry.findSkill("nonexistent")).isEmpty();
		}
	}

	@Nested
	@DisplayName("loadSkillsFromFolder and loadSkillSources")
	class LoadingSkills {

		@Test
		@DisplayName("loads Agent Skills from directory")
		void loadsFromDirectory(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("code-review");
			Files.createDirectories(skillDir);
			Files.writeString(skillDir.resolve("SKILL.md"), """
					---
					name: code-review
					description: Automated PR code review.
					---
					## Instructions
					Review for style and security.
					""");

			registry.loadSkillsFromFolder(tempDir);

			assertThat(registry.getAvailableSkillNames()).contains("code-review");
			AgentSkill skill = registry.findSkill("code-review").orElseThrow();
			assertThat(skill.description()).isEqualTo("Automated PR code review.");
		}

		@Test
		@DisplayName("loadSkillSources parses ToolSource entries")
		void loadsFromToolSources(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("sql-optimization");
			Files.createDirectories(skillDir);
			Files.writeString(skillDir.resolve("SKILLS.md"), """
					---
					name: sql-optimization
					description: Optimize PostgreSQL queries.
					---
					Analyze EXPLAIN ANALYZE output.
					""");

			SkillSource source = new SkillSource("FOLDER", tempDir.toString(), false);
			registry.loadSkillSources(List.of(source));

			assertThat(registry.getAvailableSkillNames()).contains("sql-optimization");
		}
	}
}
