package com.tuluat.engine.embabel;

import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.agent.UsageStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmbabelAgentRunnerTest {

    private AgentExecutionService agentExecutionService;
    private EmbabelAgentRunner runner;

    @BeforeEach
    void setUp() {
        agentExecutionService = mock(AgentExecutionService.class);
        EmbabelGoalEngine goalEngine = new EmbabelGoalEngine(agentExecutionService);
        runner = new EmbabelAgentRunner(goalEngine);
    }

    @Test
    @DisplayName("Should execute goal using Embabel agent runner")
    void testExecuteGoal() {
        AgentResponse mockResponse = AgentResponse.create(
                "web-researcher-agent",
                "deepseek-chat",
                "DEEPSEEK",
                "Goal achieved: Research completed",
                List.of(),
                UsageStats.calculate(10, 10, "deepseek-chat", 100L)
        );

        when(agentExecutionService.executeAgent(eq("web-researcher-agent"), anyString(), any()))
                .thenReturn(mockResponse);

        AgentResponse response = runner.executeGoal("web-researcher-agent", "Research CRD Operators", Map.of("depth", "high"));

        assertNotNull(response);
        assertEquals("Goal achieved: Research completed", response.answer());
        verify(agentExecutionService, times(1)).executeAgent(eq("web-researcher-agent"), anyString(), any());
    }
}
