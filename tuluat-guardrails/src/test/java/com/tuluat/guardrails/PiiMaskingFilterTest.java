package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.PiiMaskingConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiMaskingFilterTest {

    private final PiiMaskingFilter filter = new PiiMaskingFilter();

    private GuardrailsConfig cfg(List<String> modes) {
        return new GuardrailsConfig(
            new PiiMaskingConfig(true, modes, "[REDACTED]"),
            null, null
        );
    }

    @Test
    void masksEmail() {
        FilterResult r = filter.apply("reach me at alice.smith@corp.io today", cfg(List.of("EMAIL")));
        assertTrue(r.allowed());
        assertFalse(r.content().contains("alice.smith@corp.io"));
        assertTrue(r.content().contains("[REDACTED]"));
    }

    @Test
    void masksCreditCardAndSsn() {
        FilterResult r = filter.apply("card 4111 1111 1111 1111 ssn 123-45-6789", cfg(List.of("CREDIT_CARD", "SSN")));
        assertEquals(2, countRedacted(r.content()));
        assertFalse(r.content().contains("4111"));
        assertFalse(r.content().contains("123-45-6789"));
    }

    private static int countRedacted(String s) {
        return (int) java.util.regex.Pattern.compile("\\[REDACTED\\]").matcher(s).results().count();
    }

    @Test
    void masksPhone() {
        FilterResult r = filter.apply("call +1 (555) 123-4567 now", cfg(List.of("PHONE")));
        assertTrue(r.content().contains("[REDACTED]"));
        assertFalse(r.content().contains("123-4567"));
    }

    @Test
    void leavesCleanTextUntouched() {
        String clean = "What is the weather in Istanbul?";
        FilterResult r = filter.apply(clean, cfg(List.of("EMAIL", "SSN")));
        assertEquals(clean, r.content());
        assertTrue(r.allowed());
    }

    @Test
    void disabledWhenNotEnabled() {
        GuardrailsConfig off = new GuardrailsConfig(new PiiMaskingConfig(false, List.of("EMAIL"), null), null, null);
        assertFalse(filter.isEnabled(off));
        GuardrailsConfig on = cfg(List.of("EMAIL"));
        assertTrue(filter.isEnabled(on));
    }
}
