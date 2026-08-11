package com.tuluat.engine.tool;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in fallback tool for custom/unrecognized tool definitions.
 */
public class CustomTool implements Tool {

	private final String toolName;
	private final String description;

	public CustomTool(String toolName, String description) {
		this.toolName = toolName;
		this.description = description != null ? description : "Custom tool: " + toolName;
	}

	@Override
	public String name() {
		return toolName;
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public ToolResult execute(String input, Map<String, String> parameters) {
		String paramsStr = parameters.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining(", "));
		String result = String.format("Executed Custom Tool [%s] with input: '%s', params: [%s]", toolName, input,
				paramsStr);
		return ToolResult.success(toolName, result);
	}
}
