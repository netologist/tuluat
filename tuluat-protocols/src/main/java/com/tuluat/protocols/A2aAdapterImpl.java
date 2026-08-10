package com.tuluat.protocols;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP-based A2A adapter. Remote agents are discovered via {@code GET
 * {endpoint}/agents} and executed via {@code POST
 * {endpoint}/agents/{id}/execute}. Uses the JDK {@link HttpClient}.
 */
@Service
@Slf4j
public class A2aAdapterImpl implements A2aAdapter {
private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient;

	public A2aAdapterImpl() {
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	@Override
	public List<A2aAgentDescriptor> discoverAgents(String endpoint) {
		try {
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint + "/agents"))
					.timeout(Duration.ofSeconds(10)).GET().build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				JsonNode body = objectMapper.readTree(response.body());
				List<A2aAgentDescriptor> agents = new ArrayList<>();
				if (body.isArray()) {
					body.forEach(node -> agents.add(parseAgent(node)));
				} else if (body.has("agents") && body.get("agents").isArray()) {
					body.get("agents").forEach(node -> agents.add(parseAgent(node)));
				}
				log.info("A2A discovery at [{}] found {} agent(s)", endpoint, agents.size());
				return agents;
			}
			log.warn("A2A discovery at [{}] returned HTTP {}", endpoint, response.statusCode());
		} catch (Exception e) {
			log.warn("A2A discovery at [{}] failed: {}", endpoint, e.getMessage());
		}
		return List.of();
	}

	@Override
	public A2aExecutionResult executeRemote(String endpoint, String agentId, Map<String, Object> task) {
		try {
			ObjectNodeProxy payload = new ObjectNodeProxy(task);
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint + "/agents/" + agentId + "/execute"))
					.timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload.raw()))).build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				JsonNode body = objectMapper.readTree(response.body());
				String result = body.has("result") ? body.get("result").toString() : response.body();
				log.info("A2A execution on [{}] succeeded", agentId);
				return A2aExecutionResult.ok(agentId, result);
			}
			log.warn("A2A execution on [{}] failed with HTTP {}", agentId, response.statusCode());
			return A2aExecutionResult.failure(agentId, "HTTP " + response.statusCode() + ": " + response.body());
		} catch (Exception e) {
			log.warn("A2A execution on [{}] failed: {}", agentId, e.getMessage());
			return A2aExecutionResult.failure(agentId, e.getMessage());
		}
	}

	@Override
	public boolean advertise(List<A2aAgentDescriptor> localAgents, String endpoint) {
		try {
			ArrayNode array = objectMapper.createArrayNode();
			for (A2aAgentDescriptor agent : localAgents) {
				array.add(objectMapper.createObjectNode().put("agentId", agent.agentId()).put("name", agent.name())
						.put("capability", agent.capability())
						.put("endpoint", agent.endpoint() == null ? "" : agent.endpoint()));
			}
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint + "/agents"))
					.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(array.toString())).build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
			log.info("A2A advertise to [{}]: {}", endpoint, ok ? "ok" : "HTTP " + response.statusCode());
			return ok;
		} catch (Exception e) {
			log.warn("A2A advertise to [{}] failed: {}", endpoint, e.getMessage());
			return false;
		}
	}

	@Override
	public Optional<A2aAgentDescriptor> findAgent(String endpoint, String agentId) {
		return discoverAgents(endpoint).stream().filter(a -> a.agentId().equals(agentId)).findFirst();
	}

	private A2aAgentDescriptor parseAgent(JsonNode node) {
		return new A2aAgentDescriptor(node.path("agentId").asText(""), node.path("name").asText(""),
				node.path("capability").asText(""), node.path("endpoint").asText(null));
	}

	/** Minimal holder to keep the execute payload shape explicit. */
	private record ObjectNodeProxy(Map<String, Object> raw) {
	}
}
