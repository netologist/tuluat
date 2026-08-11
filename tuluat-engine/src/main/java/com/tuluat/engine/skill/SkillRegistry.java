package com.tuluat.engine.skill;

import com.tuluat.crd.agent.SkillSource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry managing Markdown-based {@link AgentSkill} specifications
 * compliant with <a href="https://agentskills.io">Agent Skills</a>.
 *
 * <p>
 * Agent Skills provide instructions, guidelines, and prompt context to agents.
 */
@Service
@Slf4j
public class SkillRegistry {

	private final Map<String, AgentSkill> registeredSkills = new ConcurrentHashMap<>();

	public void registerSkill(AgentSkill skill) {
		if (skill == null || skill.name() == null) return;
		registeredSkills.put(skill.name().toLowerCase(), skill);
		log.info("Registered Agent Skill [{}] ({})", skill.name(), skill.description());
	}

	public Optional<AgentSkill> findSkill(String name) {
		if (name == null) return Optional.empty();
		return Optional.ofNullable(registeredSkills.get(name.toLowerCase()));
	}

	public Optional<AgentSkill> loadSkillFromPath(Path path) {
		Optional<AgentSkill> skill = AgentSkillLoader.loadFromPath(path);
		skill.ifPresent(this::registerSkill);
		return skill;
	}

	public List<AgentSkill> loadSkillsFromFolder(Path folder) {
		List<AgentSkill> skills = AgentSkillLoader.loadFromFolder(folder);
		skills.forEach(this::registerSkill);
		return skills;
	}

	public void loadSkillSources(List<SkillSource> sources) {
		if (sources == null || sources.isEmpty()) return;

		for (SkillSource source : sources) {
			if (source == null || source.path() == null || source.path().isBlank()) continue;
			Path path = Paths.get(source.path());
			loadSkillsFromFolder(path);
		}
	}

	public List<String> getAvailableSkillNames() {
		return registeredSkills.keySet().stream().sorted().toList();
	}

	public Map<String, AgentSkill> getRegisteredSkills() {
		return Collections.unmodifiableMap(registeredSkills);
	}
}
