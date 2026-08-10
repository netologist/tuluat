package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the guardrails pipeline for one agent execution: pre-execution
 * filters in order (PII masking, prompt injection defense), then post-execution
 * output validation.
 */
@Service
@Slf4j
public class GuardrailPipeline {
private final List<PreExecutionFilter> preExecutionFilters;
	private final List<PostExecutionFilter> postExecutionFilters;

	public GuardrailPipeline(List<PreExecutionFilter> preExecutionFilters,
			List<PostExecutionFilter> postExecutionFilters) {
		this.preExecutionFilters = preExecutionFilters;
		this.postExecutionFilters = postExecutionFilters;
	}

	/**
	 * Runs enabled pre-execution filters on the prompt. If any filter blocks,
	 * returns its result immediately; otherwise returns the final transformed
	 * (masked/sanitized) content.
	 *
	 * @throws GuardrailBlockedException
	 *             when a filter blocks the prompt
	 */
	public String processPrompt(String prompt, GuardrailsConfig config) {
		String content = prompt;
		for (PreExecutionFilter filter : preExecutionFilters) {
			if (!filter.isEnabled(config)) {
				continue;
			}
			FilterResult result = filter.apply(content, config);
			if (!result.allowed()) {
				throw new GuardrailBlockedException(filter.getFilterName(), result.reason());
			}
			content = result.content();
		}
		return content;
	}

	/**
	 * Runs enabled post-execution filters on the model output.
	 */
	public ValidationResult validateOutput(String output, GuardrailsConfig config, String outputSchema) {
		for (PostExecutionFilter filter : postExecutionFilters) {
			ValidationResult result = filter.validate(output, config, outputSchema);
			if (!result.valid()) {
				log.warn("Guardrail pipeline rejected output: {}", result.errors());
				return result;
			}
		}
		return ValidationResult.pass("pipeline");
	}

	public List<String> getActiveFilterNames(GuardrailsConfig config) {
		return preExecutionFilters.stream().filter(f -> f.isEnabled(config)).map(GuardrailFilter::getFilterName)
				.toList();
	}
}
