# ADR 009: Agent Harness — Sandboxed Execution Runtime

* **Status:** Accepted (implementation deferred; design approved)
* **Date:** 2026-08-07
* **Deciders:** Software Architecture Team + Product Owner

---

## Context and Problem Statement

Agents currently execute prompts and built-in skills inside the operator process. Evre 3 introduces **action-oriented agents**: coding agents that run commands and edit files, and conversational agents reached through external channels (WhatsApp, Telegram). Running arbitrary code or long-lived external sessions inside the operator pod is unsafe (no isolation, no resource caps, no cleanup) and operationally fragile.

Beyond execution, the platform needs **observability and management**: operators want to see what agents/sandboxes are running, browse historical execution logs, and manage agents (enable/disable features, add/edit agents, update workflows, human-in-the-loop approvals) driven by agent status.

The platform needs a **harness**: an execution runtime that spawns isolated, ephemeral, resource-bounded sandboxes on demand, a channel layer that connects external messaging endpoints to agents, and a management surface over both.

## Decision Drivers
* **Isolation:** Agent code execution must not run in the operator process. Kernel-level isolation (Kubernetes Pod) is preferred over in-process exec.
* **Ephemerality:** Sandboxes are short-lived, TTL-bounded, and deleted after use (or idle timeout) — no long-running pet pods.
* **Channel Agnosticism:** WhatsApp and Telegram share one inbound message pipeline; more channels are adapter additions.
* **On-Demand:** No sandbox pod exists until an agent actually needs execution; cold start must be bounded.
* **Management First:** Monitoring (what is running, past logs) and management (enable/disable, add/edit agents, update workflows, HITL) are first-class platform concerns, not afterthoughts.
* **Reuse:** Existing `AiAgent` CRD carries capability declarations; the harness is a platform service, not a per-agent deployment.

## Considered Options
1. **In-process exec (`ProcessBuilder`):** Simple, but no isolation, no resource limits, no cleanup guarantees — rejected.
2. **Ephemeral Kubernetes Pods per execution (chosen):** Operator creates a `Job`/bare `Pod` from an agent-selected image, streams commands via exec/attach, TTL-reaps it. Real isolation, native resource limits, natural cleanup.
3. **Sidecar container in the operator pod:** Persistent sandbox per operator, but couples operator lifetime to agent execution and complicates image/resource variance — rejected.

## Decision Outcome

A **Harness service in `tuluat-engine`** (`com.tuluat.engine.harness`) with three layers: sandbox runtime, channel adapters, and a management/observability surface.

### 1. Sandbox Runtime — `SandboxManager`

* **Namespace: `tuluat-sandbox`** (dedicated namespace, decided by product owner — keeps sandbox pods and their RBAC/NetworkPolicies separate from `tuluat-system` operator control plane).
* `SandboxSpec`: `image`, `command`, `cpu`, `memory`, `timeoutSeconds`, `ttlSeconds`, `volumeSize`.
* `SandboxSession start(SandboxSpec spec, String agentName)`:
  * Creates an ephemeral Pod in `tuluat-sandbox` with resource limits, `restartPolicy: Never`, and an emptyDir/PVC workdir.
  * Labels: `app.kubernetes.io/managed-by=tuluat-harness`, `tuluat.ai/agent=<name>`, `tuluat.ai/session=<uuid>`.
* `CommandResult exec(SandboxSession session, String command)`:
  * Streams command via Fabric8 exec with a `PipedInputStream`/output.
  * Enforces `timeoutSeconds`; kills on timeout.
* `void terminate(SandboxSession session)`: deletes the Pod (graceful → force), best-effort.
* **Reaper:** `@Scheduled(fixedDelay = 60s)` scans `tuluat.ai/session`-labeled pods in `tuluat-sandbox`, deletes any past TTL or idle timeout. Prevents leaked sandboxes.
* **Sandbox images (decided): both BYO and curated.**
  * `AiAgentSpec.harness.sandbox.image` allows **bring-your-own** images per agent.
  * A **curated set** (java, python, node, golang) is offered as shorthand via `imageRef: "python"` resolving to a platform-managed registry image; exact tags/registry to be detailed later by the product owner.

### 2. Channel Layer — `ChannelAdapter` SPI

* `ChannelMessage inbound(ChannelEnvelope envelope)` normalizes external messages into `{ channel, channelUserId, text, metadata }`.
* **Channels (decided): WhatsApp and Telegram first** — no Twilio/Meta vendor lock-in decision yet; each adapter targets the official provider API.
* **`WhatsAppChannelAdapter`**:
  * `POST /api/v1/channels/whatsapp/webhook` (Spring MVC controller in `tuluat-app`).
  * Parses **WhatsApp Business Cloud API** payload (`entry[].changes[].value.messages[]`); echo 200 immediately, process async.
  * Verification handshake: `hub.mode=subscribe&hub.verify_token=...&hub.challenge=...`.
  * Outbound replies via `POST {phone-number-id}/messages` with bearer token from env `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID`.
* **`TelegramChannelAdapter`**:
  * Polls or webhook-receives updates from Bot API (`getUpdates` long-poll or `setWebhook`).
  * Token from env `TELEGRAM_BOT_TOKEN`; chat ids from update payload; replies via `sendMessage`.
* **`WebhookChannelAdapter`** (generic): any JSON webhook → same pipeline (test-friendly, WireMock-stubable).

### 3. Management & Observability Surface

Management is **REST API first** (`tuluat-app` controllers; a future Evre 3 web portal consumes the same API). Driven by agent status.

#### Observability
* **What is running:** extend `AiAgentStatus` with a runtime block:
  ```yaml
  status:
    phase: "Ready"            # existing: Ready | Reconciling | Failed
    activeSessions: []        # running WorkflowSession ids
    activeSandboxes: []       # running sandbox pod names + agent
    lastExecutionAt: "…"      # last agent execution timestamp
    lastExecutionStatus: "COMPLETED" | "BLOCKED" | "FAILED"
  ```
* **Past logs:** extend the existing `workflow_session_logs` entity usage with **agent execution logs** (per agent, not only per session): `agent_execution_logs` table — `agent_name`, `session_id`, `node_id`, `level`, `message`, `guardrail_result`, `created_at`. REST: `GET /api/v1/agents/{name}/logs?since=&limit=`.
* **Metrics:** existing Prometheus telemetry extended with `tuluat_agent_runs_total`, `tuluat_agent_guardrail_blocks_total`, `tuluat_sandbox_pods_active`.

#### Management (by agent status)
* **Enable/disable features:** `AiAgentSpec` already carries optional blocks (`guardrails`, `mcpServers`, `a2a`, `harness`, `skills`). Management API patches the CRD spec:
  * `PATCH /api/v1/agents/{name}/features` — toggle guardrails/mcp/a2a/harness enabled flags without full spec rewrite.
  * Reconcile on spec change is already handled by the operator.
* **Add/edit agents:** `POST /api/v1/agents` / `PUT /api/v1/agents/{name}` — validated CRD create/update via the operator (Kubernetes API is the source of truth; controller proxies with validation).
* **Update workflows:** `PUT /api/v1/workflows/{name}` — patch `AiWorkflowSpec` (nodes/edges); operator re-reconciles.
* **Human-in-the-loop:** existing `POST /api/v1/sessions/{id}/approve` retained; **approval inbox** API lists sessions awaiting approval:
  * `GET /api/v1/approvals?status=PENDING` → session, currentNode, input, requestedAt.
  * Approve/reject per session; results feed Temporal `ApprovalSignal`.
* **Status-driven filtering:** list endpoints accept `?status=` filters (`GET /api/v1/agents?status=Failed`, `GET /api/v1/sessions?status=RUNNING`) so the portal can render dashboards per state.

### 4. Agent Binding — `AiAgentSpec.harness` (new optional block)
```yaml
spec:
  harness:
    enabled: true
    type: "CODE"              # CODE | CHANNEL | BOTH
    sandbox:
      imageRef: "python"      # curated shorthand OR
      image: "myorg/custom-runner:1.0"  # bring-your-own
      cpu: "500m"
      memory: "512Mi"
      timeoutSeconds: 300
      ttlSeconds: 600
    channels:
      - type: "WHATSAPP"
        phoneNumber: "+15551234567"
        verifyTokenSecretRef: { name: "whatsapp-secret", key: "verify-token" }
      - type: "TELEGRAM"
        botTokenSecretRef: { name: "telegram-secret", key: "bot-token" }
```
* `CODE`: agent may request a sandbox via `executeShell(command)` tool; output returns to the LLM loop.
* `CHANNEL`: webhook routes inbound messages to the agent's chat loop; replies sent back through the adapter.
* Harness is a **capability declaration** — pods are created on demand, never pre-provisioned.
* **Interaction surface (decided): chat/API only** — no embedded terminal in Evre 3 portal; sandbox interaction happens through the agent's chat loop and REST API.

### 5. Security Model
* Sandbox pods live in `tuluat-sandbox` with a dedicated `ServiceAccount` (no cluster permissions, `automountServiceAccountToken: false`), restrictive `SecurityContext` (runAsNonRoot, readOnlyRootFilesystem, no privileged).
* Outbound network: default allow for model/API access; optional `NetworkPolicy` egress-limited per sandbox.
* Secrets: `verifyTokenSecretRef`, `botTokenSecretRef`, `WHATSAPP_ACCESS_TOKEN` via Kustomize `secretGenerator`, never in manifests.
* Per-agent concurrent sandbox cap (default 1) enforced by `SandboxManager`.

### 6. Out of Scope (this ADR)
* WhatsApp/Telegram provider onboarding/approval (Meta/Telegram-side).
* Persistent dev containers / attachable IDEs (interaction is chat/API).
* Windows/GPU sandbox images (later).
* Exact curated image tag/registry matrix (deferred — product owner will detail).

## Positives
* **Real isolation** with Kubernetes-native resource limits and TTL cleanup in a dedicated namespace.
* **Uniform channel pipeline** — WhatsApp and Telegram are two adapters over one SPI.
* **Management/observability are first-class** — status-driven APIs cover enable/disable, add/edit, workflow updates, HITL inbox.
* **On-demand economics** — zero sandbox pods when no agent executes code.
* **Testable** with Fabric8 KubernetesMockServer for operator tests; WireMock for webhook tests.

## Negatives
* Sandbox cold start (image pull) adds latency to first code execution.
* Operator's ServiceAccount needs RBAC to create pods in `tuluat-sandbox`.
* `kubectl exec` streaming adds complexity vs in-process exec.
* Channel providers (Meta/Telegram) impose their own onboarding and rate limits.

## Resolved Questions
1. **Namespace:** `tuluat-sandbox` (dedicated) — decided.
2. **Channels:** WhatsApp + Telegram first, official provider APIs — decided.
3. **Sandbox images:** both BYO (`sandbox.image`) and curated shorthand (`sandbox.imageRef`) — decided; exact image matrix deferred.
4. **Coding agent UI:** chat/API only, no embedded terminal — decided.

## Open Questions (for later detailing)
1. Curated image registry/tag matrix (`imageRef: java|python|node|golang` → concrete images).
2. Telegram transport: long-poll vs webhook (depends on cluster ingress availability).
3. `tuluat-sandbox` RBAC policy details (which agents may create sandboxes, namespace-level quotas).
4. Agent execution log retention period and rotation.
