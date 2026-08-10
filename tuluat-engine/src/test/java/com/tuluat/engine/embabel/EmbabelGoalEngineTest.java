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

class EmbabelGoalEngineTest {

	private AgentExecutionService agentExecutionService;
	private EmbabelGoalEngine goalEngine;

	@BeforeEach
	void setUp() {
		agentExecutionService = mock(AgentExecutionService.class);
		goalEngine = new EmbabelGoalEngine(agentExecutionService);
	}

	@Test
	@DisplayName("Should sequence actions based on preconditions and achieve goal state")
	void testExecuteGoalWithActions() {
		EmbabelGoal goal = new EmbabelGoal("research-goal", "Research and Report", "final_report");

		EmbabelAction action1 = new EmbabelAction("research", "web-researcher-agent", "Research {{input}}",
				"research_data", List.of());
		EmbabelAction action2 = new EmbabelAction("report", "report-writer-agent", "Report {{research_data}}",
				"final_report", List.of("research_data"));

		when(agentExecutionService.executeAgent(eq("web-researcher-agent"), anyString(), any()))
				.thenReturn(AgentResponse.create("web-researcher-agent", "model", "OPENAI", "Raw Findings", List.of(),
						UsageStats.calculate(5, 5, "model", 50L)));

		when(agentExecutionService.executeAgent(eq("report-writer-agent"), anyString(), any()))
				.thenReturn(AgentResponse.create("report-writer-agent", "model", "OPENAI", "Formatted Executive Report",
						List.of(), UsageStats.calculate(5, 5, "model", 50L)));

		EmbabelBlackboard blackboard = new EmbabelBlackboard(Map.of("input", "Kubernetes AI"));
		blackboard = goalEngine.executeGoal(goal, List.of(action1, action2), blackboard);

		assertTrue(blackboard.has("research_data"));
		assertTrue(blackboard.has("final_report"));
		assertEquals("Formatted Executive Report", blackboard.get("final_report"));

		verify(agentExecutionService, times(2)).executeAgent(anyString(), anyString(), any());
	}
}
