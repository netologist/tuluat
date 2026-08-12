package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.app.websocket.WorkflowEventPublisher;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ApprovalControllerTest {

	private WorkflowSessionRepository sessionRepository;
	private WorkflowExecutionService executionService;
	private KubernetesResourceResolver resolver;
	private WorkflowEventPublisher eventPublisher;
	private ApprovalController controller;

	@BeforeEach
	void setUp() {
		sessionRepository = Mockito.mock(WorkflowSessionRepository.class);
		executionService = Mockito.mock(WorkflowExecutionService.class);
		eventPublisher = Mockito.mock(WorkflowEventPublisher.class);
		resolver = new KubernetesResourceResolver(Mockito.mock(KubernetesClient.class));
		controller = new ApprovalController(sessionRepository, executionService, resolver, eventPublisher);
	}

	@Test
	void testGetPendingApprovalsReturnsOnlyWaitingApproval() {
		UUID s1 = UUID.randomUUID();

		WorkflowSessionEntity entity1 = new WorkflowSessionEntity();
		entity1.setSessionId(s1);
		entity1.setWorkflowName("test-wf");
		entity1.setStatus(SessionStatus.WAITING_APPROVAL);
		entity1.setCurrentNodeId("approval-node");
		entity1.setContextData("{\"input\":\"test\"}");

		Mockito.when(sessionRepository.findByStatus(SessionStatus.WAITING_APPROVAL)).thenReturn(List.of(entity1));

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
