package com.tuluat.engine.embabel;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupMetadata;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.engine.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exposes all registered Tuluat {@link com.tuluat.engine.tool.Tool} instances
 * as Embabel {@link Tool Tools}.
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
	private final ToolGroupMetadata metadata;

	public TuluatToolGroup(ToolRegistry toolRegistry) {
		this.toolRegistry = toolRegistry;
		this.metadata = createMetadata();
	}

	@Override
	public ToolGroupMetadata getMetadata() {
		return metadata;
	}

	@Override
	public List<Tool> getTools() {
		return toolRegistry.getAvailableToolNames().stream().map(this::toEmbabelTool).toList();
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
					"Tuluat AI Operator Tools — calculator, web-search, weather, custom", TOOL_GROUP_NAME,
					TOOL_GROUP_NAME, "tuluat", Set.of(), "1.0.0");
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new IllegalStateException("Embabel ToolGroupMetadata factory not found", e);
		} catch (InvocationTargetException | NoSuchFieldException e) {
			throw new IllegalStateException("Failed to create ToolGroupMetadata", e);
		}
	}
}
