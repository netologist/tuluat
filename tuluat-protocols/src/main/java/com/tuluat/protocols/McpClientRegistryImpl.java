package com.tuluat.protocols;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tuluat.crd.mcp.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory MCP client registry. Clients register {@link McpServer} resources
 * (via CR reconciliation) or endpoints programmatically; tools are invoked over
 * JSON-RPC using the JDK {@link HttpClient} (virtual-thread friendly on Java
 * 25).
 */
@Service
public class McpClientRegistryImpl implements McpClientRegistry {

	private static final Logger log = LoggerFactory.getLogger(McpClientRegistryImpl.class);

	private final Map<String, McpClientConnection> clients = new ConcurrentHashMap<>();
	private final AtomicLong requestId = new AtomicLong();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient;

	public McpClientRegistryImpl() {
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	@Override
	public void registerClient(String clientName, String serverEndpoint) {
		clients.put(clientName, new McpClientConnection(clientName, serverEndpoint, "SSE", "NONE"));
		log.info("Registered MCP client [{}] -> {}", clientName, serverEndpoint);
	}

	@Override
	public void registerFromCr(McpServer server) {
		McpServerSpecView spec = specOf(server);
		McpClientConnection conn = new McpClientConnection(server.getMetadata().getName(), spec.endpoint(),
				spec.transport(), spec.authType());
		clients.put(conn.name(), conn);
		log.info("Registered MCP client [{}] from CR: {} ({})", conn.name(), conn.endpoint(), conn.transport());
	}

	@Override
	public void unregisterClient(String clientName) {
		clients.remove(clientName);
		log.info("Unregistered MCP client [{}]", clientName);
	}

	@Override
	public Map<String, McpClientConnection> getRegisteredClients() {
		return Map.copyOf(clients);
	}

	@Override
	public Optional<McpClientConnection> findClient(String clientName) {
		return Optional.ofNullable(clients.get(clientName));
	}

	@Override
	public McpToolResult invokeTool(String clientName, String toolName, Map<String, Object> arguments) {
		McpClientConnection client = clients.get(clientName);
		if (client == null) {
			return McpToolResult.failure(toolName, "No MCP client registered with name: " + clientName);
		}
		try {
			ObjectNode request = objectMapper.createObjectNode();
			request.put("jsonrpc", "2.0");
			request.put("id", requestId.incrementAndGet());
			request.put("method", "tools/call");
			ObjectNode params = request.putObject("params");
			params.put("name", toolName);
			params.set("arguments", objectMapper.valueToTree(arguments == null ? Map.of() : arguments));

			HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(client.endpoint()))
					.timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request))).build();

			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				JsonNode body = objectMapper.readTree(response.body());
				JsonNode result = body.get("result");
				if (result != null && result.has("content")) {
					String content = result.get("content").toString();
					log.info("MCP tool [{}] on [{}] succeeded", toolName, clientName);
					return McpToolResult.ok(toolName, content);
				}
				return McpToolResult.ok(toolName, body.toString());
			}
			log.warn("MCP tool [{}] on [{}] failed with HTTP {}", toolName, clientName, response.statusCode());
			return McpToolResult.failure(toolName, "HTTP " + response.statusCode() + ": " + response.body());
		} catch (Exception e) {
			log.warn("MCP tool [{}] on [{}] invocation error: {}", toolName, clientName, e.getMessage());
			return McpToolResult.failure(toolName, e.getMessage());
		}
	}

	@Override
	public List<String> getAvailableClientNames() {
		return clients.keySet().stream().sorted().toList();
	}

	private record McpServerSpecView(String endpoint, String transport, String authType) {
	}

	private McpServerSpecView specOf(McpServer server) {
		var spec = server.getSpec();
		if (spec == null) {
			return new McpServerSpecView("", "SSE", "NONE");
		}
		return new McpServerSpecView(spec.endpoint() == null ? "" : spec.endpoint(),
				spec.transport() == null ? "SSE" : spec.transport(),
				spec.authType() == null ? "NONE" : spec.authType());
	}
}
