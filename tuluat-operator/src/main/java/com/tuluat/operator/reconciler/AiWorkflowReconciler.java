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
