package com.tuluat.engine.embabel;

import com.tuluat.engine.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.engine.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TuluatToolGroup")
class TuluatToolGroupTest {

	private TuluatToolGroup toolGroup;

	@BeforeEach
	void setUp() {
		toolGroup = new TuluatToolGroup(new ToolRegistry(), java.util.Optional.empty());
	}

	@Nested
	@DisplayName("ToolGroup contract")
	class ToolGroupContract {

		@Test
		@DisplayName("is a ToolGroup")
		void isToolGroup() {
			assertThat(toolGroup).isInstanceOf(ToolGroup.class);
		}

		@Test
		@DisplayName("metadata has correct role name")
		void metadataHasCorrectRole() {
			assertThat(toolGroup.getMetadata().getRole()).isEqualTo(TuluatToolGroup.TOOL_GROUP_NAME);
		}

		@Test
		@DisplayName("metadata has non-empty description")
		void metadataHasDescription() {
			assertThat(toolGroup.getMetadata().getDescription()).isNotBlank();
		}
	}

	@Nested
	@DisplayName("getTools")
	class GetTools {

		@Test
		@DisplayName("exposes built-in tools as Embabel tools")
		void exposesBuiltinTools() {
			assertThat(toolGroup.getTools()).isNotEmpty();
		}

		@Test
		@DisplayName("each tool has name and description")
		void eachToolHasNameAndDescription() {
			for (com.embabel.agent.api.tool.Tool tool : toolGroup.getTools()) {
				assertThat(tool.getDefinition().getName()).isNotBlank();
				assertThat(tool.getDefinition().getDescription()).isNotBlank();
			}
		}

		@Test
		@DisplayName("known built-in tools are present")
		void knownBuiltinsPresent() {
			var names = toolGroup.getTools().stream().map(t -> t.getDefinition().getName()).toList();
			assertThat(names).contains("calculator", "web-search", "weather");
		}
	}

	@Nested
	@DisplayName("custom tool registration")
	class CustomToolRegistration {

		@Test
		@DisplayName("newly registered tools appear in Embabel ToolGroup")
		void newlyRegisteredToolAppearsInToolGroup() {
			var registry = new ToolRegistry();
			registry.register(new Tool() {
				@Override
				public String name() {
					return "echo";
				}
				@Override
				public String description() {
					return "Echoes input";
				}
				@Override
				public ToolResult execute(String input, Map<String, String> params) {
					return ToolResult.success("echo", "echo: " + input);
				}
			});

			var tg = new TuluatToolGroup(registry, java.util.Optional.empty());
			var toolNames = tg.getTools().stream().map(t -> t.getDefinition().getName()).toList();

			assertThat(toolNames).contains("echo");
		}

		@Test
		@DisplayName("tool description matches Tuluat tool description")
		void toolDescriptionMatchesTuluatTool() {
			var registry = new ToolRegistry();
			registry.register(new Tool() {
				@Override
				public String name() {
					return "describe-me";
				}
				@Override
				public String description() {
					return "A custom tool for testing";
				}
				@Override
				public ToolResult execute(String input, Map<String, String> params) {
					return ToolResult.success("describe-me", input);
				}
			});

			var tg = new TuluatToolGroup(registry, java.util.Optional.empty());
			var tool = tg.getTools().stream().filter(t -> "describe-me".equals(t.getDefinition().getName()))
					.findFirst();

			assertThat(tool).isPresent();
			assertThat(tool.orElseThrow().getDefinition().getDescription()).isEqualTo("A custom tool for testing");
		}
	}
}
