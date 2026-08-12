# Tech Debt: `ModelGateway` — In-Memory Budget State (Not Durable, Not Pod-Safe)

- **Status:** Accepted (PoC trade-off)
- **Date:** 2026-08-12
- **Severity:** High
- **Module:** `tuluat-engine` → `ModelGateway`

## Current State

`ModelGateway` tracks per-agent LLM spend in a `ConcurrentHashMap<String, BudgetState>`:

```java
private final Map<String, BudgetState> budgets = new ConcurrentHashMap<>();

private static final class BudgetState {
    private final AtomicReference<Double> spentUsd = new AtomicReference<>(0.0);
    ...
}
```

Spend is accumulated via `recordSpend(agentName, costUsd)` on every successful model call.
The budget is pre-checked via `budget.spentUsd() >= budgetLimitUsd` at invocation time.

### Problems

| Problem | Detail |
|---|---|
| **In-memory only** | Budget resets to zero on every pod restart. A rolling update or OOMKill resets all agent budgets silently. |
| **Not multi-replica safe** | With `replicas > 1` in `AiAgentSpec`, each replica maintains its own independent `budgets` map. Budget checks are per-pod, not per-agent-globally. An agent with a $10 limit could spend $10 × N replicas before any single pod blocks it. |
| **No persistence** | There is no `BudgetEntity` or CRD status field recording cumulative spend. Operators cannot query "how much has agent X spent this month?" |
| **`getSpendByAgent()` is local only** | Exposed as an observability endpoint but returns only this pod's view — misleading in multi-replica deployments. |
| **Budget limit not surfaced on AiAgent CRD status** | The `LlmProvider` spec carries `budgetLimitUsd` (implicitly), but the `AiAgentStatus` has no `currentSpendUsd` field for Kubernetes-native visibility. |

### Code path

```
ModelGateway.invoke()
  → budgets.computeIfAbsent(agentName, ...)  // pod-local
  → budget.spentUsd() >= budgetLimitUsd      // pod-local check only
  → tryCall(...)
  → recordSpend(agentName, costUsd)          // pod-local accumulation
```

## Impact

- Budget enforcement is a **safety theater** in any replicated deployment. A cost-control CRD that says "$5 limit" can be silently violated by $5 × replicaCount.
- Pod restart wipes the spend counter → budget that was 90% exhausted becomes 0% after a crash-loop.
- No audit trail: finance teams cannot reconcile operator-reported spend without external billing APIs.

## Proposed Fix

1. **Persist spend to `WorkflowSessionEntity` or a dedicated `AgentSpendEntity`** — write cost per call to the existing PostgreSQL database already used for session state.

2. **Surface `currentSpendUsd` in `AiAgentStatus`** — reconciler reads DB aggregate and patches status; Kubernetes `kubectl get aiagents` shows live spend.

3. **Budget check via DB aggregate** — `SELECT SUM(cost_usd) FROM agent_spend WHERE agent_name = ? AND period = ?` replaces the in-memory map; acceptable latency overhead for a pre-call check.

4. **Short-term (PoC):** Expose a `/actuator/budget` endpoint that queries the DB and document the single-replica assumption explicitly in the `LlmProviderSpec` CRD schema description.

## Risk if not addressed

- Production multi-replica deployments will exceed budget limits by a factor of the replica count.
- No audit trail for compliance or charge-back reporting.
