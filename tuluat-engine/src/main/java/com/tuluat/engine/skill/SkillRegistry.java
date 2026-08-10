package com.tuluat.engine.skill;

import com.tuluat.crd.agent.SkillDefinition;
import com.tuluat.crd.agent.SkillSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service managing skill registry and execution using Java Virtual Threads and
 * Streams. Skills are contributed by {@link SkillProvider} implementations:
 * compiled-in (builtin provider) or external JARs loaded from
 * {@code skillSources} folders.
 */
@Service
public class SkillRegistry {
	private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

	private final Map<String, Skill> registeredSkills = new ConcurrentHashMap<>();
	private final Map<String, List<SkillJarLoader.LoadedProvider>> loadedProviders = new ConcurrentHashMap<>();
	private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

	public SkillRegistry() {
		// Register compiled-in skills via the builtin provider (ADR 007)
		registerProvider(new BuiltinSkillProvider());
	}

	/**
	 * Register all skills from a provider.
	 */
	public void registerProvider(SkillProvider provider) {
		for (Skill skill : provider.provideSkills()) {
			register(skill);
		}
		log.info("Registered skill provider [{}] with {} skill(s)", provider.providerName(),
				provider.provideSkills().size());
	}

	/**
	 * Load skills from declared {@link SkillSource} entries (FOLDER / JAR).
	 */
	public void loadSkillSources(List<SkillSource> sources) {
		if (sources == null || sources.isEmpty()) {
			return;
		}
		for (SkillSource source : sources) {
			if (source == null || source.path() == null || source.path().isBlank()) {
				continue;
			}
			if ("CONFIGMAP".equalsIgnoreCase(source.type()) || "FOLDER".equalsIgnoreCase(source.type())
					|| "JAR".equalsIgnoreCase(source.type())) {
				List<SkillJarLoader.LoadedProvider> found = SkillJarLoader.loadFromFolder(Paths.get(source.path()));
				loadedProviders.put(source.path(), found);
				found.forEach(lp -> registerProvider(lp.provider()));
			}
		}
	}

	public void register(Skill skill) {
		registeredSkills.put(skill.name().toLowerCase(), skill);
		log.info("Registered skill: {} [{}]", skill.name(), skill.description());
	}

	public Optional<Skill> findSkill(String name) {
		if (name == null)
			return Optional.empty();
		return Optional.ofNullable(registeredSkills.get(name.toLowerCase()));
	}

	/**
	 * Executes requested active skills concurrently on Virtual Threads and returns
	 * results mapped by skill name.
	 */
	public Map<String, SkillResult> executeActiveSkills(List<SkillDefinition> skillDefs, String userInput) {
		if (skillDefs == null || skillDefs.isEmpty()) {
			return Map.of();
		}

		// Use Java Streams to filter enabled skills
		List<SkillDefinition> enabledDefs = skillDefs.stream().filter(def -> Boolean.TRUE.equals(def.enabled()))
				.toList();

		Map<String, SkillResult> results = new ConcurrentHashMap<>();

		// Virtual Thread per task execution using modern Java concurrency
		try {
			var futures = enabledDefs.stream().map(def -> virtualThreadExecutor.submit(() -> {
				String name = def.name();
				Skill skill = findSkill(name).orElseGet(() -> new CustomSkill(def.name(), def.description()));
				log.info("Executing skill [{}] on Virtual Thread: {}", name, Thread.currentThread());
				return skill.execute(userInput, def.parameters());
			})).toList();

			for (var f : futures) {
				SkillResult res = f.get();
				results.put(res.skillName(), res);
			}
		} catch (Exception e) {
			log.error("Error executing skills concurrently on virtual threads", e);
		}

		return results;
	}

	public List<String> getAvailableSkillNames() {
		return registeredSkills.keySet().stream().sorted().toList();
	}

	/** Compiled-in skills (Calculator, Web Search, Weather). */
	public static final class BuiltinSkillProvider implements SkillProvider {
		@Override
		public String providerName() {
			return "builtin";
		}

		@Override
		public List<Skill> provideSkills() {
			return List.of(new CalculatorSkill(), new WebSearchSkill(), new WeatherSkill());
		}
	}
}
