package com.tuluat.engine.temporal.factory;

import com.tuluat.engine.temporal.model.NodeType;
import com.tuluat.engine.temporal.strategy.NodeExecutor;
import com.tuluat.engine.temporal.strategy.AgentNodeExecutor;
import com.tuluat.engine.temporal.strategy.ConditionNodeExecutor;
import com.tuluat.engine.temporal.strategy.HumanApprovalNodeExecutor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NodeExecutorFactory {

	private final Map<NodeType, NodeExecutor> strategies;

	public NodeExecutorFactory() {
		List<NodeExecutor> executorList = List.of(new AgentNodeExecutor(), new ConditionNodeExecutor(),
				new HumanApprovalNodeExecutor());

		this.strategies = executorList.stream()
				.collect(Collectors.toMap(NodeExecutor::getSupportedType, Function.identity()));
	}

	public Optional<NodeExecutor> getExecutor(NodeType type) {
		return Optional.ofNullable(strategies.get(type));
	}
}