package com.tuluat.protocols;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aAdapterImplTest {

	private HttpServer server;
	private A2aAdapterImpl adapter;
	private String baseUrl;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/agents", exchange -> {
			String path = exchange.getRequestURI().getPath();
			byte[] body;
			if (path.equals("/agents")) {
				body = """
						{"agents":[{"agentId":"remote-writer","name":"Remote Writer","capability":"report-generation","endpoint":"http://remote:8080"}]}
						"""
						.getBytes(StandardCharsets.UTF_8);
			} else if (path.endsWith("/execute")) {
				body = "{\"result\":{\"output\":\"remote report done\"}}".getBytes(StandardCharsets.UTF_8);
			} else {
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
		adapter = new A2aAdapterImpl();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void discoversRemoteAgents() {
		List<A2aAgentDescriptor> agents = adapter.discoverAgents(baseUrl);

		assertEquals(1, agents.size());
		A2aAgentDescriptor a = agents.get(0);
		assertEquals("remote-writer", a.agentId());
		assertEquals("report-generation", a.capability());
	}

	@Test
	void findsAgentById() {
		Optional<A2aAgentDescriptor> found = adapter.findAgent(baseUrl, "remote-writer");
		assertTrue(found.isPresent());
		assertEquals("Remote Writer", found.orElseThrow().name());

		assertFalse(adapter.findAgent(baseUrl, "nope").isPresent());
	}

	@Test
	void executesRemoteTask() {
		A2aExecutionResult result = adapter.executeRemote(baseUrl, "remote-writer", Map.of("task", "write report"));

		assertTrue(result.success());
		assertTrue(result.result().contains("remote report done"));
		assertEquals("remote-writer", result.remoteAgentId());
	}

	@Test
	void advertiseReturnsTrue() {
		boolean ok = adapter.advertise(List.of(new A2aAgentDescriptor("local-agent", "Local", "research", "")),
				baseUrl);
		assertTrue(ok);
	}

	@Test
	void unreachableEndpointReturnsEmpty() {
		List<A2aAgentDescriptor> agents = adapter.discoverAgents("http://127.0.0.1:1");
		assertTrue(agents.isEmpty());
	}
}
