package com.tuluat.ai.engine;

import com.tuluat.ai.crd.agent.SkillDefinition;
import com.tuluat.ai.engine.skill.CalculatorSkill;
import com.tuluat.ai.engine.skill.SkillRegistry;
import com.tuluat.ai.engine.skill.SkillResult;
import com.tuluat.ai.engine.skill.WeatherSkill;
import com.tuluat.ai.engine.skill.WebSearchSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    private SkillRegistry skillRegistry;

    @BeforeEach
    void setUp() {
        skillRegistry = new SkillRegistry();
    }

    @Test
    @DisplayName("Should register default skills and list available skill names")
    void testDefaultSkillsRegistration() {
        var skills = skillRegistry.getAvailableSkillNames();
        assertTrue(skills.contains("calculator"));
        assertTrue(skills.contains("web-search"));
        assertTrue(skills.contains("weather"));
    }

    @Test
    @DisplayName("Should execute enabled skills concurrently on Virtual Threads")
    void testExecuteActiveSkillsConcurrently() {
        var skillDefs = List.of(
            new SkillDefinition("calculator", "Math calculations", true, Map.of()),
            new SkillDefinition("weather", "Weather forecast", true, Map.of()),
            new SkillDefinition("web-search", "Search web", false, Map.of()) // Disabled
        );

        Map<String, SkillResult> results = skillRegistry.executeActiveSkills(skillDefs, "25 * 4 in Istanbul");

        assertEquals(2, results.size());
        assertTrue(results.containsKey("calculator"));
        assertTrue(results.containsKey("weather"));
        assertFalse(results.containsKey("web-search"));

        SkillResult calcRes = results.get("calculator");
        assertTrue(calcRes.success());
        assertTrue(calcRes.output().contains("100"));

        SkillResult weatherRes = results.get("weather");
        assertTrue(weatherRes.success());
        assertTrue(weatherRes.output().contains("Istanbul"));
    }

    @Test
    @DisplayName("Should execute calculator skill correctly with math expression")
    void testCalculatorSkillDirectExecution() {
        var calc = new CalculatorSkill();
        var res = calc.execute("100 / 4", Map.of());
        assertTrue(res.success());
        assertTrue(res.output().contains("25.00"));
    }
}
