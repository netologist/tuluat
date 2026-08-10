package com.tuluat.guardrails;

/**
 * Thrown when a pre-execution guardrail blocks a prompt from reaching the LLM.
 */
public class GuardrailBlockedException extends RuntimeException {

	private final String filterName;

	public GuardrailBlockedException(String filterName, String reason) {
		super("Guardrail [" + filterName + "] blocked request: " + reason);
		this.filterName = filterName;
	}

	public String getFilterName() {
		return filterName;
	}
}
