package com.tuluat.engine.tool;

import com.tuluat.crd.agent.ToolSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolRegistry loadedProviders")
class ToolRegistryLoadedProvidersTest {
	private static final String TEST_AGENT = "test-agent";

	private ToolRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new ToolRegistry();
	}

	@Nested
	@DisplayName("registerProvider")
	class RegisterProvider {

		@Test
		@DisplayName("registers tools from a custom provider")
		void registersCustomProviderTools() {
			registry.registerProvider(new TestToolProvider("ext-math", new CalculatorTool()));

			assertThat(registry.getAvailableToolNames()).contains("calculator");
		}

		@Test
		@DisplayName("handles provider with no tools gracefully")
		void handlesEmptyProvider() {
			registry.registerProvider(new TestToolProvider("empty"));

			assertThat(registry.getAvailableToolNames()).hasSize(3); // only builtins
		}

		@Test
		@DisplayName("tools from provider shadow built-in tools by name")
		void shadowsBuiltinTools() {
			var customCalc = new Tool() {
				@Override
				public String name() {
					return "calculator";
				}
				@Override
				public String description() {
					return "Custom calc";
				}
				@Override
				public ToolResult execute(String input, Map<String, String> parameters) {
					return ToolResult.success("calculator", "custom: " + input);
				}
			};

			registry.registerProvider(new TestToolProvider("custom", customCalc));

			var tool = registry.findTool("calculator").orElseThrow();
			assertThat(tool.description()).isEqualTo("Custom calc");
			assertThat(tool.execute("1+1", Map.of()).output()).isEqualTo("custom: 1+1");
		}
	}

	@Nested
	@DisplayName("loadToolSources")
	class LoadToolSources {

		@Test
		@DisplayName("handles null source list")
		void handlesNullSources() {
			registry.loadToolSources(TEST_AGENT, null);
			assertThat(registry.getAvailableToolNames()).hasSize(3); // builtins intact
		}

		@Test
		@DisplayName("handles empty source list")
		void handlesEmptySources() {
			registry.loadToolSources(TEST_AGENT, List.of());
			assertThat(registry.getAvailableToolNames()).hasSize(3);
		}

		@Test
		@DisplayName("skips sources with null or blank path")
		void skipsNullPathSources(@TempDir Path tempDir) {
			registry.loadToolSources(TEST_AGENT, List.of(new ToolSource("FOLDER", null, false),
					new ToolSource("FOLDER", "  ", false), new ToolSource("FOLDER", tempDir.toString(), false)));

			assertThat(registry.getAvailableToolNames()).hasSize(3); // no new tools loaded
		}

		@Test
		@DisplayName("handles non-existent folder paths gracefully")
		void handlesNonExistentFolder() {
			registry.loadToolSources(TEST_AGENT, List.of(new ToolSource("FOLDER", "/nonexistent/path/12345", false)));

			assertThat(registry.getAvailableToolNames()).hasSize(3); // builtins intact
		}

		@Test
		@DisplayName("unknown source type is silently ignored")
		void ignoresUnknownSourceType() {
			registry.loadToolSources(TEST_AGENT, List.of(new ToolSource("UNKNOWN_TYPE", "/some/path", false)));

			assertThat(registry.getAvailableToolNames()).hasSize(3);
		}

		@Test
		@DisplayName("supports CONFIGMAP type same as FOLDER")
		void supportsConfigMapType(@TempDir Path tempDir) {
			registry.loadToolSources(TEST_AGENT, List.of(new ToolSource("CONFIGMAP", tempDir.toString(), false)));

			// empty folder → no providers found, but no error
			assertThat(registry.getAvailableToolNames()).hasSize(3);
		}
	}

	@Nested
	@DisplayName("findTool")
	class FindTool {

		@Test
		@DisplayName("returns empty for null name")
		void returnsEmptyForNull() {
			assertThat(registry.findTool(null)).isEmpty();
		}

		@Test
		@DisplayName("returns empty for unknown name")
		void returnsEmptyForUnknown() {
			assertThat(registry.findTool("nonexistent")).isEmpty();
		}

		@Test
		@DisplayName("is case-insensitive")
		void isCaseInsensitive() {
			assertThat(registry.findTool("CALCULATOR")).isPresent();
			assertThat(registry.findTool("Calculator")).isPresent();
			assertThat(registry.findTool("Web-Search")).isPresent();
		}
	}

	// ── helpers ────────────────────────────────────────────────────────────

	private record TestToolProvider(String providerName, Tool... tools) implements ToolProvider {
		@Override
		public List<Tool> provideTools() {
			return List.of(tools);
		}
	}
}
