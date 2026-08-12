package com.tuluat.protocols;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.tuluat.crd.mcp.McpServer;
import com.tuluat.crd.mcp.McpServerSpec;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientRegistryImplTest {

	private HttpServer server;
	private McpClientRegistryImpl registry;
	private String baseUrl;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/mcp", exchange -> {
			byte[] body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"rows\\\":42}\"}]}}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
		registry = new McpClientRegistryImpl();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void registerAndUnregisterClient() {
		registry.registerClient("postgres-mcp", baseUrl);
		assertTrue(registry.findClient("postgres-mcp").isPresent());
		assertEquals(baseUrl, registry.findClient("postgres-mcp").orElseThrow().endpoint());

		registry.unregisterClient("postgres-mcp");
		assertFalse(registry.findClient("postgres-mcp").isPresent());
	}

	@Test
	void invokesToolOverJsonRpc() {
		registry.registerClient("postgres-mcp", baseUrl);

		McpToolResult result = registry.invokeTool("postgres-mcp", "query", Map.of("sql", "SELECT count(*) FROM t"));

		assertTrue(result.success());
		assertTrue(result.content().contains("rows"));
		assertEquals("query", result.toolName());
	}

	@Test
	void unknownClientReturnsFailure() {
		McpToolResult result = registry.invokeTool("missing", "query", Map.of());
		assertFalse(result.success());
		assertTrue(result.error().contains("No MCP client"));
	}

	@Test
	void availableClientNamesSorted() {
		registry.registerClient("zeta", baseUrl);
		registry.registerClient("alpha", baseUrl);
		assertEquals(java.util.List.of("alpha", "zeta"), registry.getAvailableClientNames());
	}

	@Test
	void registerFromMcpServerCr() {
		var server = new McpServer();
		server.getMetadata().setName("github-mcp");
		server.setSpec(new McpServerSpec("http://github-mcp:8080/sse", "SSE", "NONE", null, 30, "GitHub tools"));

		registry.registerFromCr(server);

		var conn = registry.findClient("github-mcp").orElseThrow();
		assertEquals("http://github-mcp:8080/sse", conn.endpoint());
		assertEquals("SSE", conn.transport());
	}
}
