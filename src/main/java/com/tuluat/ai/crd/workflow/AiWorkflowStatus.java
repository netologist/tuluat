package com.tuluat.ai.crd.workflow;

public class AiWorkflowStatus {
    private String state = "Ready";
    private int nodeCount;

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
}
