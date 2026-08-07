package com.tuluat.ai.engine.embabel;

import java.util.ArrayList;
import java.util.List;

public class EmbabelAction {
    private String name;
    private String agentRef;
    private String inputTemplate;
    private String outputKey;
    private List<String> requiredPreconditions = new ArrayList<>();

    public EmbabelAction(String name, String agentRef, String inputTemplate, String outputKey, List<String> requiredPreconditions) {
        this.name = name;
        this.agentRef = agentRef;
        this.inputTemplate = inputTemplate;
        this.outputKey = outputKey;
        if (requiredPreconditions != null) {
            this.requiredPreconditions = requiredPreconditions;
        }
    }

    public String getName() { return name; }
    public String getAgentRef() { return agentRef; }
    public String getInputTemplate() { return inputTemplate; }
    public String getOutputKey() { return outputKey; }
    public List<String> getRequiredPreconditions() { return requiredPreconditions; }
}
