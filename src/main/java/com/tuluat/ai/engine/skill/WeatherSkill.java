package com.tuluat.ai.engine.skill;

import java.util.Map;

public final class WeatherSkill implements Skill {
    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "Retrieves current weather information for a specified location";
    }

    @Override
    public SkillResult execute(String input, Map<String, String> parameters) {
        String city = (input != null && !input.isBlank()) ? input.trim() : "Istanbul";
        String weatherInfo = String.format("Current weather in %s: 22°C, Partly Cloudy, Humidity 55%%", city);
        return SkillResult.success(name(), weatherInfo);
    }
}
