package com.tuluat.engine.tool;

import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.agent.ToolSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

/**
 * Service managing tool registry and execution using Java Virtual Threads and
 * Streams. Tools are contributed by {@link ToolProvider} implementations:
 * compiled-in (builtin provider) or external JARs loaded from
 * {@code toolSources} folders.
 */
@Service
@Slf4j
public class ToolRegistry {
	private final Map<String, Tool> registeredTools = new ConcurrentHashMap<>();
	private final Map<String, List<ToolJarLoader.LoadedProvider>> loadedProviders = new ConcurrentHashMap<>();
	private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

	public ToolRegistry() {
		// Register compiled-in tools via the builtin provider (ADR 007)
		registerProvider(new BuiltinToolProvider());
	}

	/**
	 * Register all tools from a provider.
	 */
	public void registerProvider(ToolProvider provider) {
		for (Tool tool : provider.provideTools()) {
			register(tool);
		}
		log.info("Registered tool provider [{}] with {} tool(s)", provider.providerName(),
				provider.provideTools().size());
	}

	/**
	 * Load tools from declared {@link ToolSource} entries (FOLDER / JAR).
	 */
	public void loadToolSources(List<ToolSource> sources) {
		if (sources == null || sources.isEmpty()) {
			return;
		}
		for (ToolSource source : sources) {
			if (source == null || source.path() == null || source.path().isBlank()) {
				continue;
			}
			if ("CONFIGMAP".equalsIgnoreCase(source.type()) || "FOLDER".equalsIgnoreCase(source.type())
					|| "JAR".equalsIgnoreCase(source.type())) {
				List<ToolJarLoader.LoadedProvider> found = ToolJarLoader.loadFromFolder(Paths.get(source.path()));
				loadedProviders.put(source.path(), found);
				found.forEach(lp -> registerProvider(lp.provider()));
			}
		}
	}

	public void register(Tool tool) {
		registeredTools.put(tool.name().toLowerCase(), tool);
		log.info("Registered tool: {} [{}]", tool.name(), tool.description());
	}

	public Optional<Tool> findTool(String name) {
		if (name == null)
			return Optional.empty();
		return Optional.ofNullable(registeredTools.get(name.toLowerCase()));
	}

	/**
	 * Executes requested active tools concurrently on Virtual Threads and returns
	 * results mapped by tool name.
	 */
	public Map<String, ToolResult> executeActiveTools(List<ToolDefinition> toolDefs, String userInput) {
		if (toolDefs == null || toolDefs.isEmpty()) {
			return Map.of();
		}

		List<ToolDefinition> activeDefs = toolDefs.stream().filter(def -> Boolean.TRUE.equals(def.enabled())).toList();
		if (activeDefs.isEmpty()) {
			return Map.of();
		}

		Map<String, ToolResult> results = new ConcurrentHashMap<>();

		// Virtual Thread per task execution using modern Java concurrency
		try {
			var futures = activeDefs.stream().map(def -> virtualThreadExecutor.submit(() -> {
				Tool tool = findTool(def.name()).orElseGet(() -> new CustomTool(def.name(), def.description()));
				return tool.execute(userInput, def.parameters());
			})).toList();

			for (var f : futures) {
				ToolResult res = f.get();
				results.put(res.toolName(), res);
			}
		} catch (Exception e) {
			log.error("Error executing active tools concurrently", e);
		}

		return results;
	}

	public List<String> getAvailableToolNames() {
		return registeredTools.keySet().stream().sorted().toList();
	}

	/** Compiled-in tools (Calculator, Web Search, Weather). */
	public static final class BuiltinToolProvider implements ToolProvider {
		@Override
		public String providerName() {
			return "builtin";
		}

		@Override
		public List<Tool> provideTools() {
			return List.of(new CalculatorTool(), new WebSearchTool(), new WeatherTool());
		}
	}

	@PreDestroy
	public void shutdown() {
		virtualThreadExecutor.shutdown();
	}
}
