package com.tuluat.app;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.workflow.GraphStateMachineEngine;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Regression test for connection-pool exhaustion ("FATAL: sorry, too many
 * clients already").
 *
 * <p>
 * The bug: {@link WorkflowExecutionService#startSession} was annotated
 * {@code @Transactional}, so a single HikariCP connection was held for the
 * entire synchronous workflow execution — including slow LLM HTTP calls inside
 * {@link GraphStateMachineEngine#executeNextStep}. With concurrent sessions
 * this exhausted the pool.
 * </p>
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
		"spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=update", "AGENT_NAME=test-agent", "embabel.models.default-llm=deepseek-chat",
		"embabel.agent.platform.models.openai.custom.api-key=sk-test-key",
		"embabel.agent.platform.models.openai.custom.base-url=https://api.deepseek.com",
		"embabel.agent.platform.models.openai.custom.models=deepseek-chat", "spring.ai.openai.api-key=sk-test-key",
		"spring.ai.openai.base-url=https://api.deepseek.com"})
class WorkflowExecutionServiceTransactionTest {

	private final WorkflowExecutionService executionService;

	@MockitoBean
	private KubernetesClient kubernetesClient;

	@MockitoBean
	private WorkflowServiceStubs workflowServiceStubs;

	@MockitoBean
	private WorkflowClient workflowClient;

	@MockitoBean
	private WorkerFactory workerFactory;

	@MockitoBean
	private GraphStateMachineEngine engine;

	@MockitoBean
	private WorkflowSessionRepository sessionRepository;

	@Autowired
	WorkflowExecutionServiceTransactionTest(WorkflowExecutionService executionService) {
		this.executionService = executionService;
	}

	@Test
	@DisplayName("startSession must not hold a DB transaction during engine (LLM) execution")
	void startSessionDoesNotHoldTransactionDuringEngineExecution() {
		when(sessionRepository.save(any(WorkflowSessionEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(engine.executeNextStep(any(), any(WorkflowSessionEntity.class), anyInt())).thenAnswer(invocation -> {
			WorkflowSessionEntity session = invocation.getArgument(1);
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
					"startSession must not hold a connection/transaction during engine (LLM) execution");
			session.setStatus(SessionStatus.COMPLETED);
			return session;
		});

		AiWorkflowSpec spec = new AiWorkflowSpec(null, "node-1", null, null, null, null);
		executionService.startSession("test-workflow", spec, "input", 5);
	}
}
