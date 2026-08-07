package com.tuluat.engine.embabel;

public class EmbabelGoal {
    private String id;
    private String description;
    private String targetStateKey;

    public EmbabelGoal(String id, String description, String targetStateKey) {
        this.id = id;
        this.description = description;
        this.targetStateKey = targetStateKey;
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public String getTargetStateKey() { return targetStateKey; }
}
