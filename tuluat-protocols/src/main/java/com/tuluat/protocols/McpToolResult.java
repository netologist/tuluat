package com.tuluat.protocols;

/**
 * Result of invoking a tool on an MCP server.
 *
 * @param success
 *            whether the invocation succeeded
 * @param content
 *            tool result content (JSON string)
 * @param toolName
 *            invoked tool
 * @param error
 *            error message when unsuccessful
 */
public record McpToolResult(boolean success, String content, String toolName, String error) {
	public static McpToolResult ok(String toolName, String content) {
		return new McpToolResult(true, content, toolName, null);
	}

	public static McpToolResult failure(String toolName, String error) {
		return new McpToolResult(false, null, toolName, error);
	}
}