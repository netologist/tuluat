package com.tuluat.operator.reconciler;

import com.tuluat.crd.session.WorkflowSession;
import com.tuluat.crd.session.WorkflowSessionStatus;
import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@ControllerConfiguration
@Component
public class WorkflowSessionReconciler implements Reconciler<WorkflowSession> {

	private static final Logger log = LoggerFactory.getLogger(WorkflowSessionReconciler.class);
	private final WorkflowExecutionService executionService;
	private final KubernetesClient kubernetesClient;

	public WorkflowSessionReconciler(WorkflowExecutionService executionService, KubernetesClient kubernetesClient) {
		this.executionService = executionService;
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public UpdateControl<WorkflowSession> reconcile(WorkflowSession resource, Context<WorkflowSession> context) {
		log.info("Reconciling WorkflowSession: {}", resource.getMetadata().getName());

		WorkflowSessionStatus status = resource.getStatus();
		if (status == null) {
			status = new WorkflowSessionStatus();
			resource.setStatus(status);
		}

		if ("PENDING".equalsIgnoreCase(status.getPhase()) || status.getPhase() == null) {
			String workflowName = resource.getSpec().getWorkflowRef();
			AiWorkflow workflow = kubernetesClient.resources(AiWorkflow.class)
					.inNamespace(resource.getMetadata().getNamespace()).withName(workflowName).get();

			if (workflow != null) {
				WorkflowSessionEntity entity = executionService.startSession(workflowName, workflow.getSpec(),
						resource.getSpec().getInput(), 10);

				status.setSessionId(entity.getSessionId().toString());
				status.setPhase(entity.getStatus());
				status.setCurrentNode(entity.getCurrentNodeId());
				return UpdateControl.patchStatus(resource);
			}
		}

		return UpdateControl.noUpdate();
	}
}
