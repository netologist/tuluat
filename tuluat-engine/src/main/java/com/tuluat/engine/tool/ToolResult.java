package com.tuluat.engine.tool;

import java.util.Map;

/**
 * Record representing the outcome of executing a tool.
 */
public record ToolResult(String toolName, boolean success, String output, Map<String, Object> metadata) {
	public static ToolResult success(String toolName, String output) {
		return new ToolResult(toolName, true, output, Map.of("timestamp", System.currentTimeMillis()));
	}

	public static ToolResult failure(String toolName, String error) {
		return new ToolResult(toolName, false, error, Map.of("timestamp", System.currentTimeMillis()));
	}
}
