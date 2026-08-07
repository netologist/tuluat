package com.tuluat.engine.skill;

import com.tuluat.crd.agent.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service managing skill registry and execution using Java Virtual Threads and Streams.
 */
@Service
public class SkillRegistry {
    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);
    
    private final Map<String, Skill> registeredSkills = new ConcurrentHashMap<>();
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SkillRegistry() {
        // Register default skills
        register(new CalculatorSkill());
        register(new WebSearchSkill());
        register(new WeatherSkill());
    }

    public void register(Skill skill) {
        registeredSkills.put(skill.name().toLowerCase(), skill);
        log.info("Registered skill: {} [{}]", skill.name(), skill.description());
    }

    public Optional<Skill> findSkill(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(registeredSkills.get(name.toLowerCase()));
    }

    /**
     * Executes requested active skills concurrently on Virtual Threads and returns results mapped by skill name.
     */
    public Map<String, SkillResult> executeActiveSkills(List<SkillDefinition> skillDefs, String userInput) {
        if (skillDefs == null || skillDefs.isEmpty()) {
            return Map.of();
        }

        // Use Java Streams to filter enabled skills
        List<SkillDefinition> enabledDefs = skillDefs.stream()
            .filter(def -> Boolean.TRUE.equals(def.enabled()))
            .toList();

        Map<String, SkillResult> results = new ConcurrentHashMap<>();

        // Virtual Thread per task execution using modern Java concurrency
        try {
            var futures = enabledDefs.stream()
                .map(def -> virtualThreadExecutor.submit(() -> {
                    String name = def.name();
                    Skill skill = findSkill(name)
                        .orElseGet(() -> new CustomSkill(def.name(), def.description()));
                    log.info("Executing skill [{}] on Virtual Thread: {}", name, Thread.currentThread());
                    return skill.execute(userInput, def.parameters());
                }))
                .toList();

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
}
