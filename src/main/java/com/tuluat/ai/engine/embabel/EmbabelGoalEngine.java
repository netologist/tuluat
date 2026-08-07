package com.tuluat.ai.engine.embabel;

import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
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

    public EmbabelBlackboard executeGoal(EmbabelGoal goal, List<EmbabelAction> availableActions, EmbabelBlackboard blackboard) {
        log.info("Embabel Engine: Initiating Goal '{}' (targetKey: {})", goal.getDescription(), goal.getTargetStateKey());

        int maxSteps = 10;
        int step = 0;

        while (!blackboard.has(goal.getTargetStateKey()) && step < maxSteps) {
            EmbabelAction nextAction = availableActions.stream()
                    .filter(action -> action.getRequiredPreconditions().stream().allMatch(blackboard::has))
                    .filter(action -> !blackboard.has(action.getOutputKey()))
                    .findFirst()
                    .orElse(null);

            if (nextAction == null) {
                log.warn("Embabel Engine: No eligible actions with satisfied preconditions found for goal '{}'", goal.getId());
                break;
            }

            log.info("Embabel Engine: Executing action '{}' using agent '{}'", nextAction.getName(), nextAction.getAgentRef());
            String prompt = resolvePromptTemplate(nextAction.getInputTemplate(), blackboard.getState());

            AgentResponse response = agentExecutionService.executeAgent(nextAction.getAgentRef(), prompt, null);
            blackboard.put(nextAction.getOutputKey(), response.answer());

            step++;
        }

        return blackboard;
    }

    private String resolvePromptTemplate(String template, Map<String, Object> state) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
