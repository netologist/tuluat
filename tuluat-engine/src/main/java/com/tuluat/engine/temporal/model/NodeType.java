package com.tuluat.engine.temporal.model;

public enum NodeType {
	AGENT, CONDITION, HUMAN_APPROVAL, UNKNOWN;

	public static NodeType from(String type) {
		if (type == null)
			return UNKNOWN;
		try {
			return NodeType.valueOf(type.toUpperCase());
		} catch (IllegalArgumentException e) {
			return UNKNOWN;
		}
	}
}