package com.tuluat.ai.controller;

import com.tuluat.ai.crd.agent.AiAgent;
import com.tuluat.ai.crd.agent.AiAgentSpec;
import com.tuluat.ai.crd.agent.ProviderRef;
import com.tuluat.ai.crd.agent.SkillDefinition;
import com.tuluat.ai.crd.provider.LlmProvider;
import com.tuluat.ai.engine.AgentExecutionService;
import com.tuluat.ai.engine.AgentResponse;
import com.tuluat.ai.engine.skill.SkillResult;
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

        when(client.resources(eq(AiAgent.class))).thenReturn(aiAgentsMock);
        when(aiAgentsMock.inNamespace(anyString())).thenReturn(agentNsMock);
        when(agentNsMock.withName(anyString())).thenReturn(agentResMock);

        // Also mock LlmProvider resources
        MixedOperation providersMock = mock(MixedOperation.class);
        NonNamespaceOperation providerNsMock = mock(NonNamespaceOperation.class);
        Resource providerResMock = mock(Resource.class);
        when(client.resources(eq(LlmProvider.class))).thenReturn(providersMock);
        when(providersMock.inNamespace(anyString())).thenReturn(providerNsMock);
        when(providerNsMock.withName(anyString())).thenReturn(providerResMock);
    }

    @Test
    @DisplayName("Should return 404 when requested AiAgent CR does not exist")
    void testChatWithNonExistentAgent() {
        when(agentResMock.get()).thenReturn(null);
        ResponseEntity<AgentResponse> response = controller.chatWithAgent("unknown-agent", new ChatRequest("hello", "default"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().answer().contains("not found"));
    }

    @Test
    @DisplayName("Should process chat request when AiAgent CR exists in Kubernetes")
    void testChatWithExistentAgent() {
        var agent = new AiAgent();
        agent.setMetadata(new ObjectMetaBuilder().withName("support-agent").withNamespace("default").build());
        agent.setSpec(new AiAgentSpec(
            new ProviderRef("openai-provider", "default"),
            "gpt-4o",
            "System prompt",
            "User prompt",
            List.of(new SkillDefinition("calculator", "Math", true, Map.of())),
            null,
            1
        ));
        when(agentResMock.get()).thenReturn(agent);

        var expectedResponse = AgentResponse.create(
            "support-agent", "gpt-4o", "System prompt", "42 is the answer",
            List.of(SkillResult.success("calculator", "42")), 15L
        );
        when(agentExecutionService.processAgentPrompt(any(), any(), anyString()))
            .thenReturn(expectedResponse);

        ResponseEntity<AgentResponse> response = controller.chatWithAgent("support-agent", new ChatRequest("What is 6 * 7?", "default"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("support-agent", response.getBody().agentName());
        assertEquals("42 is the answer", response.getBody().answer());
    }
}
