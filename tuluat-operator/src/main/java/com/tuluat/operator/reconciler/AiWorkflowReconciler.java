package com.tuluat.operator.reconciler;

import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowStatus;
import io.javaoperatorsdk.operator.api.reconciler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@ControllerConfiguration
@Component
public class AiWorkflowReconciler implements Reconciler<AiWorkflow> {

    private static final Logger log = LoggerFactory.getLogger(AiWorkflowReconciler.class);

    @Override
    public UpdateControl<AiWorkflow> reconcile(AiWorkflow resource, Context<AiWorkflow> context) {
        log.info("Reconciling AiWorkflow: {}", resource.getMetadata().getName());

        AiWorkflowStatus status = resource.getStatus();
        if (status == null) {
            status = new AiWorkflowStatus();
        }

        status.setState("Ready");
        status.setNodeCount(resource.getSpec() != null && resource.getSpec().getNodes() != null ? resource.getSpec().getNodes().size() : 0);
        resource.setStatus(status);

        return UpdateControl.patchStatus(resource);
    }
}
