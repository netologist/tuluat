package com.tuluat.ai.controller;

import com.tuluat.ai.crd.workflow.AiWorkflow;
import com.tuluat.ai.crd.workflow.AiWorkflowSpec;
import com.tuluat.ai.engine.workflow.WorkflowExecutionService;
import com.tuluat.ai.entity.WorkflowSessionEntity;
import com.tuluat.ai.entity.WorkflowSessionLogEntity;
import com.tuluat.ai.repository.WorkflowSessionLogRepository;
import com.tuluat.ai.repository.WorkflowSessionRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowSessionControllerTest {

    private WorkflowExecutionService executionService;
    private WorkflowSessionRepository sessionRepository;
    private WorkflowSessionLogRepository logRepository;
    private KubernetesClient kubernetesClient;
    private WorkflowSessionController controller;

    private MixedOperation workflowsMock;
    private NonNamespaceOperation workflowNsMock;
    private Resource workflowResMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        executionService = mock(WorkflowExecutionService.class);
        sessionRepository = mock(WorkflowSessionRepository.class);
        logRepository = mock(WorkflowSessionLogRepository.class);
        kubernetesClient = mock(KubernetesClient.class);

        workflowsMock = mock(MixedOperation.class);
        workflowNsMock = mock(NonNamespaceOperation.class);
        workflowResMock = mock(Resource.class);

        when(kubernetesClient.resources(eq(AiWorkflow.class))).thenReturn(workflowsMock);
        when(workflowsMock.inNamespace(anyString())).thenReturn(workflowNsMock);
        when(workflowNsMock.withName(anyString())).thenReturn(workflowResMock);

        controller = new WorkflowSessionController(executionService, sessionRepository, logRepository, kubernetesClient);
    }

    @Test
    @DisplayName("Should return 404 when requested AiWorkflow CR is not found in cluster")
    void testCreateSessionWorkflowNotFound() {
        when(workflowResMock.get()).thenReturn(null);

        ResponseEntity<WorkflowSessionEntity> response = controller.createSession("non-existent-wf", Map.of("input", "hello"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(executionService, never()).startSession(anyString(), any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Should create session when AiWorkflow CR exists")
    void testCreateSessionSuccess() {
        AiWorkflow workflow = new AiWorkflow();
        AiWorkflowSpec spec = new AiWorkflowSpec();
        spec.setInitialNode("node-1");
        workflow.setSpec(spec);

        when(workflowResMock.get()).thenReturn(workflow);

        WorkflowSessionEntity entity = new WorkflowSessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setWorkflowName("my-workflow");
        entity.setStatus("COMPLETED");

        when(executionService.startSession(eq("my-workflow"), eq(spec), eq("test input"), eq(10)))
                .thenReturn(entity);

        ResponseEntity<WorkflowSessionEntity> response = controller.createSession("my-workflow", Map.of("input", "test input", "maxLoops", 10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("COMPLETED", response.getBody().getStatus());
    }

    @Test
    @DisplayName("Should get session by ID")
    void testGetSession() {
        UUID sessionId = UUID.randomUUID();
        WorkflowSessionEntity entity = new WorkflowSessionEntity();
        entity.setSessionId(sessionId);
        entity.setStatus("RUNNING");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(entity));

        ResponseEntity<WorkflowSessionEntity> response = controller.getSession(sessionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("RUNNING", response.getBody().getStatus());
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
}
