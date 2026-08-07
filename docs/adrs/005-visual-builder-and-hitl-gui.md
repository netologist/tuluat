# ADR 005: Architecture for Visual Workflow Builder and Human-in-the-Loop Web Portal

* **Status:** Accepted  
* **Date:** 2026-08-06  
* **Deciders:** Software Architecture Team  

---

## Context and Problem Statement
To productize the AI Operator into an enterprise SaaS/PaaS product, non-developer users and domain experts need a visual interface to design workflows, monitor execution progress, review agent outputs, and approve human-in-the-loop nodes.

## Decision Drivers
* **User Accessibility:** Allow visual drag-and-drop workflow construction (`React Flow`).
* **Human-in-the-Loop:** Provide an inbox UI for approving or rejecting workflow steps in state `WAITING_APPROVAL`.
* **Observability:** Display real-time execution graphs, logs, and token cost analytics.

## Decision Outcome
**Chosen Option:** Build a modern React-based Web Portal using **XYFlow / React Flow** for the canvas, **Tailwind CSS** for UI, and **STOMP WebSockets** for real-time streaming.

### Key Components
1. **Visual Builder Canvas:** Drag-and-drop nodes (`AGENT`, `CONDITION`, `TOOL`, `HUMAN_APPROVAL`), automatically serializing to `AiWorkflow` CRD YAML.
2. **Approval Inbox:** Dedicated UI list for pending human-in-the-loop tasks with diff preview, edit capabilities, and approve/reject actions.
3. **Analytics Dashboard:** Metrics view showing session success rates, node durations, and LLM token expenditures.

### Positives
* **Productization:** Transforms complex Kubernetes CRDs into an intuitive SaaS user experience.
* **Low Friction Approval:** Business users can inspect and approve agent actions effortlessly.
