package com.tuluat.operator.reconciler;

import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.NodeDefinition;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiWorkflowReconcilerTest {

    private final AiWorkflowReconciler reconciler = new AiWorkflowReconciler();

    @Test
    @DisplayName("Should set status to Ready with correct node count")
    void testReconcileSuccess() {
        var workflow = new AiWorkflow();
        workflow.setMetadata(new ObjectMetaBuilder().withName("research-wf").withNamespace("default").build());

        NodeDefinition n1 = new NodeDefinition();
        n1.setId("n1");
        NodeDefinition n2 = new NodeDefinition();
        n2.setId("n2");

        var spec = new AiWorkflowSpec();
        spec.setInitialNode("n1");
        spec.setNodes(List.of(n1, n2));
        workflow.setSpec(spec);

        UpdateControl<AiWorkflow> control = reconciler.reconcile(workflow, null);

        assertNotNull(control);
        assertNotNull(workflow.getStatus());
        assertEquals("Ready", workflow.getStatus().getState());
        assertEquals(2, workflow.getStatus().getNodeCount());
    }
}
