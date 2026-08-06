package com.tuluat.ai.engine.workflow;

import com.tuluat.ai.crd.workflow.AiWorkflowSpec;
import com.tuluat.ai.entity.WorkflowSessionEntity;
import com.tuluat.ai.repository.WorkflowSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WorkflowExecutionService {

    private final WorkflowSessionRepository sessionRepository;
    private final GraphStateMachineEngine engine;

    public WorkflowExecutionService(WorkflowSessionRepository sessionRepository, GraphStateMachineEngine engine) {
        this.sessionRepository = sessionRepository;
        this.engine = engine;
    }

    @Transactional
    public WorkflowSessionEntity startSession(String workflowName, AiWorkflowSpec spec, String input, int maxLoops) {
        WorkflowSessionEntity session = new WorkflowSessionEntity();
        session.setSessionId(UUID.randomUUID());
        session.setWorkflowName(workflowName);
        session.setStatus("RUNNING");
        session.setCurrentNodeId(spec.getInitialNode());
        session.setContextData("{\"input\":\"" + input + "\"}");

        session = sessionRepository.save(session);

        while ("RUNNING".equalsIgnoreCase(session.getStatus())) {
            session = engine.executeNextStep(spec, session, maxLoops);
            session = sessionRepository.save(session);
        }

        return session;
    }
}
