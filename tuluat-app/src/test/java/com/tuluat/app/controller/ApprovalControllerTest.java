package com.tuluat.app.controller;

import com.tuluat.app.websocket.WorkflowEventPublisher;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ApprovalControllerTest {

	private WorkflowSessionRepository sessionRepository;
	private WorkflowExecutionService executionService;
	private WorkflowEventPublisher eventPublisher;
	private ApprovalController controller;

	@BeforeEach
	void setUp() {
		sessionRepository = Mockito.mock(WorkflowSessionRepository.class);
		executionService = Mockito.mock(WorkflowExecutionService.class);
		eventPublisher = Mockito.mock(WorkflowEventPublisher.class);
		controller = new ApprovalController(sessionRepository, executionService, eventPublisher);
	}

	@Test
	void testGetPendingApprovalsReturnsOnlyWaitingApproval() {
		UUID s1 = UUID.randomUUID();
		UUID s2 = UUID.randomUUID();

		WorkflowSessionEntity entity1 = new WorkflowSessionEntity();
		entity1.setSessionId(s1);
		entity1.setWorkflowName("test-wf");
		entity1.setStatus("WAITING_APPROVAL");
		entity1.setCurrentNodeId("approval-node");
		entity1.setContextData("{\"input\":\"test\"}");

		WorkflowSessionEntity entity2 = new WorkflowSessionEntity();
		entity2.setSessionId(s2);
		entity2.setWorkflowName("test-wf");
		entity2.setStatus("RUNNING");
		entity2.setCurrentNodeId("agent-node");
		entity2.setContextData("{\"input\":\"test\"}");

		Mockito.when(sessionRepository.findAll()).thenReturn(List.of(entity1, entity2));

		ResponseEntity<List<Map<String, Object>>> response = controller.getPendingApprovals();
		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());
		assertEquals(s1, response.getBody().get(0).get("sessionId"));
	}

	@Test
	void testHandleApprovalActionExecutesSignalAndPublishesEvent() {
		UUID sessionId = UUID.randomUUID();
		Map<String, Object> request = Map.of("approved", true, "feedback", "Looks great!");

		ResponseEntity<Map<String, Object>> response = controller.handleApprovalAction(sessionId, request);
		assertEquals(200, response.getStatusCode().value());
		assertEquals("SUCCESS", response.getBody().get("status"));
		assertEquals(true, response.getBody().get("approved"));

		Mockito.verify(executionService).sendApprovalSignal(eq(sessionId.toString()), any());
		Mockito.verify(eventPublisher).publishApprovalResolved(eq(sessionId), eq(true), eq("Looks great!"));
	}
}
