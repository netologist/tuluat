package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.crd.session.WorkflowSession;
import com.tuluat.crd.session.WorkflowSessionStatus;
import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.NodeExecutionRepository;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowSessionControllerTest {

	private WorkflowExecutionService executionService;
	private WorkflowSessionRepository sessionRepository;
	private NodeExecutionRepository nodeExecutionRepository;
	private WorkflowSessionLogRepository logRepository;
	private KubernetesResourceResolver resolver;
	private WorkflowSessionController controller;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		executionService = mock(WorkflowExecutionService.class);
		sessionRepository = mock(WorkflowSessionRepository.class);
		nodeExecutionRepository = mock(NodeExecutionRepository.class);
		logRepository = mock(WorkflowSessionLogRepository.class);
		resolver = mock(KubernetesResourceResolver.class);

		controller = new WorkflowSessionController(executionService, sessionRepository, nodeExecutionRepository,
				resolver, logRepository, null);
	}

	@Test
	@DisplayName("Should return 404 when requested AiWorkflow CR is not found in cluster")
	void testCreateSessionWorkflowNotFound() {
		when(resolver.get(eq(AiWorkflow.class), isNull(), eq("non-existent-wf"))).thenReturn(null);

		ResponseEntity<WorkflowSessionEntity> response = controller.createSession("non-existent-wf", null,
				Map.of("input", "hello"));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		verify(resolver, never()).createOrReplace(eq(WorkflowSession.class), any(WorkflowSession.class));
	}

	@Test
	@DisplayName("Should create WorkflowSession CR and return completed entity when AiWorkflow CR exists")
	void testCreateSessionSuccess() {
		AiWorkflow workflow = new AiWorkflow();
		workflow.setMetadata(new ObjectMetaBuilder().withName("my-workflow").withNamespace("tuluat-system").build());
		workflow.setSpec(new AiWorkflowSpec(null, "node-1", null, null, null, null));

		when(resolver.get(eq(AiWorkflow.class), isNull(), eq("my-workflow"))).thenReturn(workflow);

		WorkflowSessionEntity entity = new WorkflowSessionEntity();
		entity.setSessionId(UUID.randomUUID());
		entity.setWorkflowName("my-workflow");
		entity.setStatus(SessionStatus.COMPLETED);

		WorkflowSession completedCr = new WorkflowSession();
		completedCr.setStatus(new WorkflowSessionStatus(entity.getSessionId().toString(), "COMPLETED", null, null, null,
				null, 0L, 0L, 0L, BigDecimal.ZERO, 0L, List.of()));
		when(resolver.get(eq(WorkflowSession.class), eq("tuluat-system"), anyString())).thenReturn(completedCr);
		when(sessionRepository.findById(entity.getSessionId())).thenReturn(Optional.of(entity));

		ResponseEntity<WorkflowSessionEntity> response = controller.createSession("my-workflow", null,
				Map.of("input", "test input", "maxLoops", 10));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(SessionStatus.COMPLETED, response.getBody().getStatus());
		verify(resolver).createOrReplace(eq(WorkflowSession.class), any(WorkflowSession.class));
	}

	@Test
	@DisplayName("Should get session by ID")
	void testGetSession() {
		UUID sessionId = UUID.randomUUID();
		WorkflowSessionEntity entity = new WorkflowSessionEntity();
		entity.setSessionId(sessionId);
		entity.setStatus(SessionStatus.RUNNING);

		when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(entity));

		ResponseEntity<WorkflowSessionEntity> response = controller.getSession(sessionId);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(SessionStatus.RUNNING, response.getBody().getStatus());
	}

	@Test
	@DisplayName("Should get session execution logs by session ID")
	void testGetSessionLogs() {
		UUID sessionId = UUID.randomUUID();
		WorkflowSessionLogEntity log1 = new WorkflowSessionLogEntity();
		log1.setSessionId(sessionId);
		log1.setMessage("Executing node-1");

		when(logRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(log1));

		ResponseEntity<List<WorkflowSessionLogEntity>> response = controller.getSessionLogs(sessionId);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());
		assertEquals("Executing node-1", response.getBody().get(0).getMessage());
	}

	@Test
	@DisplayName("Should list sessions ordered by created date")
	void testGetSessions() {
		WorkflowSessionEntity entity = new WorkflowSessionEntity();
		entity.setSessionId(UUID.randomUUID());
		entity.setWorkflowName("order-processing-workflow");
		entity.setStatus(SessionStatus.COMPLETED);

		when(sessionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entity));
		when(nodeExecutionRepository.findAll()).thenReturn(List.of());

		ResponseEntity<List<Map<String, Object>>> response = controller.getSessions(null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());
		assertEquals("order-processing-workflow", response.getBody().get(0).get("workflowName"));
	}

	@Test
	@DisplayName("Should build execution tree steps with condition evaluation data")
	void testGetSessionExecutionTree() {
		UUID sessionId = UUID.randomUUID();
		WorkflowSessionEntity session = new WorkflowSessionEntity();
		session.setSessionId(sessionId);
		session.setWorkflowName("order-processing-workflow");
		session.setContextData("{\"riskResult\":\"HIGH Risk Detected\"}");

		when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
		when(nodeExecutionRepository.findBySessionIdOrderByStartTimeAsc(sessionId)).thenReturn(List.of());

		WorkflowSessionLogEntity log1 = new WorkflowSessionLogEntity();
		log1.setSessionId(sessionId);
		log1.setNodeId("risk-check-condition");
		log1.setMessage("Executing node 'risk-check-condition' (type: CONDITION) for session " + sessionId);

		WorkflowSessionLogEntity log2 = new WorkflowSessionLogEntity();
		log2.setSessionId(sessionId);
		log2.setNodeId("risk-check-condition");
		log2.setMessage(
				"Condition expression '#data[\"riskResult\"].contains(\"HIGH\")' evaluated to: true with context: {\"riskResult\":\"HIGH Risk Detected\"}");

		when(logRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(log1, log2));

		ResponseEntity<List<Map<String, Object>>> response = controller.getSessionExecutionTree(sessionId);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());

		Map<String, Object> step = response.getBody().get(0);
		assertEquals("risk-check-condition", step.get("nodeId"));
		assertEquals("CONDITION", step.get("nodeType"));
		assertEquals(true, step.get("evaluationResult"));
		assertEquals("#data[\"riskResult\"].contains(\"HIGH\")", step.get("expression"));
	}

	@Test
	@DisplayName("Should send free-form approval signal for human-in-the-loop node")
	void testApproveSessionStepWithFreeFormFeedback() {
		UUID sessionId = UUID.randomUUID();
		Map<String, Object> body = Map.of("approved", true, "feedback", "Add security analysis section", "metadata",
				Map.of("reviewer", "admin"));

		ResponseEntity<Map<String, Object>> response = controller.approveSessionStep(sessionId, body);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("SIGNAL_SENT", response.getBody().get("status"));
		assertEquals(true, response.getBody().get("approved"));
		assertEquals("Add security analysis section", response.getBody().get("feedback"));
	}

	@Test
	@DisplayName("Should route POST /api/v1/sessions/{sessionId}/approve correctly via MockMvc")
	void testApproveSessionStepEndpointWithMockMvc() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
		UUID sessionId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/approve").contentType(MediaType.APPLICATION_JSON)
				.content("{\"approved\": true, \"feedback\": \"E2E Acceptance Test Signal Verified\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNAL_SENT"))
				.andExpect(jsonPath("$.approved").value(true))
				.andExpect(jsonPath("$.feedback").value("E2E Acceptance Test Signal Verified"));
	}
}
