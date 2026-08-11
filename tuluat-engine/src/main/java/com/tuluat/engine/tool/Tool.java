package com.tuluat.engine.tool;

import java.util.Map;

/**
 * Contract for AI Tools. Non-sealed so external JARs can contribute
 * implementations via {@link ToolProvider} (ADR 007: tool provisioning vs
 * binding).
 */
public interface Tool {
	String name();
	String description();
	ToolResult execute(String input, Map<String, String> parameters);
}
