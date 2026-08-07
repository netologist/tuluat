package com.tuluat.engine.temporal;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.Map;
import java.util.UUID;

@WorkflowInterface
public interface WorkflowSessionTemporalWorkflow {

    @WorkflowMethod
    Map<String, Object> runSession(UUID sessionId, String workflowName, AiWorkflowSpec spec, String input, int maxLoops);

    @SignalMethod
    void signalApproval(ApprovalSignal signal);
}
