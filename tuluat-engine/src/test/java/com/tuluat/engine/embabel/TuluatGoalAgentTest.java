package com.tuluat.engine.embabel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.agent.UsageStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TuluatGoalAgent embabel integration")
class TuluatGoalAgentTest {

	private final AgentExecutionService executionService = mock(AgentExecutionService.class);
	private final TuluatGoalAgent goalAgent = new TuluatGoalAgent(executionService);

	@Nested
	@DisplayName("session-aware goal execution")
	class SessionAwareExecution {

		@Test
		@DisplayName("passes sessionId to 4-param AgentExecutionService when provided")
		void passesSessionIdToExecutionService() {
			UUID sessionId = UUID.randomUUID();
			var request = new GoalRequest("test-agent", "Analyze data", Map.of(), sessionId);

			when(executionService.executeAgent(eq("test-agent"), eq("Analyze data"), eq("{}"), eq(sessionId)))
					.thenReturn(AgentResponse.create("test-agent", "m", "sys", "done", List.of(),
							UsageStats.calculate(5, 5, "m", 10)));

			GoalResult result = goalAgent.executeGoal(request);

			assertThat(result.agentName()).isEqualTo("test-agent");
			assertThat(result.answer()).isEqualTo("done");
			verify(executionService).executeAgent(eq("test-agent"), eq("Analyze data"), eq("{}"), eq(sessionId));
		}

		@Test
		@DisplayName("passes null sessionId when not provided")
		void passesNullSessionIdWhenNotProvided() {
			var request = new GoalRequest("test-agent", "Analyze data", Map.of(), null);

			when(executionService.executeAgent(anyString(), anyString(), anyString(), any())).thenReturn(AgentResponse
					.create("test-agent", "m", "sys", "done", List.of(), UsageStats.calculate(5, 5, "m", 10)));

			GoalResult result = goalAgent.executeGoal(request);

			assertThat(result.answer()).isEqualTo("done");
		}

		@Test
		@DisplayName("legacy 3-arg constructor works for backward compatibility")
		void legacyConstructorStillWorks() {
			var request = new GoalRequest("test-agent", "Analyze data", Map.of());

			when(executionService.executeAgent(anyString(), anyString(), anyString(), any())).thenReturn(AgentResponse
					.create("test-agent", "m", "sys", "done", List.of(), UsageStats.calculate(5, 5, "m", 10)));

			GoalResult result = goalAgent.executeGoal(request);

			assertThat(result.answer()).isEqualTo("done");
			assertThat(request.sessionId()).isNull();
		}

		@Test
		@DisplayName("returns typed GoalResult with usage stats")
		void returnsTypedGoalResultWithUsage() {
			var request = new GoalRequest("test-agent", "Analyze data", Map.of(), null);
			var usage = UsageStats.calculate(100, 50, "m", 200);

			when(executionService.executeAgent(anyString(), anyString(), anyString(), any()))
					.thenReturn(AgentResponse.create("test-agent", "m", "sys", "result", List.of(), usage));

			GoalResult result = goalAgent.executeGoal(request);

			assertThat(result.agentName()).isEqualTo("test-agent");
			assertThat(result.answer()).isEqualTo("result");
			assertThat(result.usage()).isNotNull();
		}
	}
}