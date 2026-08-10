package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.PiiMaskingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pre-execution PII masking filter. Replaces sensitive data (emails, credit
 * cards, SSNs, phone numbers) with a configurable token before the prompt is
 * sent to the external LLM provider.
 */
@Component
public class PiiMaskingFilter implements PreExecutionFilter {

	private static final Logger log = LoggerFactory.getLogger(PiiMaskingFilter.class);

	public static final String NAME = "pii-masking";

	private static final Map<String, Pattern> MODE_PATTERNS = Map.of("EMAIL",
			Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "CREDIT_CARD",
			Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b"), "SSN", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
			"PHONE", Pattern.compile("\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"));

	@Override
	public String getFilterName() {
		return NAME;
	}

	@Override
	public boolean isEnabled(GuardrailsConfig config) {
		return config != null && config.piiMasking() != null && config.piiMasking().isEnabled();
	}

	@Override
	public FilterResult apply(String prompt, GuardrailsConfig config) {
		if (prompt == null || prompt.isBlank()) {
			return FilterResult.allow(prompt, NAME);
		}
		PiiMaskingConfig policy = config.piiMasking();
		List<String> modes = policy.modes() == null || policy.modes().isEmpty()
				? List.of("EMAIL", "CREDIT_CARD", "SSN", "PHONE")
				: policy.modes();
		String token = policy.replacementToken() == null ? "[REDACTED]" : policy.replacementToken();

		String masked = prompt;
		int hits = 0;
		for (String mode : modes) {
			Pattern p = MODE_PATTERNS.get(mode.toUpperCase());
			if (p == null) {
				continue;
			}
			var matcher = p.matcher(masked);
			StringBuilder sb = new StringBuilder();
			while (matcher.find()) {
				matcher.appendReplacement(sb, token);
				hits++;
			}
			matcher.appendTail(sb);
			masked = sb.toString();
		}

		if (hits > 0) {
			log.info("PiiMaskingFilter masked {} sensitive value(s) in prompt", hits);
		}
		return FilterResult.allow(masked, NAME);
	}
}
