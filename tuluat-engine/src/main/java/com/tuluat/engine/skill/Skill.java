package com.tuluat.engine.skill;

import java.util.Map;

/**
 * Sealed interface for AI Skills, demonstrating Java sealed types.
 */
public sealed interface Skill permits CalculatorSkill, WebSearchSkill, WeatherSkill, CustomSkill {
    String name();
    String description();
    SkillResult execute(String input, Map<String, String> parameters);
}
