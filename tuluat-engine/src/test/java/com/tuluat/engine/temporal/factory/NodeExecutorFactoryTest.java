package com.tuluat.engine.temporal.factory;

import com.tuluat.engine.temporal.model.NodeType;
import com.tuluat.engine.temporal.strategy.AgentNodeExecutor;
import com.tuluat.engine.temporal.strategy.ConditionNodeExecutor;
import com.tuluat.engine.temporal.strategy.HumanApprovalNodeExecutor;
import com.tuluat.engine.temporal.strategy.NodeExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NodeExecutorFactory")
class NodeExecutorFactoryTest {

	private NodeExecutorFactory factory;

	@BeforeEach
	void setUp() {
		factory = new NodeExecutorFactory();
	}

	@Nested
	@DisplayName("getExecutor")
	class GetExecutor {

		@ParameterizedTest(name = "returns {0} for {1} type")
		@MethodSource("provideNodeTypeAndExecutorClass")
		void returnsExecutorByType(NodeType nodeType, Class<NodeExecutor> clazz) {
			assertThat(factory.getExecutor(nodeType)).containsInstanceOf(clazz);
		}

		private static Stream<Arguments> provideNodeTypeAndExecutorClass() {
			return Stream.of(Arguments.of(NodeType.AGENT, AgentNodeExecutor.class),
					Arguments.of(NodeType.CONDITION, ConditionNodeExecutor.class),
					Arguments.of(NodeType.HUMAN_APPROVAL, HumanApprovalNodeExecutor.class));
		}

		@Test
		@DisplayName("returns empty for UNKNOWN type")
		void returnsEmptyForUnknown() {
			assertThat(factory.getExecutor(NodeType.UNKNOWN)).isEmpty();
		}

		@Test
		@DisplayName("returns empty for null type")
		void returnsEmptyForNull() {
			assertThat(factory.getExecutor(null)).isEmpty();
		}
	}

	@Nested
	@DisplayName("strategy correctness")
	class StrategyCorrectness {

		@Test
		@DisplayName("each registered executor reports the correct supported type")
		void eachExecutorReportsCorrectType() {
			assertThat(factory.getExecutor(NodeType.AGENT).orElseThrow().getSupportedType()).isEqualTo(NodeType.AGENT);
			assertThat(factory.getExecutor(NodeType.CONDITION).orElseThrow().getSupportedType())
					.isEqualTo(NodeType.CONDITION);
			assertThat(factory.getExecutor(NodeType.HUMAN_APPROVAL).orElseThrow().getSupportedType())
					.isEqualTo(NodeType.HUMAN_APPROVAL);
		}

		@Test
		@DisplayName("all three known types have distinct executor instances")
		void distinctInstancesPerType() {
			NodeExecutor agent = factory.getExecutor(NodeType.AGENT).orElseThrow();
			NodeExecutor condition = factory.getExecutor(NodeType.CONDITION).orElseThrow();
			NodeExecutor approval = factory.getExecutor(NodeType.HUMAN_APPROVAL).orElseThrow();

			assertThat(agent).isNotSameAs(condition).isNotSameAs(approval);
			assertThat(condition).isNotSameAs(approval);
		}
	}
}
