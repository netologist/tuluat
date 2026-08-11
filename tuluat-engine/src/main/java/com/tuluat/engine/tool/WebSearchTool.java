package com.tuluat.engine.tool;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in web search tool (simulated search provider for testing).
 */
public class WebSearchTool implements Tool {

	@Override
	public String name() {
		return "web-search";
	}

	@Override
	public String description() {
		return "Searches the web for real-time technical information and documentation";
	}

	@Override
	public ToolResult execute(String input, Map<String, String> parameters) {
		if (input == null || input.isBlank()) {
			return ToolResult.failure(name(), "Search query cannot be empty");
		}
		var mockResults = List.of("Search Result 1 for '" + input + "': Latest technical specifications and updates.",
				"Search Result 2 for '" + input + "': Community discussions and insights.",
				"Search Result 3 for '" + input + "': Official documentation reference.");
		String formatted = mockResults.stream().map(res -> "- " + res).collect(Collectors.joining("\n"));
		return ToolResult.success(name(), "Search Results for '" + input + "':\n" + formatted);
	}
}
