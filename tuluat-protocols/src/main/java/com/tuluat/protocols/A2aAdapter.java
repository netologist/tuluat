package com.tuluat.protocols;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contract for the Agent-to-Agent (A2A) protocol adapter: discovery of remote
 * agents and remote execution / handoff across clusters.
 */
public interface A2aAdapter {

    /**
     * Discover agents exposed by a remote A2A endpoint.
     *
     * @param endpoint base URL of the remote A2A gateway
     * @return descriptors of remotely available agents
     */
    List<A2aAgentDescriptor> discoverAgents(String endpoint);

    /**
     * Execute a task on a remote agent (handoff).
     *
     * @param endpoint    base URL of the remote A2A gateway
     * @param agentId     remote agent id
     * @param task        task payload
     * @return remote execution result
     */
    A2aExecutionResult executeRemote(String endpoint, String agentId, Map<String, Object> task);

    /**
     * Advertise this platform's own agents at the given A2A endpoint
     * (self-registration for cross-cluster discovery).
     */
    boolean advertise(List<A2aAgentDescriptor> localAgents, String endpoint);

    /**
     * Look up a remote agent by id across a discovery endpoint.
     */
    Optional<A2aAgentDescriptor> findAgent(String endpoint, String agentId);
}
