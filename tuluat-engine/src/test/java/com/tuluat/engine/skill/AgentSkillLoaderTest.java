package com.tuluat.engine.skill;

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

@DisplayName("AgentSkillLoader (agentskills.io specification)")
class AgentSkillLoaderTest {

	@Nested
	@DisplayName("parseMarkdown")
	class ParseMarkdown {

		@Test
		@DisplayName("parses minimal valid SKILL.md content")
		void parsesMinimalSkillContent() {
			String markdown = """
					---
					name: pdf-processing
					description: Extract PDF text, fill forms, merge files.
					---

					# PDF Processing Instructions
					Follow these steps to extract PDF content:
					1. Read PDF file.
					2. Extract text.
					""";

			Optional<AgentSkill> result = AgentSkillLoader.parseMarkdown(markdown, Path.of("/tmp/pdf-processing"));

			assertThat(result).isPresent();
			AgentSkill skill = result.get();
			assertThat(skill.name()).isEqualTo("pdf-processing");
			assertThat(skill.description()).isEqualTo("Extract PDF text, fill forms, merge files.");
			assertThat(skill.instructions()).contains("# PDF Processing Instructions").contains("Extract text.");
		}

		@Test
		@DisplayName("parses SKILL.md with all optional frontmatter fields")
		void parsesFullFrontmatter() {
			String markdown = """
					---
					name: code-review
					description: Perform static code analysis and security review.
					license: Apache-2.0
					compatibility: Requires Java 25 and git
					allowed-tools: Bash(git:*) Read
					metadata:
					  author: tuluat-team
					  version: "1.2.0"
					---

					## Code Review Guidelines
					Check for security vulnerabilities and style compliance.
					""";

			Optional<AgentSkill> result = AgentSkillLoader.parseMarkdown(markdown, Path.of("/tmp/code-review"));

			assertThat(result).isPresent();
			AgentSkill skill = result.get();
			assertThat(skill.name()).isEqualTo("code-review");
			assertThat(skill.license()).isEqualTo("Apache-2.0");
			assertThat(skill.compatibility()).isEqualTo("Requires Java 25 and git");
			assertThat(skill.allowedTools()).isEqualTo("Bash(git:*) Read");
			assertThat(skill.metadata()).containsEntry("author", "tuluat-team").containsEntry("version", "1.2.0");
			assertThat(skill.instructions()).contains("Check for security vulnerabilities");
		}

		@Test
		@DisplayName("returns empty for null or missing frontmatter")
		void returnsEmptyForInvalidContent() {
			assertThat(AgentSkillLoader.parseMarkdown(null, null)).isEmpty();
			assertThat(AgentSkillLoader.parseMarkdown("   ", null)).isEmpty();
			assertThat(AgentSkillLoader.parseMarkdown("Just plain markdown without frontmatter", null)).isEmpty();
		}

		@Test
		@DisplayName("falls back to parent directory name if frontmatter name is missing")
		void fallsBackToDirectoryName() {
			String markdown = """
					---
					description: A skill without a explicit name field.
					---
					Instruction body.
					""";

			Optional<AgentSkill> result = AgentSkillLoader.parseMarkdown(markdown, Path.of("/skills/data-analysis"));

			assertThat(result).isPresent();
			assertThat(result.get().name()).isEqualTo("data-analysis");
		}
	}

	@Nested
	@DisplayName("loadFromPath and loadFromFolder")
	class FileAndFolderLoading {

		@Test
		@DisplayName("loads SKILL.md from a skill directory")
		void loadsFromDirectory(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("my-skill");
			Files.createDirectories(skillDir);
			Path skillFile = skillDir.resolve("SKILL.md");

			Files.writeString(skillFile, """
					---
					name: my-skill
					description: Custom skill for testing directory loading.
					---
					Detailed instructions here.
					""");

			Optional<AgentSkill> skill = AgentSkillLoader.loadFromPath(skillDir);

			assertThat(skill).isPresent();
			assertThat(skill.get().name()).isEqualTo("my-skill");
			assertThat(skill.get().skillDir()).isEqualTo(skillDir);
		}

		@Test
		@DisplayName("scans folder recursively for SKILL.md and SKILLS.md files")
		void scansFolderRecursively(@TempDir Path tempDir) throws IOException {
			// Skill 1: SKILL.md
			Path dir1 = tempDir.resolve("skill-1");
			Files.createDirectories(dir1);
			Files.writeString(dir1.resolve("SKILL.md"), """
					---
					name: skill-one
					description: First skill.
					---
					Body 1
					""");

			// Skill 2: SKILLS.md (case insensitive)
			Path dir2 = tempDir.resolve("skill-2");
			Files.createDirectories(dir2);
			Files.writeString(dir2.resolve("SKILLS.md"), """
					---
					name: skill-two
					description: Second skill.
					---
					Body 2
					""");

			List<AgentSkill> skills = AgentSkillLoader.loadFromFolder(tempDir);

			assertThat(skills).hasSize(2);
			assertThat(skills).extracting(AgentSkill::name).containsExactlyInAnyOrder("skill-one", "skill-two");
		}
	}

	@Nested
	@DisplayName("AgentSkill execution and file references")
	class AgentSkillExecution {

		@Test
		@DisplayName("reads referenced relative file from skill directory if requested")
		void executeReadsReferencedFile(@TempDir Path skillDir) throws IOException {
			Path refDir = skillDir.resolve("references");
			Files.createDirectories(refDir);
			Path refFile = refDir.resolve("guide.md");
			Files.writeString(refFile, "Reference Guide Content");

			AgentSkill skill = new AgentSkill("ref-skill", "Ref skill", null, null, null, null, "Main instructions", skillDir);

			String content = skill.readReferenceFile("references/guide.md");

			assertThat(content).isEqualTo("Reference Guide Content");
		}
	}
}
