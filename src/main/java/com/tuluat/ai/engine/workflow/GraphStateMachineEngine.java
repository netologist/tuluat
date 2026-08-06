package com.tuluat.ai.engine.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuluat.ai.crd.workflow.AiWorkflowSpec;
import com.tuluat.ai.crd.workflow.EdgeDefinition;
import com.tuluat.ai.crd.workflow.NodeDefinition;
import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
import com.tuluat.ai.entity.WorkflowSessionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

@Component
public class GraphStateMachineEngine {

    private static final Logger log = LoggerFactory.getLogger(GraphStateMachineEngine.class);
    private final AgentExecutionService agentExecutionService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper mapper = new ObjectMapper();

    public GraphStateMachineEngine(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    public WorkflowSessionEntity executeNextStep(AiWorkflowSpec workflowSpec, WorkflowSessionEntity session, int maxLoops) {
        if (session.getLoopCount() >= maxLoops) {
            log.error("Session {} exceeded max loops ({})", session.getSessionId(), maxLoops);
            session.setStatus("FAILED");
            session.setUpdatedAt(OffsetDateTime.now());
            return session;
        }

        String currentNodeId = session.getCurrentNodeId();
        if (currentNodeId == null || currentNodeId.isEmpty()) {
            currentNodeId = workflowSpec.getInitialNode();
            session.setCurrentNodeId(currentNodeId);
        }

        final String targetId = currentNodeId;
        NodeDefinition currentNode = workflowSpec.getNodes().stream()
                .filter(n -> n.getId().equals(targetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + session.getCurrentNodeId()));

        Map<String, Object> contextData = parseContext(session.getContextData());

        log.info("Executing session {} node {} (type: {})", session.getSessionId(), currentNode.getId(), currentNode.getType());

        if ("AGENT".equalsIgnoreCase(currentNode.getType())) {
            String prompt = resolvePromptTemplate(currentNode.getInputTemplate(), contextData);
            AgentResponse response = agentExecutionService.executeAgent(currentNode.getAgentRef(), prompt, null);
            contextData.put(currentNode.getOutputKey(), response.answer());
            session.setContextData(writeContext(contextData));

            String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.getId(), true);
            if (nextNodeId == null) {
                session.setStatus("COMPLETED");
            } else {
                session.setCurrentNodeId(nextNodeId);
            }
        } else if ("CONDITION".equalsIgnoreCase(currentNode.getType())) {
            boolean result = evaluateCondition(currentNode.getExpression(), contextData);
            String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.getId(), result);
            if (nextNodeId == null) {
                session.setStatus("COMPLETED");
            } else {
                session.setCurrentNodeId(nextNodeId);
            }
        }

        session.setLoopCount(session.getLoopCount() + 1);
        session.setUpdatedAt(OffsetDateTime.now());
        return session;
    }

    public boolean evaluateCondition(String expression, Map<String, Object> contextData) {
        if (expression == null || expression.isBlank()) return true;
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariable("data", contextData);
        Boolean result = parser.parseExpression(expression).getValue(evalContext, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public String resolveNextNodeId(AiWorkflowSpec spec, String fromNodeId, boolean conditionResult) {
        return spec.getEdges().stream()
                .filter(e -> e.getFrom().equals(fromNodeId))
                .filter(e -> e.getCondition() == null || e.getCondition().isEmpty() || Boolean.parseBoolean(e.getCondition()) == conditionResult)
                .map(EdgeDefinition::getTo)
                .findFirst()
                .orElse(null);
    }

    private String resolvePromptTemplate(String template, Map<String, Object> contextData) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : contextData.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContext(String json) {
        try {
            return json == null || json.isEmpty() ? new HashMap<>() : mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeContext(Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
