package com.tuluat.guardrails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tuluat.crd.agent.GuardrailsConfig;
import com.tuluat.crd.agent.OutputValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Post-execution output validation filter. Verifies that the model output is
 * valid JSON conforming to the node's declared JSON Schema and scores
 * confidence (1.0 for schema-valid output, lower otherwise).
 */
@Component
public class OutputValidationFilter implements PostExecutionFilter {

    private static final Logger log = LoggerFactory.getLogger(OutputValidationFilter.class);

    public static final String NAME = "output-validation";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    @Override
    public String getFilterName() {
        return NAME;
    }

    @Override
    public ValidationResult validate(String output, GuardrailsConfig config, String outputSchema) {
        // A node-declared schema is a hard contract (ADR 007): validate it even
        // when the agent has no guardrails policy block (workflow path).
        boolean schemaGiven = outputSchema != null && !outputSchema.isBlank();
        if (!schemaGiven) {
            // No policy block declared => no post-execution filtering.
            if (config == null || config.outputValidation() == null || !config.outputValidation().isEnabled()) {
                return ValidationResult.pass(NAME);
            }
        }
        if (output == null || output.isBlank()) {
            return ValidationResult.fail(0.0, List.of("output is empty"), NAME);
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(output);
        } catch (Exception e) {
            return ValidationResult.fail(0.2, List.of("output is not valid JSON: " + e.getMessage()), NAME);
        }

        if (!schemaGiven) {
            // Well-formed JSON with baseline confidence when no schema declared.
            return new ValidationResult(true, 0.9, List.of(), NAME);
        }

        try {
            JsonSchema schema = schemaFactory.getSchema(outputSchema);
            Set<ValidationMessage> errors = schema.validate(node);
            if (errors.isEmpty()) {
                return ValidationResult.pass(NAME);
            }
            List<String> errorMessages = errors.stream()
                .map(ValidationMessage::getMessage)
                .limit(10)
                .toList();
            double confidence = Math.max(0.1, 0.8 - 0.1 * errorMessages.size());
            log.warn("OutputValidationFilter rejected output with {} schema violation(s)", errorMessages.size());
            return ValidationResult.fail(confidence, errorMessages, NAME);
        } catch (Exception e) {
            return ValidationResult.fail(0.2, List.of("schema validation error: " + e.getMessage()), NAME);
        }
    }
}
