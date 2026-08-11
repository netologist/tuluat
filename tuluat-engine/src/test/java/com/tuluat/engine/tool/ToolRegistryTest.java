package com.tuluat.engine.tool;

import com.tuluat.crd.agent.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

	private ToolRegistry toolRegistry;

	@BeforeEach
	void setUp() {
		toolRegistry = new ToolRegistry();
	}

	@Test
	@DisplayName("Should register default tools and list available tool names")
	void testDefaultToolsRegistration() {
		var tools = toolRegistry.getAvailableToolNames();
		assertTrue(tools.contains("calculator"));
		assertTrue(tools.contains("web-search"));
		assertTrue(tools.contains("weather"));
	}

	@Test
	@DisplayName("Should execute enabled tools concurrently on Virtual Threads")
	void testExecuteActiveToolsConcurrently() {
		var toolDefs = List.of(new ToolDefinition("calculator", "Math calculations", true, Map.of()),
				new ToolDefinition("weather", "Weather forecast", true, Map.of()),
				new ToolDefinition("web-search", "Search web", false, Map.of()) // Disabled
		);

		Map<String, ToolResult> results = toolRegistry.executeActiveTools(toolDefs, "25 * 4 in Istanbul");

		assertEquals(2, results.size());
		assertTrue(results.containsKey("calculator"));
		assertTrue(results.containsKey("weather"));
		assertFalse(results.containsKey("web-search"));

		ToolResult calcRes = results.get("calculator");
		assertTrue(calcRes.success());
		assertTrue(calcRes.output().contains("100"));

		ToolResult weatherRes = results.get("weather");
		assertTrue(weatherRes.success());
		assertTrue(weatherRes.output().contains("Istanbul"));
	}

	@Test
	@DisplayName("Should execute calculator tool correctly with math expression")
	void testCalculatorToolDirectExecution() {
		var calc = new CalculatorTool();
		var res = calc.execute("100 / 4", Map.of());
		assertTrue(res.success());
		assertTrue(res.output().contains("25.00"));
	}
}
