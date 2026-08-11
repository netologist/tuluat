package com.tuluat.engine.temporal.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NodeType")
class NodeTypeTest {

	@Nested
	@DisplayName("from(String)")
	class From {

		@Test
		@DisplayName("parses uppercase input")
		void parsesUppercase() {
			assertThat(NodeType.from("AGENT")).isEqualTo(NodeType.AGENT);
			assertThat(NodeType.from("CONDITION")).isEqualTo(NodeType.CONDITION);
			assertThat(NodeType.from("HUMAN_APPROVAL")).isEqualTo(NodeType.HUMAN_APPROVAL);
		}

		@Test
		@DisplayName("parses lowercase input case-insensitively")
		void parsesLowercase() {
			assertThat(NodeType.from("agent")).isEqualTo(NodeType.AGENT);
			assertThat(NodeType.from("condition")).isEqualTo(NodeType.CONDITION);
			assertThat(NodeType.from("human_approval")).isEqualTo(NodeType.HUMAN_APPROVAL);
		}

		@Test
		@DisplayName("parses mixed-case input")
		void parsesMixedCase() {
			assertThat(NodeType.from("Agent")).isEqualTo(NodeType.AGENT);
			assertThat(NodeType.from("Human_Approval")).isEqualTo(NodeType.HUMAN_APPROVAL);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "UNSUPPORTED", "garbage", "agent_node", "123"})
		@DisplayName("returns UNKNOWN for invalid input")
		void returnsUnknownForInvalid(String input) {
			assertThat(NodeType.from(input)).isEqualTo(NodeType.UNKNOWN);
		}
	}

	@Test
	@DisplayName("enum has exactly 4 constants")
	void hasFourConstants() {
		assertThat(NodeType.values()).containsExactly(NodeType.AGENT, NodeType.CONDITION, NodeType.HUMAN_APPROVAL,
				NodeType.UNKNOWN);
	}
}
