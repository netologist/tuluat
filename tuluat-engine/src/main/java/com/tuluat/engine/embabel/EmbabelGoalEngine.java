package com.tuluat.engine.embabel;

import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EmbabelGoalEngine {

	private static final Logger log = LoggerFactory.getLogger(EmbabelGoalEngine.class);
	private final AgentExecutionService agentExecutionService;

	public EmbabelGoalEngine(AgentExecutionService agentExecutionService) {
		this.agentExecutionService = agentExecutionService;
	}

	public EmbabelBlackboard executeGoal(EmbabelGoal goal, List<EmbabelAction> availableActions,
			EmbabelBlackboard blackboard) {
		log.info("Embabel Engine: Initiating Goal '{}' (targetKey: {})", goal.getDescription(),
				goal.getTargetStateKey());

		int maxSteps = 10;
		int step = 0;

		while (!blackboard.has(goal.getTargetStateKey()) && step < maxSteps) {
			EmbabelAction nextAction = availableActions.stream()
					.filter(action -> action.requiredPreconditions().stream().allMatch(blackboard::has))
					.filter(action -> !blackboard.has(action.outputKey())).findFirst().orElse(null);

			if (nextAction == null) {
				log.warn("Embabel Engine: No eligible actions with satisfied preconditions found for goal '{}'",
						goal.getId());
				break;
			}

			log.info("Embabel Engine: Executing action '{}' using agent '{}'", nextAction.name(),
					nextAction.agentRef());
			String prompt = resolvePromptTemplate(nextAction.inputTemplate(), blackboard.getState());

			AgentResponse response = agentExecutionService.executeAgent(nextAction.agentRef(), prompt, null);
			blackboard.put(nextAction.outputKey(), response.answer());

			step++;
		}

		return blackboard;
	}

	private String resolvePromptTemplate(String template, Map<String, Object> state) {
		if (template == null)
			return "";
		String result = template;
		for (Map.Entry<String, Object> entry : state.entrySet()) {
			result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
		}
		return result;
	}
}
