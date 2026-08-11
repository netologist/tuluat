package com.tuluat.app.controller;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.tool.ToolResult;
import com.tuluat.engine.agent.UsageStats;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentChatControllerTest {

	private KubernetesClient client;
	private AgentExecutionService agentExecutionService;
	private AgentChatController controller;

	private MixedOperation aiAgentsMock;
	private NonNamespaceOperation agentNsMock;
	private Resource agentResMock;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		client = mock(KubernetesClient.class);
		agentExecutionService = mock(AgentExecutionService.class);
		controller = new AgentChatController(client, agentExecutionService);

		aiAgentsMock = mock(MixedOperation.class);
		agentNsMock = mock(NonNamespaceOperation.class);
		agentResMock = mock(Resource.class);

		MixedOperation llmProvidersMock = mock(MixedOperation.class);
		NonNamespaceOperation llmNsMock = mock(NonNamespaceOperation.class);
		Resource llmResMock = mock(Resource.class);

		when(client.resources(AiAgent.class)).thenReturn(aiAgentsMock);
		when(aiAgentsMock.inNamespace(anyString())).thenReturn(agentNsMock);
		when(agentNsMock.withName(anyString())).thenReturn(agentResMock);

		when(client.resources(LlmProvider.class)).thenReturn(llmProvidersMock);
		when(llmProvidersMock.inNamespace(anyString())).thenReturn(llmNsMock);
		when(llmNsMock.withName(anyString())).thenReturn(llmResMock);
	}

	@Test
	@DisplayName("Should return 404 when requested AiAgent CR does not exist")
	void testChatWithNonExistentAgent() {
		when(agentResMock.get()).thenReturn(null);

		ResponseEntity<AgentResponse> response = controller.chatWithAgent("missing-agent",
				new ChatRequest("Hello", "default"));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("missing-agent", response.getBody().agentName());
	}

	@Test
	@DisplayName("Should process chat request when AiAgent CR exists in Kubernetes")
	void testChatWithExistentAgent() {
		var agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName("support-agent").withNamespace("tuluat-system").build());
		agent.setSpec(new AiAgentSpec(new ProviderRef("openai-provider", "default"), "gpt-4o", "System prompt",
				"User prompt", List.of(new ToolDefinition("calculator", "Math", true, Map.of())), List.of(), // toolSources
				List.of(), // mcpServers
				null, // guardrails
				null, // a2a
				null, // ingress
				1));
		when(agentResMock.get()).thenReturn(agent);

		var expectedResponse = AgentResponse.create("support-agent", "gpt-4o", "System prompt", "42 is the answer",
				List.of(ToolResult.success("calculator", "42")), UsageStats.calculate(10, 10, "gpt-4o", 15L));
		when(agentExecutionService.processAgentPrompt(any(), any(), anyString())).thenReturn(expectedResponse);

		ResponseEntity<AgentResponse> response = controller.chatWithAgent("support-agent",
				new ChatRequest("What is 6 * 7?", "tuluat-system"));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("42 is the answer", response.getBody().answer());
		assertEquals("support-agent", response.getBody().agentName());
	}
}
