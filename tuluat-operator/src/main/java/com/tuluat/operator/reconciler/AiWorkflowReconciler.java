package com.tuluat.operator.reconciler;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.AiWorkflowStatus;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.entity.NodeExecutionEntity;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.NodeExecutionRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@ControllerConfiguration
@Component
@Slf4j
public class AiWorkflowReconciler implements Reconciler<AiWorkflow> {
	private final WorkflowSessionRepository sessionRepository;
	private final NodeExecutionRepository nodeExecutionRepository;

	public AiWorkflowReconciler(WorkflowSessionRepository sessionRepository,
			NodeExecutionRepository nodeExecutionRepository) {
		this.sessionRepository = sessionRepository;
		this.nodeExecutionRepository = nodeExecutionRepository;
	}

	@Override
	public UpdateControl<AiWorkflow> reconcile(AiWorkflow resource, Context<AiWorkflow> context) {
		String workflowName = resource.getMetadata().getName();
		AiWorkflowSpec spec = resource.getSpec();

		int nodeCount = spec != null && spec.nodes() != null ? spec.nodes().size() : 0;
		BigDecimal budget = spec != null && spec.budgetLimitUsd() != null ? spec.budgetLimitUsd() : BigDecimal.ZERO;

		List<WorkflowSessionEntity> sessions = sessionRepository.findByWorkflowName(workflowName);
		int sessionCount = sessions.size();

		long totalTokens = 0;
		long inputTokens = 0;
		long outputTokens = 0;
		BigDecimal costSpent = BigDecimal.ZERO;
		for (WorkflowSessionEntity session : sessions) {
			for (NodeExecutionEntity e : nodeExecutionRepository
					.findBySessionIdOrderByStartTimeAsc(session.getSessionId())) {
				totalTokens += e.getTotalTokens();
				inputTokens += e.getInputTokens();
				outputTokens += e.getOutputTokens();
				if (e.getCostUsd() != null) {
					costSpent = costSpent.add(e.getCostUsd());
				}
			}
		}

		List<String> agentNames = spec != null && spec.nodes() != null
				? spec.nodes().stream().map(NodeDefinition::agentRef).filter(Objects::nonNull)
						.filter(n -> !n.isBlank()).distinct().toList()
				: List.of();

		resource.setStatus(new AiWorkflowStatus("Ready", nodeCount, costSpent, budget, sessionCount, totalTokens,
				inputTokens, outputTokens, agentNames));
		return UpdateControl.patchStatus(resource);
	}
}
