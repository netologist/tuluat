package com.tuluat.crd.workflow;

public class NodeDefinition {
    private String id;
    private String type;
    private String agentRef;
    private String inputTemplate;
    private String outputKey;
    private String expression;
    private String outputSchema;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAgentRef() { return agentRef; }
    public void setAgentRef(String agentRef) { this.agentRef = agentRef; }
    public String getInputTemplate() { return inputTemplate; }
    public void setInputTemplate(String inputTemplate) { this.inputTemplate = inputTemplate; }
    public String getOutputKey() { return outputKey; }
    public void setOutputKey(String outputKey) { this.outputKey = outputKey; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getOutputSchema() { return outputSchema; }
    public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }
}
