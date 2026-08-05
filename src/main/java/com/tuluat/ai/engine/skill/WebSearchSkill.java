package com.tuluat.ai.engine.skill;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WebSearchSkill implements Skill {
    @Override
    public String name() {
        return "web-search";
    }

    @Override
    public String description() {
        return "Searches the web for up-to-date real-time factual information";
    }

    @Override
    public SkillResult execute(String input, Map<String, String> parameters) {
        if (input == null || input.isBlank()) {
            return SkillResult.failure(name(), "Search query cannot be empty");
        }
        var mockResults = List.of(
            "Search Result 1 for '" + input + "': Latest technical specifications and updates.",
            "Search Result 2 for '" + input + "': Community discussions and insights.",
            "Search Result 3 for '" + input + "': Official documentation and references."
        );
        String formatted = mockResults.stream()
            .map(res -> "- " + res)
            .collect(Collectors.joining("\n"));
        return SkillResult.success(name(), "Search Results for '" + input + "':\n" + formatted);
    }
}
