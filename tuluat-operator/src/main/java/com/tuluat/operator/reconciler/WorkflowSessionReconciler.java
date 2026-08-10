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
			status = WorkflowSessionStatus.pending();
			resource.setStatus(status);
		}

		if ("PENDING".equalsIgnoreCase(status.phase()) || status.phase() == null) {
			String workflowName = resource.getSpec().workflowRef();
			AiWorkflow workflow = kubernetesClient.resources(AiWorkflow.class)
					.inNamespace(resource.getMetadata().getNamespace()).withName(workflowName).get();

			if (workflow != null) {
			WorkflowSessionEntity entity = executionService.startSession(workflowName, workflow.getSpec(),
					resource.getSpec().input(), 10);

			status = new WorkflowSessionStatus(entity.getSessionId().toString(), entity.getStatus(),
					entity.getCurrentNodeId(), null, null, null);
			resource.setStatus(status);
			return UpdateControl.patchStatus(resource);
			}
		}

		return UpdateControl.noUpdate();
	}
}
