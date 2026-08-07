package com.tuluat.protocols;

/**
 * Interface contract for Model Context Protocol (MCP) Client Registry.
 */
public interface McpClientRegistry {
    void registerClient(String clientName, String serverEndpoint);
}
