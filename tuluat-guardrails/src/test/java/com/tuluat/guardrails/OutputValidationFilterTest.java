package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.OutputValidationConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputValidationFilterTest {

	private final OutputValidationFilter filter = new OutputValidationFilter();

	@Test
	void passesValidJsonAgainstSchema() {
		String schema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}";
		GuardrailsConfig cfg = new GuardrailsConfig(null, null, new OutputValidationConfig(true, 0.5));
		ValidationResult r = filter.validate("{\"name\":\"ok\"}", cfg, schema);
		assertTrue(r.valid());
		assertTrue(r.errors().isEmpty());
	}

	@Test
	void rejectsSchemaViolation() {
		String schema = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";
		GuardrailsConfig cfg = new GuardrailsConfig(null, null, new OutputValidationConfig(true, 0.5));
		ValidationResult r = filter.validate("{\"age\":5}", cfg, schema);
		assertFalse(r.valid());
		assertFalse(r.errors().isEmpty());
	}

	@Test
	void rejectsMalformedJson() {
		GuardrailsConfig cfg = new GuardrailsConfig(null, null, new OutputValidationConfig(true, 0.5));
		ValidationResult r = filter.validate("not json at all", cfg, null);
		assertFalse(r.valid());
	}

	@Test
	void acceptsWellFormedJsonWithoutSchema() {
		GuardrailsConfig cfg = new GuardrailsConfig(null, null, new OutputValidationConfig(true, 0.5));
		ValidationResult r = filter.validate("{\"any\": [1, 2, 3]}", cfg, null);
		assertTrue(r.valid());
	}

	@Test
	void noConfigMeansNoValidation() {
		ValidationResult r = filter.validate("garbage{{{", null, null);
		assertTrue(r.valid());
	}

	@Test
	void skipsValidationWhenDisabled() {
		GuardrailsConfig cfg = new GuardrailsConfig(null, null, new OutputValidationConfig(false, 0.5));
		ValidationResult r = filter.validate("garbage{{{", cfg, null);
		assertTrue(r.valid());
	}
}
