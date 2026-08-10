package com.tuluat.engine.embabel;

import java.util.ArrayList;
import java.util.List;

public record EmbabelAction(String name, String agentRef, String inputTemplate, String outputKey,
		List<String> requiredPreconditions) {
	public EmbabelAction {
		if (requiredPreconditions == null) {
			requiredPreconditions = List.of();
		}
	}
}
