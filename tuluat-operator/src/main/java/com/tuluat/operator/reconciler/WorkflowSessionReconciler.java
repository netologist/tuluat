package com.tuluat.operator.reconciler;

import com.tuluat.crd.session.NodeExecution;
import com.tuluat.crd.session.WorkflowSession;
import com.tuluat.crd.session.WorkflowSessionStatus;
import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.engine.entity.NodeExecutionEntity;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.NodeExecutionRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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

	public WorkflowSessionReconciler(WorkflowExecutionService executionService, KubernetesClient kubernetesClient,
			Optional<WorkflowSessionRepository> sessionRepository,
			Optional<NodeExecutionRepository> nodeExecutionRepository) {
		this.executionService = executionService;
		this.kubernetesClient = kubernetesClient;
		this.sessionRepository = Optional.ofNullable(sessionRepository).flatMap(o -> o);
		this.nodeExecutionRepository = Optional.ofNullable(nodeExecutionRepository).flatMap(o -> o);
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

				status = new WorkflowSessionStatus(entity.getSessionId().toString(), entity.getStatus().name(),
						entity.getCurrentNodeId(), null, null, null, 0L, 0L, 0L, BigDecimal.ZERO, 0L, List.of());
				resource.setStatus(status);
				return UpdateControl.patchStatus(resource);
			}
		}

		// For non-PENDING sessions, populate status with real execution data from DB
		if (status.sessionId() != null && !status.sessionId().isBlank()) {
			status = populateFromDatabase(status);
			resource.setStatus(status);
			return UpdateControl.patchStatus(resource);
		}

		return UpdateControl.noUpdate();
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
						e.getTotalTokens(), e.getInputTokens(), e.getOutputTokens(), e.getCostUsd(), e.getStatus()))
				.toList();

		// Aggregate totals
		long totalTokens = executions.stream().mapToLong(NodeExecutionEntity::getTotalTokens).sum();
		long inputTokens = executions.stream().mapToLong(NodeExecutionEntity::getInputTokens).sum();
		long outputTokens = executions.stream().mapToLong(NodeExecutionEntity::getOutputTokens).sum();
		BigDecimal costUsd = executions.stream().map(NodeExecutionEntity::getCostUsd).filter(c -> c != null)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// Duration from first start to last end
		long durationSecs = 0;
		if (!executions.isEmpty()) {
			var first = executions.get(0).getStartTime();
			var last = executions.get(executions.size() - 1).getEndTime();
			if (first != null && last != null) {
				durationSecs = Duration.between(first, last).getSeconds();
			}
		}

		// Read the session entity to get the latest phase
		UUID sessionId = UUID.fromString(current.sessionId());
		String phase = sessionRepository.flatMap(repo -> repo.findById(sessionId)).map(e -> e.getStatus().name())
				.orElse(current.phase());

		return new WorkflowSessionStatus(current.sessionId(), phase, current.currentNode(), current.output(),
				current.startTime(), current.endTime(), totalTokens, inputTokens, outputTokens, costUsd, durationSecs,
				nodeExecs);
	}
}
