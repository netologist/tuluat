package com.tuluat.guardrails;

import com.tuluat.crd.agent.GuardrailsConfig;

/**
 * Base contract for guardrail filters. Implementations are pure functions:
 * given content plus the agent's {@link GuardrailsConfig}, produce a
 * decision/transformation. No I/O, no state.
 */
public interface GuardrailFilter {

    /**
     * Unique, stable filter name (used in logs, metrics, and results).
     */
    String getFilterName();
}
