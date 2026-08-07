package com.tuluat.ai.engine.embabel;

import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmbabelAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbabelAgentRunner.class);
    private final AgentExecutionService agentExecutionService;

    public EmbabelAgentRunner(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    public AgentResponse executeGoal(String agentName, String goalDescription, Map<String, Object> goalContext) {
        log.info("Embabel Goal Runner: Planning goal '{}' for agent '{}'", goalDescription, agentName);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Goal: ").append(goalDescription).append("\n");
        promptBuilder.append("Context: ").append(goalContext.toString()).append("\n");
        promptBuilder.append("Formulate a step-by-step goal execution plan and produce final result.");

        return agentExecutionService.executeAgent(agentName, promptBuilder.toString(), null);
    }
}
