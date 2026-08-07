# ADR 009: Agent Harness — Sandboxed Execution Runtime

* **Status:** Proposed (design pending user review)
* **Date:** 2026-08-07
* **Deciders:** Software Architecture Team

---

## Context and Problem Statement

Agents currently execute prompts and built-in skills inside the operator process. Evre 3 introduces **action-oriented agents**: coding agents that run commands and edit files, and conversational agents reached through external channels (e.g. WhatsApp). Running arbitrary code or long-lived external sessions inside the operator pod is unsafe (no isolation, no resource caps, no cleanup) and operationally fragile.

The platform needs a **harness**: an execution runtime that spawns isolated, ephemeral, resource-bounded sandboxes on demand, and a channel layer that connects external messaging endpoints to agents.

## Decision Drivers
* **Isolation:** Agent code execution must not run in the operator process. Kernel-level isolation (Kubernetes Pod) is preferred over in-process exec.
* **Ephemerality:** Sandboxes are short-lived, TTL-bounded, and deleted after use (or idle timeout) — no long-running pet pods.
* **Channel Agnosticism:** WhatsApp, webhook, and future channels (Slack, Telegram) share one inbound message pipeline.
* **On-Demand:** No sandbox pod exists until an agent actually needs execution; cold start must be bounded.
* **Reuse:** Existing `AiAgent` CRD carries capability declarations; the harness is a platform service, not a per-agent deployment.

## Considered Options
1. **In-process exec (`ProcessBuilder`):** Simple, but no isolation, no resource limits, no cleanup guarantees — rejected.
2. **Ephemeral Kubernetes Pods per execution (chosen):** Operator creates a `Job`/bare `Pod` from an agent-selected image, streams commands via exec/attach, TTL-reaps it. Real isolation, native resource limits, natural cleanup.
3. **Sidecar container in the operator pod:** Persistent sandbox per operator, but couples operator lifetime to agent execution and complicates image/resource variance — rejected.

## Decision Outcome

A **Harness service in `tuluat-engine`** (`com.tuluat.engine.harness`) with two layers:

### 1. Sandbox Runtime — `SandboxManager`
* `SandboxSpec`: `image`, `command`, `cpu`, `memory`, `timeoutSeconds`, `ttlSeconds`, `volumeSize`.
* `SandboxSession start(SandboxSpec spec, String agentName)`:
  * Creates an ephemeral Pod in `tuluat-system` (or a dedicated `tuluat-harness` namespace) with resource limits, `restartPolicy: Never`, and an emptyDir/PVC workdir.
  * Labels: `app.kubernetes.io/managed-by=tuluat-harness`, `tuluat.ai/agent=<name>`, `tuluat.ai/session=<uuid>`.
* `CommandResult exec(SandboxSession session, String command)`:
  * Streams command over `kubectl exec`-equivalent (Fabric8 `PodResource#exec` with a `PipedInputStream`/output).
  * Enforces `timeoutSeconds`; kills on timeout.
* `void terminate(SandboxSession session)`:
  * Deletes the Pod (graceful → force), best-effort.
* **Reaper:** a scheduled task (`@Scheduled(fixedDelay = 60s)`) scans `tuluat.ai/session`-labeled pods, deletes any past their TTL or idle timeout. Prevents leaked sandboxes.
* **Cold-start budget:** sandbox image prefetching is out of scope; document expected 5–30s pod start in E2E.

### 2. Channel Layer — `ChannelAdapter` SPI
* `ChannelMessage inbound(ChannelEnvelope envelope)` normalizes external messages into `{ channel, channelUserId, text, metadata }`.
* **`WhatsAppChannelAdapter`** (first implementation):
  * Exposes `POST /api/v1/channels/whatsapp/webhook` (Spring MVC controller in `tuluat-app`).
  * Parses the **WhatsApp Business Cloud API** payload (`entry[].changes[].value.messages[]`: `from`, `text.body`); echoes a 200 immediately, processes async.
  * Optional verification handshake: `hub.mode=subscribe&hub.verify_token=...&hub.challenge=...` (Meta requirement).
  * Outbound replies via `POST {phone-number-id}/messages` with a bearer token from env `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID`.
* **WebhookChannelAdapter** (generic): any JSON webhook → same pipeline (test-friendly, WireMock-stubable).

### 3. Agent Binding — `AiAgentSpec.harness` (new optional block)
```yaml
spec:
  harness:
    enabled: true
    type: "CODE"              # CODE | CHANNEL | BOTH
    sandbox:
      image: "eclipse-temurin:25-jdk"
      command: ["/bin/bash"]
      cpu: "500m"
      memory: "512Mi"
      timeoutSeconds: 300
      ttlSeconds: 600
    channels:
      - type: "WHATSAPP"
        phoneNumber: "+15551234567"
        verifyTokenSecretRef: { name: "whatsapp-secret", key: "verify-token" }
```
* `CODE`: agent may request a sandbox via `executeShell(command)` tool; output returns to the LLM loop.
* `CHANNEL`: webhook routes inbound messages to the agent's chat loop; replies sent back through the adapter.
* Harness is a **capability declaration** — pods are created on demand, never pre-provisioned.

### 4. Security Model
* Sandbox pods use a dedicated `ServiceAccount` with no cluster permissions (no `automountServiceAccountToken`), a `restrictive` `SecurityContext` (runAsNonRoot, readOnlyRootFilesystem, no privileged).
* Outbound network: default allow for model/API access; optionally `NetworkPolicy` egress-limited per sandbox.
* Secrets: `verifyTokenSecretRef` and `WHATSAPP_ACCESS_TOKEN` via Kustomize `secretGenerator`, never in manifests.
* Per-agent concurrent sandbox cap (default 1) to bound resource usage; enforced by `SandboxManager`.

### 5. Out of Scope (this ADR)
* WhatsApp Business API approval/onboarding (Meta-side).
* Persistent dev containers / attachable IDEs.
* Windows/GPU sandbox images (later).

## Positives
* **Real isolation** with Kubernetes-native resource limits and TTL cleanup.
* **Uniform channel pipeline** — WhatsApp first, Slack/Telegram add one adapter each.
* **On-demand economics** — zero sandbox pods when no agent executes code.
* **Testable with Fabric8 KubernetesMockServer** for operator tests; WireMock for webhook tests.

## Negatives
* Sandbox cold start (image pull) adds latency to first code execution.
* Requires the operator's ServiceAccount to create pods in the harness namespace (RBAC addition).
* `kubectl exec` streaming adds complexity vs in-process exec.

## Open Questions (for user review)
1. **Namespace:** sandbox pods in `tuluat-system` vs dedicated `tuluat-harness`?
2. **WhatsApp provider:** Meta WhatsApp Business Cloud API (official) vs Twilio WhatsApp (simpler sandbox onboarding)?
3. **Sandbox images:** bring-your-own per agent, or a small curated set (java, python, node, golang)?
4. **Coding agent UI:** is the coding agent driven purely via chat/API, or does Evre 3's web portal need an embedded terminal?
