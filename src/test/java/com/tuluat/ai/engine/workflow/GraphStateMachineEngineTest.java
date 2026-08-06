package com.tuluat.ai.engine.workflow;

import com.tuluat.ai.crd.workflow.AiWorkflowSpec;
import com.tuluat.ai.crd.workflow.EdgeDefinition;
import com.tuluat.ai.crd.workflow.NodeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphStateMachineEngineTest {

    private GraphStateMachineEngine engine;

    @BeforeEach
    void setUp() {
        engine = new GraphStateMachineEngine(null);
    }

    @Test
    @DisplayName("Should evaluate condition node expression correctly using SpEL")
    void shouldEvaluateConditionNodeExpression() {
        NodeDefinition condNode = new NodeDefinition();
        condNode.setId("check-result");
        condNode.setType("CONDITION");
        condNode.setExpression("#data['status'] == 'OK'");

        Map<String, Object> context = new HashMap<>();
        context.put("status", "OK");

        boolean result = engine.evaluateCondition(condNode.getExpression(), context);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should find next node ID based on edge conditions")
    void shouldFindNextNode() {
        EdgeDefinition edge1 = new EdgeDefinition();
        edge1.setFrom("check-result");
        edge1.setTo("success-node");
        edge1.setCondition("true");

        EdgeDefinition edge2 = new EdgeDefinition();
        edge2.setFrom("check-result");
        edge2.setTo("retry-node");
        edge2.setCondition("false");

        AiWorkflowSpec spec = new AiWorkflowSpec();
        spec.setEdges(List.of(edge1, edge2));

        String nextNode = engine.resolveNextNodeId(spec, "check-result", true);
        assertEquals("success-node", nextNode);

        String failNode = engine.resolveNextNodeId(spec, "check-result", false);
        assertEquals("retry-node", failNode);
    }
}
