package com.tuluat.operator.reconciler;

import com.tuluat.crd.session.NodeExecution;
import com.tuluat.crd.session.WorkflowSession;
import com.tuluat.crd.session.WorkflowSessionStatus;
import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.engine.entity.NodeExecutionEntity;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.NodeExecutionRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import com.tuluat.operator.event.KubernetesEventRecorder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ControllerConfiguration
@Component
@Slf4j
public class WorkflowSessionReconciler implements Reconciler<WorkflowSession> {
	private final WorkflowExecutionService executionService;
	private final KubernetesClient kubernetesClient;
	private final Optional<WorkflowSessionRepository> sessionRepository;
	private final Optional<NodeExecutionRepository> nodeExecutionRepository;
	private final KubernetesEventRecorder eventRecorder;

	public WorkflowSessionReconciler(WorkflowExecutionService executionService, KubernetesClient kubernetesClient,
			Optional<WorkflowSessionRepository> sessionRepository,
			Optional<NodeExecutionRepository> nodeExecutionRepository, KubernetesEventRecorder eventRecorder) {
		this.executionService = executionService;
		this.kubernetesClient = kubernetesClient;
		this.sessionRepository = Optional.ofNullable(sessionRepository).flatMap(o -> o);
		this.nodeExecutionRepository = Optional.ofNullable(nodeExecutionRepository).flatMap(o -> o);
		this.eventRecorder = eventRecorder;
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

			if (workflow == null) {
				eventRecorder.record(resource, KubernetesEventRecorder.TYPE_WARNING, "WorkflowSessionWorkflowNotFound",
						"Referenced workflow '" + workflowName + "' not found; session stays pending");
				return UpdateControl.noUpdate();
			}
			eventRecorder.record(resource, KubernetesEventRecorder.TYPE_NORMAL, "WorkflowSessionStarted",
					"Starting execution of workflow '" + workflowName + "'");
			int maxLoops = resolveMaxLoops(resource.getSpec().parameters());
			WorkflowSessionEntity entity = executionService.startSession(workflowName, workflow.getSpec(),
					resource.getSpec().input(), maxLoops);

			status = new WorkflowSessionStatus(entity.getSessionId().toString(), entity.getStatus().name(),
					entity.getCurrentNodeId(), null, null, null, 0L, 0L, 0L, "0", 0L, List.of());
			status = populateFromDatabase(status);
			resource.setStatus(status);
			recordCompletionEvent(resource, entity);
			return UpdateControl.patchStatus(resource);
		}

		// For non-PENDING sessions, populate status with real execution data from DB
		if (status.sessionId() != null && !status.sessionId().isBlank()) {
			status = populateFromDatabase(status);
			resource.setStatus(status);
			return UpdateControl.patchStatus(resource);
		}

		return UpdateControl.noUpdate();
	}

	private void recordCompletionEvent(WorkflowSession resource, WorkflowSessionEntity entity) {
		String workflowName = resource.getSpec().workflowRef();
		String type;
		String reason;
		String message;
		switch (entity.getStatus()) {
			case COMPLETED -> {
				type = KubernetesEventRecorder.TYPE_NORMAL;
				reason = "WorkflowSessionCompleted";
				message = "Workflow '" + workflowName + "' completed successfully";
			}
			case FAILED -> {
				type = KubernetesEventRecorder.TYPE_WARNING;
				reason = "WorkflowSessionFailed";
				message = "Workflow '" + workflowName + "' failed";
			}
			case WAITING_APPROVAL -> {
				type = KubernetesEventRecorder.TYPE_NORMAL;
				reason = "WorkflowSessionWaitingApproval";
				message = "Workflow '" + workflowName + "' is awaiting human approval";
			}
			case REJECTED -> {
				type = KubernetesEventRecorder.TYPE_WARNING;
				reason = "WorkflowSessionRejected";
				message = "Workflow '" + workflowName + "' was rejected";
			}
			default -> {
				type = KubernetesEventRecorder.TYPE_NORMAL;
				reason = "WorkflowSessionUpdated";
				message = "Workflow '" + workflowName + "' phase: " + entity.getStatus();
			}
		}
		eventRecorder.record(resource, type, reason, message);
	}

	private static int resolveMaxLoops(Map<String, Object> parameters) {
		if (parameters != null && parameters.get("maxLoops") instanceof Number number) {
			return Math.max(1, number.intValue());
		}
		return 10;
	}

	private WorkflowSessionStatus populateFromDatabase(WorkflowSessionStatus current) {
		var executions = nodeExecutionRepository
				.map(repo -> repo.findBySessionIdOrderByStartTimeAsc(UUID.fromString(current.sessionId())))
				.orElse(List.of());

		// Map entities to CRD records
		List<NodeExecution> nodeExecs = executions.stream()
				.map(e -> new NodeExecution(e.getNodeId(), e.getAgentName(), e.getProvider(), e.getModel(),
						e.getInputPrompt(), e.getOutputText(),
						e.getStartTime() != null ? e.getStartTime().toString() : null,
						e.getEndTime() != null ? e.getEndTime().toString() : null, e.getDurationMs(),
						e.getTotalTokens(), e.getInputTokens(), e.getOutputTokens(),
						e.getCostUsd() != null ? e.getCostUsd().toPlainString() : "0", e.getStatus()))
				.toList();
		// Aggregate totals
		long totalTokens = executions.stream().mapToLong(NodeExecutionEntity::getTotalTokens).sum();
		long inputTokens = executions.stream().mapToLong(NodeExecutionEntity::getInputTokens).sum();
		long outputTokens = executions.stream().mapToLong(NodeExecutionEntity::getOutputTokens).sum();
		BigDecimal costUsd = executions.stream().map(NodeExecutionEntity::getCostUsd).filter(c -> c != null)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// Duration from first start to last end
		long durationSecs = 0;
		String startTimeStr = current.startTime();
		String endTimeStr = current.endTime();
		if (!executions.isEmpty()) {
			var first = executions.get(0).getStartTime();
			var last = executions.get(executions.size() - 1).getEndTime();
			if (first != null) {
				startTimeStr = first.toString();
			}
			if (last != null) {
				endTimeStr = last.toString();
			}
			if (first != null && last != null) {
				durationSecs = Duration.between(first, last).getSeconds();
			}
		}

		// Read the session entity to get the latest phase
		UUID sessionId = UUID.fromString(current.sessionId());
		String phase = sessionRepository.flatMap(repo -> repo.findById(sessionId)).map(e -> e.getStatus().name())
				.orElse(current.phase());
		return new WorkflowSessionStatus(current.sessionId(), phase, current.currentNode(), current.output(),
				startTimeStr, endTimeStr, totalTokens, inputTokens, outputTokens,
				costUsd.toPlainString(), durationSecs, nodeExecs);
	}
}
