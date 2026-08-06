package com.tuluat.ai.crd.workflow;

import java.util.ArrayList;
import java.util.List;

public class AiWorkflowSpec {
    private String description;
    private String initialNode;
    private List<NodeDefinition> nodes = new ArrayList<>();
    private List<EdgeDefinition> edges = new ArrayList<>();
    private MemoryConfig memoryConfig = new MemoryConfig();

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInitialNode() { return initialNode; }
    public void setInitialNode(String initialNode) { this.initialNode = initialNode; }
    public List<NodeDefinition> getNodes() { return nodes; }
    public void setNodes(List<NodeDefinition> nodes) { this.nodes = nodes; }
    public List<EdgeDefinition> getEdges() { return edges; }
    public void setEdges(List<EdgeDefinition> edges) { this.edges = edges; }
    public MemoryConfig getMemoryConfig() { return memoryConfig; }
    public void setMemoryConfig(MemoryConfig memoryConfig) { this.memoryConfig = memoryConfig; }
}
