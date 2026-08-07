# ADR 002: Transitioning Agentic Abstractions to Embabel AI Framework

* **Status:** Accepted  
* **Date:** 2026-08-06  
* **Deciders:** Software Architecture Team  

---

## Context and Problem Statement
Spring AI (`spring-ai-starter`) provides excellent low-level abstractions for `ChatModel`, `EmbeddingModel`, and `VectorStore`. However, Spring AI lacks high-level agentic abstractions such as Goal-Oriented Action Planning (GOAP), dynamic multi-step agent reasoning loops, and structured object-oriented agent state management.

## Decision Drivers
* **Goal-Oriented Reasoning:** Agents must dynamically plan actions to achieve declarative targets rather than following fixed static scripts.
* **Spring Ecosystem Alignment:** The framework should seamlessly integrate with Spring Boot 3.x, Virtual Threads, and dependency injection.
* **Maintainability:** Avoid reinventing low-level agent loop state management.

## Considered Options
1. **Plain Spring AI + Custom Graph Engine (Status Quo):** Requires manual implementation of goal planning, context windows, and tool routing.
2. **Embabel AI Framework:** Object-oriented Java/Kotlin framework for Goal-Oriented AI Agents created by Spring Framework founder Rod Johnson.
3. **LangChain4j:** Alternative Java AI framework, but heavily opinionated and less aligned with goal-oriented Spring architectures.

## Decision Outcome
**Chosen Option:** **Embabel AI Framework (Option 2)** for high-level Agent Goal/Action planning, while retaining Spring AI for low-level vector store and model bindings.

### Positives
* **Goal-Oriented Agentic Loop:** Enables agents to dynamically decide actions and tool executions based on goals.
* **Clean Spring Integration:** Designed specifically for Spring Boot and modern Java paradigms.
* **Strong Typing & Object Models:** Clear domain boundary between goals, actions, and model providers.

### Negatives
* Embabel is an emerging library requiring team familiarization.
