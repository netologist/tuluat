package com.tuluat.ai.controller;

import com.tuluat.ai.crd.workflow.AiWorkflow;
import com.tuluat.ai.engine.workflow.WorkflowExecutionService;
import com.tuluat.ai.entity.WorkflowSessionEntity;
import com.tuluat.ai.repository.WorkflowSessionRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WorkflowSessionController {

    private final WorkflowExecutionService executionService;
    private final WorkflowSessionRepository sessionRepository;
    private final KubernetesClient kubernetesClient;

    public WorkflowSessionController(WorkflowExecutionService executionService,
                                      WorkflowSessionRepository sessionRepository,
                                      KubernetesClient kubernetesClient) {
        this.executionService = executionService;
        this.sessionRepository = sessionRepository;
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
}
