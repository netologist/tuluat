package com.tuluat.app.controller;

import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
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
                                                                @RequestParam(required = false) String namespace,
                                                                @RequestBody Map<String, Object> request) {
        String input = (String) request.getOrDefault("input", "");
        int maxLoops = (int) request.getOrDefault("maxLoops", 10);

        String targetNamespace = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";

        AiWorkflow workflow = kubernetesClient.resources(AiWorkflow.class)
                .inNamespace(targetNamespace)
                .withName(workflowName)
                .get();

        if (workflow == null) {
            workflow = kubernetesClient.resources(AiWorkflow.class)
                    .inNamespace("default")
                    .withName(workflowName)
                    .get();
        }

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

    @PostMapping("/sessions/{sessionId}/approve")
    public ResponseEntity<Map<String, Object>> approveSessionStep(@PathVariable UUID sessionId,
                                                                 @RequestBody Map<String, Object> request) {
        boolean approved = Boolean.parseBoolean(String.valueOf(request.getOrDefault("approved", true)));
        String feedback = (String) request.get("feedback");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

        ApprovalSignal signal = new ApprovalSignal(approved, feedback, metadata);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId.toString(),
                "status", "SIGNAL_SENT",
                "approved", signal.isApproved(),
                "feedback", signal.getFeedback() != null ? signal.getFeedback() : ""
        ));
    }
}
