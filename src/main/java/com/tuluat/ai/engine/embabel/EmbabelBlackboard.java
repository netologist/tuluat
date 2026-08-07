package com.tuluat.ai.engine.embabel;

import java.util.HashMap;
import java.util.Map;

public class EmbabelBlackboard {

    private final Map<String, Object> state = new HashMap<>();

    public EmbabelBlackboard() {}

    public EmbabelBlackboard(Map<String, Object> initialState) {
        if (initialState != null) {
            this.state.putAll(initialState);
        }
    }

    public Object get(String key) { return state.get(key); }
    public void put(String key, Object value) { state.put(key, value); }
    public boolean has(String key) { return state.containsKey(key) && state.get(key) != null; }
    public Map<String, Object> getState() { return new HashMap<>(state); }
}
