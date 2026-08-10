package com.tuluat.engine.embabel;

import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.agent.UsageStats;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EmbabelAgentRunner {

	private final EmbabelGoalEngine goalEngine;

	public EmbabelAgentRunner(EmbabelGoalEngine goalEngine) {
		this.goalEngine = goalEngine;
	}

	public AgentResponse executeGoal(String agentName, String goalDescription, Map<String, Object> goalContext) {
		EmbabelGoal goal = new EmbabelGoal("goal-1", goalDescription, "final_result");
		EmbabelAction action = new EmbabelAction("execute-goal-action", agentName,
				"Goal: " + goalDescription + "\nContext: {{context}}", "final_result", List.of());

		EmbabelBlackboard blackboard = new EmbabelBlackboard();
		blackboard.put("context", goalContext != null ? goalContext.toString() : "{}");

		blackboard = goalEngine.executeGoal(goal, List.of(action), blackboard);

		String answer = (String) blackboard.get("final_result");
		if (answer == null)
			answer = "Goal failed to produce final result";

		return AgentResponse.create(agentName, "embabel-model", "DEEPSEEK", answer, List.of(),
				UsageStats.calculate(10, 10, "embabel-model", 100L));
	}
}
