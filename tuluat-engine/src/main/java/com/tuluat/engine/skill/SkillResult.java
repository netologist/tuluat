package com.tuluat.engine.skill;

import java.util.Map;

/**
 * Record representing the outcome of executing a skill/tool.
 */
public record SkillResult(
    String skillName,
    boolean success,
    String output,
    Map<String, Object> metadata
) {
    public static SkillResult success(String skillName, String output) {
        return new SkillResult(skillName, true, output, Map.of("timestamp", System.currentTimeMillis()));
    }

    public static SkillResult failure(String skillName, String error) {
        return new SkillResult(skillName, false, error, Map.of("timestamp", System.currentTimeMillis()));
    }
}
