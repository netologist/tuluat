package com.tuluat.ai.crd.session;

public class WorkflowSessionStatus {
    private String sessionId;
    private String phase = "PENDING";
    private String currentNode;
    private String output;
    private String startTime;
    private String endTime;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getCurrentNode() { return currentNode; }
    public void setCurrentNode(String currentNode) { this.currentNode = currentNode; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
