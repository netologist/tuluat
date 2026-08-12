package com.tuluat.engine.embabel;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupMetadata;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.engine.tool.ToolResult;
import com.tuluat.protocols.McpClientRegistry;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exposes all registered Tuluat {@link com.tuluat.engine.tool.Tool} and MCP
 * tool instances as Embabel {@link Tool Tools}.
 *
 * <p>
 * Uses reflection to create {@link ToolGroupMetadata} because the Kotlin
 * companion factory method name contains hyphens which are illegal in Java
 * source identifiers.
 */
@Component
public class TuluatToolGroup implements ToolGroup {

	static final String TOOL_GROUP_NAME = "tuluat-tools";

	private final ToolRegistry toolRegistry;
	private final Optional<McpClientRegistry> mcpClientRegistry;
	private final ToolGroupMetadata metadata;

	public TuluatToolGroup(ToolRegistry toolRegistry, Optional<McpClientRegistry> mcpClientRegistry) {
		this.toolRegistry = toolRegistry;
		this.mcpClientRegistry = mcpClientRegistry;
		this.metadata = createMetadata();
	}

	@Override
	public ToolGroupMetadata getMetadata() {
		return metadata;
	}

	@Override
	public List<Tool> getTools() {
		List<Tool> tools = new ArrayList<>();

		// Local tools from ToolRegistry
		toolRegistry.getAvailableToolNames().stream().map(this::toEmbabelTool).forEach(tools::add);

		// MCP tools from McpClientRegistry (ADR 013)
		mcpClientRegistry.ifPresent(registry -> {
			for (String clientName : registry.getAvailableClientNames()) {
				var client = registry.findClient(clientName);
				if (client.isPresent()) {
					tools.add(mcpToEmbabelTool(registry, clientName, client.get().endpoint()));
				}
			}
		});

		return tools;
	}

	private Tool toEmbabelTool(String toolName) {
		var tool = toolRegistry.findTool(toolName).orElseThrow();
		return Tool.create(tool.name(), tool.description(), input -> {
			try {
				ToolResult result = tool.execute(input, Map.of());
				if (result.success()) {
					return Tool.Result.text(result.output());
				}
				return Tool.Result.error(result.output());
			} catch (Exception e) {
				return Tool.Result.error(toolName + " failed: " + e.getMessage(), e);
			}
		});
	}

	/**
	 * Wraps an MCP server as a single Embabel Tool. The tool acts as a proxy that
	 * forwards queries to the MCP server's default tool.
	 */
	private Tool mcpToEmbabelTool(McpClientRegistry registry, String clientName, String endpoint) {
		String toolName = "mcp:" + clientName;
		String description = "MCP tool on " + clientName + " (" + endpoint + ") — JSON-RPC invocation";

		return Tool.create(toolName, description, input -> {
			try {
				var result = registry.invokeTool(clientName, clientName, Map.of("query", input));
				if (result.success()) {
					return Tool.Result.text(result.content());
				}
				return Tool.Result.error(result.error() != null ? result.error() : "MCP tool returned failure");
			} catch (Exception e) {
				return Tool.Result.error(toolName + " failed: " + e.getMessage(), e);
			}
		});
	}

	/**
	 * Calls {@code ToolGroupMetadata.Companion.invoke-oteLqWg} via reflection.
	 * Parameter order: (description, role, name, provider, permissions, version).
	 */
	private static ToolGroupMetadata createMetadata() {
		try {
			Field companionField = ToolGroupMetadata.Companion.class.getDeclaredField("$$INSTANCE");
			companionField.setAccessible(true);
			Object companion = companionField.get(null);
			Method factory = ToolGroupMetadata.Companion.class.getDeclaredMethod("invoke-oteLqWg", String.class,
					String.class, String.class, String.class, Set.class, String.class);
			return (ToolGroupMetadata) factory.invoke(companion,
					"Tuluat AI Operator Tools — local + MCP tools (calculator, web-search, weather, custom, mcp:*)",
					TOOL_GROUP_NAME, TOOL_GROUP_NAME, "tuluat", Set.of(), "1.0.0");
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new IllegalStateException("Embabel ToolGroupMetadata factory not found", e);
		} catch (InvocationTargetException | NoSuchFieldException e) {
			throw new IllegalStateException("Failed to create ToolGroupMetadata", e);
		}
	}
}