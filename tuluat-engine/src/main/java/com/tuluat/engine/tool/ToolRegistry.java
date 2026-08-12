package com.tuluat.engine.tool;

import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.agent.ToolSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

/**
 * Service managing tool registry and execution using Java Virtual Threads and
 * Streams.
 *
 * <p>
 * Tools are contributed by:
 * <ul>
 * <li>Compiled-in builtins via {@link BuiltinToolProvider} — shared read-only
 * base available to all agents</li>
 * <li>External JARs via {@link ToolProvider} SPI (loaded via
 * {@link ToolJarLoader}) — loaded into per-agent scopes for isolation</li>
 * </ul>
 *
 * <p>
 * Each agent gets its own {@link AgentToolScope} keyed by agent name. This
 * prevents tool leakage between agents and allows proper classloader lifecycle
 * management — an agent's JAR tools are evicted when the agent is deleted.
 */
@Service
@Slf4j
public class ToolRegistry {
	/** Builtin tools (Calculator, Web Search, Weather) — shared read-only base. */
	private final Map<String, Tool> builtinTools = new ConcurrentHashMap<>();
	/** Per-agent tool scopes keyed by agent name. */
	private final Map<String, AgentToolScope> agentScopes = new ConcurrentHashMap<>();
	private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

	public ToolRegistry() {
		// Register compiled-in tools as shared read-only base (ADR 007)
		BuiltinToolProvider builtin = new BuiltinToolProvider();
		builtin.provideTools().forEach(t -> builtinTools.put(t.name().toLowerCase(), t));
		log.info("Registered {} builtin tool(s)", builtinTools.size());
	}

	// ── Global (union) accessors — for tool discovery by Embabel / observability ──

	/**
	 * Returns all tool names visible across builtins and every agent scope.
	 * Used by {@code TuluatToolGroup} for Embabel tool discovery.
	 */
	public List<String> getAvailableToolNames() {
		var names = new ArrayList<>(builtinTools.keySet());
		agentScopes.values().forEach(scope -> names.addAll(scope.tools.keySet()));
		return names.stream().distinct().sorted().toList();
	}

	/**
	 * Finds a tool by name across builtins and all agent scopes.
	 * Agent-scoped tools take precedence over builtins with the same name.
	 */
	public Optional<Tool> findTool(String name) {
		if (name == null) return Optional.empty();
		String key = name.toLowerCase();
		// Check agent scopes first (custom tools override builtins)
		for (AgentToolScope scope : agentScopes.values()) {
			Tool tool = scope.tools.get(key);
			if (tool != null) return Optional.of(tool);
		}
		return Optional.ofNullable(builtinTools.get(key));
	}

	// ── Agent-scoped operations ──

	/**
	 * Load tools from declared {@link ToolSource} entries into the given agent's
	 * scope. Previously loaded tools from the same sources are replaced; previously
	 * opened classloaders are closed to prevent leaks.
	 */
	public void loadToolSources(String agentName, List<ToolSource> sources) {
		if (sources == null || sources.isEmpty()) return;
		AgentToolScope scope = agentScopes.computeIfAbsent(agentName, k -> {
			var s = new AgentToolScope();
			// Seed agent scope with builtin copies so per-agent isolation includes builtins
			builtinTools.forEach((name, tool) -> s.tools.put(name, tool));
			return s;
		});

		for (ToolSource source : sources) {
			if (source == null || source.path() == null || source.path().isBlank()) continue;
			if ("CONFIGMAP".equalsIgnoreCase(source.type()) || "FOLDER".equalsIgnoreCase(source.type())
					|| "JAR".equalsIgnoreCase(source.type())) {
				// Evict previously loaded providers for this source path
				List<ToolJarLoader.LoadedProvider> old = scope.providers.remove(source.path());
				if (old != null) {
					old.forEach(lp -> scope.tools.values().removeIf(t -> t.name().equals(lp.provider().providerName())));
					closeClassLoaders(old.stream().map(ToolJarLoader.LoadedProvider::classLoader).toList());
				}

				List<ToolJarLoader.LoadedProvider> found = ToolJarLoader.loadFromFolder(Paths.get(source.path()));
				scope.providers.put(source.path(), found);
				scope.classLoaders.addAll(found.stream().map(ToolJarLoader.LoadedProvider::classLoader).toList());
				found.forEach(lp -> {
					for (Tool tool : lp.provider().provideTools()) {
						scope.tools.put(tool.name().toLowerCase(), tool);
						log.info("Agent [{}] registered tool: {} [{}]", agentName, tool.name(), tool.description());
					}
				});
			}
		}
	}

	/**
	 * Registers a tool into the given agent's scope.
	 * @deprecated prefer {@link #loadToolSources(String, List)} for agent-scoped loading;
	 * kept for test compatibility and direct registration use cases.
	 */
	@Deprecated
	public void register(Tool tool) {
		builtinTools.put(tool.name().toLowerCase(), tool);
		log.info("Registered tool: {} [{}]", tool.name(), tool.description());
	}

	/**
	 * Register all tools from a provider into the global builtin scope.
	 * @deprecated prefer {@link #loadToolSources(String, List)} for agent-scoped loading.
	 */
	@Deprecated
	public void registerProvider(ToolProvider provider) {
		for (Tool tool : provider.provideTools()) {
			register(tool);
		}
		log.info("Registered tool provider [{}] with {} tool(s)", provider.providerName(),
				provider.provideTools().size());
	}

	/**
	 * Returns unmodifiable view of loaded JAR providers for the given agent,
	 * mapped by source path.
	 */
	public Map<String, List<ToolJarLoader.LoadedProvider>> getLoadedProviders(String agentName) {
		AgentToolScope scope = agentScopes.get(agentName);
		if (scope == null) return Map.of();
		return Collections.unmodifiableMap(scope.providers);
	}

	/**
	 * Executes requested active tools concurrently on Virtual Threads, using only
	 * the given agent's tool scope.
	 */
	public Map<String, ToolResult> executeActiveTools(String agentName, List<ToolDefinition> toolDefs,
			String userInput) {
		if (toolDefs == null || toolDefs.isEmpty()) return Map.of();

		List<ToolDefinition> activeDefs = toolDefs.stream()
				.filter(def -> Boolean.TRUE.equals(def.enabled())).toList();
		if (activeDefs.isEmpty()) return Map.of();

		AgentToolScope scope = agentScopes.get(agentName);
		Map<String, Tool> toolMap = scope != null ? scope.tools : builtinTools;

		Map<String, ToolResult> results = new ConcurrentHashMap<>();
		try {
			var futures = activeDefs.stream().map(def -> virtualThreadExecutor.submit(() -> {
				Tool tool = Optional.ofNullable(toolMap.get(def.name().toLowerCase()))
						.orElseGet(() -> new CustomTool(def.name(), def.description()));
				return tool.execute(userInput, def.parameters());
			})).toList();

			for (var f : futures) {
				ToolResult res = f.get();
				results.put(res.toolName(), res);
			}
		} catch (Exception e) {
			log.error("Error executing active tools concurrently for agent [{}]", agentName, e);
		}
		return results;
	}

	/**
	 * Evicts an agent's tool scope, closing all URLClassLoaders and removing the
	 * scope. Called when an AiAgent CR is deleted or its toolSources change.
	 */
	public void evictAgent(String agentName) {
		AgentToolScope scope = agentScopes.remove(agentName);
		if (scope != null) {
			closeClassLoaders(scope.classLoaders);
			scope.classLoaders.clear();
			scope.tools.clear();
			scope.providers.clear();
			log.info("Evicted tool scope for agent [{}]", agentName);
		}
	}

	private static void closeClassLoaders(List<? extends URLClassLoader> loaders) {
		for (var loader : loaders) {
			try {
				loader.close();
			} catch (Exception ignored) {
				// best-effort cleanup
			}
		}
	}

	@PreDestroy
	public void shutdown() {
		agentScopes.keySet().forEach(this::evictAgent);
		virtualThreadExecutor.shutdown();
	}

	// ── Inner types ──

	/**
	 * Per-agent tool scope holding the agent's registered tools, loaded JAR
	 * providers, and open URLClassLoader instances.
	 */
	static final class AgentToolScope {
		final Map<String, Tool> tools = new ConcurrentHashMap<>();
		final Map<String, List<ToolJarLoader.LoadedProvider>> providers = new ConcurrentHashMap<>();
		final List<URLClassLoader> classLoaders = new CopyOnWriteArrayList<>();
	}

	/** Compiled-in tools (Calculator, Web Search, Weather). */
	public static final class BuiltinToolProvider implements ToolProvider {
		@Override
		public String providerName() { return "builtin"; }

		@Override
		public List<Tool> provideTools() {
			return List.of(new CalculatorTool(), new WebSearchTool(), new WeatherTool());
		}
	}
}
