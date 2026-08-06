package com.tuluat.ai.controller;

import com.tuluat.ai.crd.workflow.AiWorkflow;
import com.tuluat.ai.engine.workflow.WorkflowExecutionService;
import com.tuluat.ai.entity.WorkflowSessionEntity;
import com.tuluat.ai.entity.WorkflowSessionLogEntity;
import com.tuluat.ai.repository.WorkflowSessionLogRepository;
import com.tuluat.ai.repository.WorkflowSessionRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WorkflowSessionController {

    private final WorkflowExecutionService executionService;
    private final WorkflowSessionRepository sessionRepository;
    private final WorkflowSessionLogRepository logRepository;
    private final KubernetesClient kubernetesClient;

    public WorkflowSessionController(WorkflowExecutionService executionService,
                                      WorkflowSessionRepository sessionRepository,
                                      KubernetesClient kubernetesClient) {
        this(executionService, sessionRepository, null, kubernetesClient);
    }

    @Autowired
    public WorkflowSessionController(WorkflowExecutionService executionService,
                                      WorkflowSessionRepository sessionRepository,
                                      @Autowired(required = false) WorkflowSessionLogRepository logRepository,
                                      KubernetesClient kubernetesClient) {
        this.executionService = executionService;
        this.sessionRepository = sessionRepository;
        this.logRepository = logRepository;
        this.kubernetesClient = kubernetesClient;
    }

    @PostMapping("/workflows/{workflowName}/sessions")
    public ResponseEntity<WorkflowSessionEntity> createSession(@PathVariable String workflowName,
                                                                @RequestBody Map<String, Object> request) {
        String input = (String) request.getOrDefault("input", "");
        int maxLoops = (int) request.getOrDefault("maxLoops", 10);

        AiWorkflow workflow = kubernetesClient.resources(AiWorkflow.class)
                .inNamespace("default")
                .withName(workflowName)
                .get();

        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }

        WorkflowSessionEntity session = executionService.startSession(workflowName, workflow.getSpec(), input, maxLoops);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<WorkflowSessionEntity> getSession(@PathVariable UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/{sessionId}/logs")
    public ResponseEntity<List<WorkflowSessionLogEntity>> getSessionLogs(@PathVariable UUID sessionId) {
        if (logRepository == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(logRepository.findBySessionIdOrderByCreatedAtAsc(sessionId));
    }
}
