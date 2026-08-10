package com.tuluat.engine.embabel;

import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * NOT thread-safe. Access from a single thread only.
 */
@NoArgsConstructor
public class EmbabelBlackboard {

	private final Map<String, Object> state = new HashMap<>();

	public EmbabelBlackboard(Map<String, Object> initialState) {
		if (initialState != null) {
			this.state.putAll(initialState);
		}
	}

	public Object get(String key) {
		return state.get(key);
	}
	public void put(String key, Object value) {
		state.put(key, value);
	}
	public boolean has(String key) {
		return state.containsKey(key) && state.get(key) != null;
	}
	public Map<String, Object> getState() {
		return new HashMap<>(state);
	}
}
