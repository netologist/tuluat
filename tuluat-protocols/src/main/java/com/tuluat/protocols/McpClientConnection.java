package com.tuluat.protocols;

/**
 * Immutable snapshot of a registered MCP client connection.
 *
 * @param name      client name
 * @param endpoint  server endpoint URL
 * @param transport transport (SSE, STDIO, HTTP)
 * @param authType  authentication type (NONE, API_KEY, OAUTH2)
 */
public record McpClientConnection(
    String name,
    String endpoint,
    String transport,
    String authType
) {
}
