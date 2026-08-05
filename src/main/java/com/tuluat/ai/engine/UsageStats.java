package com.tuluat.ai.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record encapsulating token usage statistics and cost estimation in USD.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageStats(
    @JsonProperty("inputTokens") int inputTokens,
    @JsonProperty("outputTokens") int outputTokens,
    @JsonProperty("totalTokens") int totalTokens,
    @JsonProperty("estimatedCostUsd") double estimatedCostUsd,
    @JsonProperty("latencyMs") long latencyMs
) {
    /**
     * Calculates UsageStats and estimates USD cost based on model token rates per 1,000,000 tokens.
     */
    public static UsageStats calculate(int inputTokens, int outputTokens, String model, long latencyMs) {
        int total = inputTokens + outputTokens;
        String m = (model != null) ? model.toLowerCase() : "";

        // Model pricing rates per 1,000,000 tokens (USD)
        double inputRatePer1M = switch (m) {
            case "deepseek-chat", "deepseek-v3" -> 0.14;       // $0.14 / 1M input
            case "deepseek-reasoner", "deepseek-r1" -> 0.55;  // $0.55 / 1M input
            case "gpt-4o" -> 2.50;                            // $2.50 / 1M input
            case "gpt-4o-mini" -> 0.15;                       // $0.15 / 1M input
            case "llama3", "llama3.2" -> 0.0;                 // Local/Self-hosted (free)
            default -> 0.50;
        };

        double outputRatePer1M = switch (m) {
            case "deepseek-chat", "deepseek-v3" -> 0.28;       // $0.28 / 1M output
            case "deepseek-reasoner", "deepseek-r1" -> 2.19;  // $2.19 / 1M output
            case "gpt-4o" -> 10.00;                           // $10.00 / 1M output
            case "gpt-4o-mini" -> 0.60;                       // $0.60 / 1M output
            case "llama3", "llama3.2" -> 0.0;                 // Local/Self-hosted (free)
            default -> 1.50;
        };

        double cost = ((inputTokens / 1_000_000.0) * inputRatePer1M) + ((outputTokens / 1_000_000.0) * outputRatePer1M);
        double roundedCost = Math.round(cost * 1_000_000.0) / 1_000_000.0;

        return new UsageStats(inputTokens, outputTokens, total, roundedCost, latencyMs);
    }
}
