package com.tuluat.engine.skill;

import java.util.Map;

/**
 * Contract for AI Skills / Tools. Non-sealed so external JARs can contribute
 * implementations via {@link SkillProvider} (ADR 007: skill provisioning vs binding).
 */
public interface Skill {
    String name();
    String description();
    SkillResult execute(String input, Map<String, String> parameters);
}
