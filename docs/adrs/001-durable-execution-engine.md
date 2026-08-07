# ADR 001: Adoption of Temporal for Durable Workflow Execution

* **Status:** Accepted  
* **Date:** 2026-08-06  
* **Deciders:** Software Architecture Team  

---

## Context and Problem Statement
Multi-agent workflows (`AiWorkflow`) and sessions (`WorkflowSession`) involve long-running executions, asynchronous agent-to-agent data pass-through, human-in-the-loop approvals (`WAITING_APPROVAL`), and LLM retries. The in-process `while` loop implementation in Spring Boot blocks Java threads and risks losing execution state if the operator pod crashes or restarts.

## Decision Drivers
* **Fault Tolerance:** State must survive pod restarts and network outages without data corruption.
* **Non-blocking Concurrency:** Support thousands of concurrent sessions without thread exhaustion.
* **Human-in-the-Loop:** Support long-running signals and approval gates that can wait days for human response.
* **Deterministic Replay:** Ability to replay workflow executions deterministically for audit and debugging.

## Considered Options
1. **In-Process Thread Loop (Status Quo):** Simple, but lacks crash safety, thread scalability, and long-term signal capability.
2. **Temporal Workflow Engine:** Open-source durable execution platform supporting deterministic workflows, activities, and signal handling.
3. **Kafka/RabbitMQ Event-Driven Machine:** High scalability, but introduces significant operational overhead and complex state machine management.

## Decision Outcome
**Chosen Option:** **Temporal Workflow Engine (Option 2)**.

### Positives
* **Crash Resilience:** Workflows automatically resume from the exact step where they left off if a worker pod dies.
* **Native Signal & Human-in-the-Loop Support:** `WAITING_APPROVAL` states can be implemented cleanly as Temporal Signals.
* **Event Sourcing Audit:** Temporal History provides a complete, immutable audit trail for every node step and decision.

### Negatives
* Requires deploying and managing a Temporal Cluster (or using Temporal Cloud).
