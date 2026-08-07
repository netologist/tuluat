package com.tuluat.engine.gateway;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.crd.provider.ModelFallback;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelGatewayTest {

    private ChatModel primaryModel;
    private ChatModel fallbackModel;
    private ModelGateway gateway;

    @BeforeEach
    void setUp() {
        primaryModel = mock(ChatModel.class);
        fallbackModel = mock(ChatModel.class);
        gateway = new ModelGateway(Map.of(
            "openAiChatModel", primaryModel,
            "ollamaChatModel", fallbackModel
        ));
    }

    private LlmProvider provider(String name, String type, double inCost, double outCost, List<ModelFallback> fallbacks) {
        var p = new LlmProvider();
        p.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("tuluat-system").build());
        p.setSpec(new LlmProviderSpec(type, "http://x", null, "gpt-4o", 0.7, 2048, inCost, outCost, fallbacks));
        return p;
    }

    private ChatResponse response(String text) {
        return response(text, 0, 0);
    }

    private ChatResponse response(String text, int inputTokens, int outputTokens) {
        if (inputTokens > 0 || outputTokens > 0) {
            org.springframework.ai.chat.metadata.Usage usage =
                new org.springframework.ai.chat.metadata.DefaultUsage(inputTokens, outputTokens);
            org.springframework.ai.chat.metadata.ChatResponseMetadata meta =
                org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().usage(usage).build();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), meta);
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private ProviderResolver resolver(LlmProvider... providers) {
        return (name, ns) -> {
            for (LlmProvider p : providers) {
                if (p.getMetadata().getName().equals(name)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        };
    }

    @Test
    void usesPrimaryRouteWhenItSucceeds() {
        LlmProvider p = provider("primary", "OPENAI", 2.0, 5.0, List.of());
        when(primaryModel.call(any(Prompt.class))).thenReturn(response("primary answer"));

        ModelGateway.GatewayCallResult result = gateway.invoke(
            new Prompt("hello"), p, null, resolver(), null, "agent-1");

        assertTrue(result.answer().contains("primary answer"));
        assertEquals("gpt-4o", result.modelUsed());
        assertEquals(false, result.usedFallback());
        verify(primaryModel).call(any(Prompt.class));
        verify(fallbackModel, never()).call(any(Prompt.class));
    }

    @Test
    void fallsBackToNextProviderWhenPrimaryFails() {
        LlmProvider primary = provider("primary", "OPENAI", 2.0, 5.0,
            List.of(new ModelFallback("backup", "tuluat-system", "llama3.2")));
        LlmProvider backup = provider("backup", "OLLAMA", 0.0, 0.0, List.of());
        when(primaryModel.call(any(Prompt.class))).thenThrow(new RuntimeException("rate limited"));
        when(fallbackModel.call(any(Prompt.class))).thenReturn(response("backup answer"));

        ModelGateway.GatewayCallResult result = gateway.invoke(
            new Prompt("hello"), primary, null, resolver(backup), null, "agent-1");

        assertTrue(result.answer().contains("backup answer"));
        assertEquals("llama3.2", result.modelUsed());
        assertTrue(result.usedFallback());
    }

    @Test
    void throwsWhenAllRoutesFail() {
        LlmProvider primary = provider("primary", "OPENAI", 2.0, 5.0,
            List.of(new ModelFallback("backup", "tuluat-system", "llama3.2")));
        when(primaryModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
        when(fallbackModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom2"));

        assertThrows(ModelGateway.ModelGatewayException.class, () ->
            gateway.invoke(new Prompt("hello"), primary, null, resolver(), null, "agent-1"));
    }

    @Test
    void estimatesCostFromProviderPricing() {
        LlmProvider p = provider("primary", "OPENAI", 2.0, 5.0, List.of());
        when(primaryModel.call(any(Prompt.class))).thenReturn(response("answer", 1000, 1000));

        ModelGateway.GatewayCallResult result = gateway.invoke(
            new Prompt("hello"), p, null, resolver(), null, "agent-1");

        // (1000/1000 * 2.0) + (1000/1000 * 5.0) = 7.0
        assertEquals(7.0, result.costUsd(), 0.001);
    }

    @Test
    void blocksExecutionWhenBudgetExceeded() {
        LlmProvider p = provider("primary", "OPENAI", 2.0, 5.0, List.of());
        when(primaryModel.call(any(Prompt.class))).thenReturn(response("first", 1000, 1000));

        gateway.invoke(new Prompt("first"), p, null, resolver(), 0.05, "agent-budget");
        // second call: spent 7.0 >= 0.05 cap
        assertThrows(ModelGateway.BudgetExceededException.class, () ->
            gateway.invoke(new Prompt("second"), p, null, resolver(), 0.05, "agent-budget"));
    }

    @Test
    void resolvesModelFromAgentOverride() {
        LlmProvider p = provider("primary", "OPENAI", 0.0, 0.0, List.of());
        when(primaryModel.call(any(Prompt.class))).thenReturn(response("x"));

        ModelGateway.GatewayCallResult result = gateway.invoke(
            new Prompt("hello"), p, "gpt-4o-mini", resolver(), null, "agent-1");

        assertEquals("gpt-4o-mini", result.modelUsed());
    }

    @Test
    void missingFallbackProviderIsSkipped() {
        LlmProvider primary = provider("primary", "OPENAI", 2.0, 5.0,
            List.of(new ModelFallback("missing", "tuluat-system", "model-x")));
        when(primaryModel.call(any(Prompt.class))).thenThrow(new RuntimeException("down"));

        assertThrows(ModelGateway.ModelGatewayException.class, () ->
            gateway.invoke(new Prompt("hello"), primary, null, resolver(), null, "agent-1"));
    }
}
