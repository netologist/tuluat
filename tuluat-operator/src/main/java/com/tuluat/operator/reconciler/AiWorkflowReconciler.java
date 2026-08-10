package com.tuluat.operator.reconciler;

import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowStatus;
import io.javaoperatorsdk.operator.api.reconciler.*;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@ControllerConfiguration
@Component
@Slf4j
public class AiWorkflowReconciler implements Reconciler<AiWorkflow> {
@Override
	public UpdateControl<AiWorkflow> reconcile(AiWorkflow resource, Context<AiWorkflow> context) {
		log.info("Reconciling AiWorkflow: {}", resource.getMetadata().getName());

		AiWorkflowStatus status = resource.getStatus();
		if (status == null) {
			status = new AiWorkflowStatus("Ready", 0);
		}

		int nodeCount = resource.getSpec() != null && resource.getSpec().nodes() != null
				? resource.getSpec().nodes().size()
				: 0;
		status = new AiWorkflowStatus("Ready", nodeCount);
		resource.setStatus(status);

		return UpdateControl.patchStatus(resource);
	}
}
