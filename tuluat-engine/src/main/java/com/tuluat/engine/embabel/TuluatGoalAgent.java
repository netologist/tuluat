package com.tuluat.engine.embabel;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Embabel {@link Agent} that executes Tuluat AI agent goals through the
 * {@link AgentExecutionService} pipeline.
 *
 * <h3>Embabel GOAP integration</h3>
 * <p>
 * The Embabel framework's Goal-Oriented Action Planning (GOAP) engine
 * automatically discovers this agent's actions and plans execution. For
 * single-step goal execution the planning is trivial; when multi-step workflows
 * are added (research → verify → report), GOAP dynamically sequences them based
 * on typed input/output contracts.
 *
 * <h3>Pipeline</h3>
 * <ol>
 * <li>Embabel planner resolves this agent for goal requests</li>
 * <li>{@link #executeGoal(GoalRequest)} delegates to
 * {@link AgentExecutionService}</li>
 * <li>Guardrails, skills, RAG, and model gateway run as configured</li>
 * <li>Result flows back through Embabel's typed data graph</li>
 * </ol>
 */
@Agent(description = "Executes AI agent goals using the Tuluat agent execution pipeline")
@Slf4j
public class TuluatGoalAgent {

	private final AgentExecutionService agentExecutionService;

	public TuluatGoalAgent(AgentExecutionService agentExecutionService) {
		this.agentExecutionService = agentExecutionService;
	}

	/**
	 * Executes a goal by delegating to the named AI agent through the full Tuluat
	 * execution pipeline (guardrails → skills → RAG → model gateway).
	 *
	 * @param request
	 *            typed goal request with agent name, description, and context
	 * @return the agent's answer wrapped in a typed result
	 */
	@AchievesGoal(description = "Completes the goal by executing the named AI agent")
	@Action
	public GoalResult executeGoal(GoalRequest request) {
		log.info("Embabel Agent: executing goal '{}' via agent '{}'", request.goalDescription(), request.agentName());

		AgentResponse response = agentExecutionService.executeAgent(request.agentName(), request.goalDescription(),
				request.context() != null ? request.context().toString() : null);
		log.info("Embabel Agent: goal '{}' completed, model={}, tokens={}", request.goalDescription(), response.model(),
				response.usage().totalTokens());

		return new GoalResult(request.agentName(), response.answer(), response.usage());
	}
}
