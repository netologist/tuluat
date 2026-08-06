package com.tuluat.ai.crd.workflow;

public class MemoryConfig {
    private int shortMemorySize = 20;
    private boolean enableLongMemory = true;
    private String vectorTableName = "session_long_memory";

    public int getShortMemorySize() { return shortMemorySize; }
    public void setShortMemorySize(int shortMemorySize) { this.shortMemorySize = shortMemorySize; }
    public boolean isEnableLongMemory() { return enableLongMemory; }
    public void setEnableLongMemory(boolean enableLongMemory) { this.enableLongMemory = enableLongMemory; }
    public String getVectorTableName() { return vectorTableName; }
    public void setVectorTableName(String vectorTableName) { this.vectorTableName = vectorTableName; }
}
