package com.tuluat.protocols;

/**
 * Descriptor of a remotely discoverable agent.
 *
 * @param agentId   stable agent identifier
 * @param name      human-readable agent name
 * @param capability declared capability / description
 * @param endpoint  agent-specific execution endpoint (optional)
 */
public record A2aAgentDescriptor(
    String agentId,
    String name,
    String capability,
    String endpoint
) {
}
