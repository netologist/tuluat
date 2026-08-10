package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;

/**
 * Result of a pre-execution guardrail filter application.
 *
 * @param allowed
 *            whether the content may proceed to the LLM provider
 * @param content
 *            masked/sanitized content (equals input when unchanged)
 * @param reason
 *            human-readable explanation when blocked
 * @param filterName
 *            originating filter
 */
public record FilterResult(boolean allowed, String content, String reason, String filterName) {
	public static FilterResult allow(String content, String filterName) {
		return new FilterResult(true, content, null, filterName);
	}

	public static FilterResult block(String content, String reason, String filterName) {
		return new FilterResult(false, content, reason, filterName);
	}
}
