package com.example.ai.engine.skill;

import java.util.Map;
import java.util.stream.Collectors;

public final class CustomSkill implements Skill {
    private final String skillName;
    private final String skillDescription;

    public CustomSkill(String skillName, String skillDescription) {
        this.skillName = (skillName != null && !skillName.isBlank()) ? skillName : "custom";
        this.skillDescription = (skillDescription != null) ? skillDescription : "Custom skill execution";
    }

    @Override
    public String name() {
        return skillName;
    }

    @Override
    public String description() {
        return skillDescription;
    }

    @Override
    public SkillResult execute(String input, Map<String, String> parameters) {
        String paramsStr = parameters.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
        String result = String.format("Executed Custom Skill [%s] with input: '%s', params: [%s]", 
            skillName, input, paramsStr);
        return SkillResult.success(skillName, result);
    }
}
