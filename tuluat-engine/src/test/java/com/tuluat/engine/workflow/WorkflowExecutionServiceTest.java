package com.tuluat.engine.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowExecutionServiceTest {

	private WorkflowSessionRepository sessionRepository;
	private GraphStateMachineEngine engine;
	private ObjectMapper objectMapper;
	private WorkflowExecutionService service;

	@BeforeEach
	void setUp() {
		sessionRepository = mock(WorkflowSessionRepository.class);
		engine = mock(GraphStateMachineEngine.class);
		objectMapper = new ObjectMapper();
		service = new WorkflowExecutionService(sessionRepository, engine, objectMapper, java.util.Optional.empty(),
				java.util.Optional.empty());
		when(sessionRepository.save(any(WorkflowSessionEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("Should initialize and execute session through engine until COMPLETED")
	void testStartSessionSuccess() {
		AiWorkflowSpec spec = new AiWorkflowSpec(null, "node-1", null, null, null);

		when(engine.executeNextStep(eq(spec), any(WorkflowSessionEntity.class), eq(5))).thenAnswer(invocation -> {
			WorkflowSessionEntity session = invocation.getArgument(1);
			session.setStatus("COMPLETED");
			session.setCurrentNodeId("node-2");
			return session;
		});

		WorkflowSessionEntity result = service.startSession("test-workflow", spec, "test input", 5);

		assertNotNull(result);
		assertEquals("test-workflow", result.getWorkflowName());
		assertEquals("COMPLETED", result.getStatus());
		assertEquals("node-2", result.getCurrentNodeId());

		verify(sessionRepository, atLeastOnce()).save(any(WorkflowSessionEntity.class));
		verify(engine, times(1)).executeNextStep(eq(spec), any(WorkflowSessionEntity.class), eq(5));
	}
}
