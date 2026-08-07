package com.tuluat.protocols;

import com.tuluat.crd.mcp.McpServer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry contract for Model Context Protocol (MCP) client connections.
 * Clients register MCP servers (from {@code McpServer} CRDs or programmatically)
 * and invoke tools exported by them.
 */
public interface McpClientRegistry {

    /**
     * Register an MCP server client.
     *
     * @param clientName     unique client name
     * @param serverEndpoint MCP server endpoint URL
     */
    void registerClient(String clientName, String serverEndpoint);

    /**
     * Register an MCP server client from a {@link McpServer} resource.
     */
    void registerFromCr(McpServer server);

    /**
     * Remove a registered client.
     */
    void unregisterClient(String clientName);

    /**
     * All registered clients, keyed by name.
     */
    Map<String, McpClientConnection> getRegisteredClients();

    /**
     * Look up a single registered client.
     */
    Optional<McpClientConnection> findClient(String clientName);

    /**
     * Invoke a tool on a registered MCP server using JSON-RPC over HTTP/SSE.
     *
     * @param clientName registered client name
     * @param toolName   tool to invoke
     * @param arguments  tool arguments
     * @return decoded tool result
     */
    McpToolResult invokeTool(String clientName, String toolName, Map<String, Object> arguments);

    /**
     * Names of all registered clients (sorted), for agent tool resolution.
     */
    List<String> getAvailableClientNames();
}
