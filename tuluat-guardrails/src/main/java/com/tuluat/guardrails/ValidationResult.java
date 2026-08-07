package com.tuluat.guardrails;

import java.util.List;

/**
 * Result of a post-execution output validation.
 *
 * @param valid      whether the output satisfied its JSON schema
 * @param confidence 0.0..1.0 score (1.0 for schema-valid output)
 * @param errors     validation errors (empty when valid)
 * @param filterName originating filter
 */
public record ValidationResult(
    boolean valid,
    double confidence,
    List<String> errors,
    String filterName
) {
    public static ValidationResult pass(String filterName) {
        return new ValidationResult(true, 1.0, List.of(), filterName);
    }

    public static ValidationResult fail(double confidence, List<String> errors, String filterName) {
        return new ValidationResult(false, confidence, errors, filterName);
    }
}
