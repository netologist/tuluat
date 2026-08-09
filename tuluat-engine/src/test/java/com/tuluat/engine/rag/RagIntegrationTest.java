package com.tuluat.engine.rag;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.agent.UsageStats;
import com.tuluat.engine.rag.embedding.LocalHashEmbeddingProvider;
import com.tuluat.engine.rag.storage.LocalObjectStorage;
import com.tuluat.engine.skill.SkillRegistry;
import com.tuluat.guardrails.GuardrailPipeline;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagIntegrationTest {

    @TempDir
    Path tempDir;

    private RagService ragService;
    private AgentExecutionService agentExecutionService;

    @BeforeEach
    void setUp() {
        LocalHashEmbeddingProvider embeddings = new LocalHashEmbeddingProvider();
        InMemoryRetriever retriever = new InMemoryRetriever();
        LocalObjectStorage storage = new LocalObjectStorage(tempDir.toString());
        ragService = new RagService(new RecursiveCharacterChunker(), embeddings, retriever, storage);

        agentExecutionService = new AgentExecutionService(
            new SkillRegistry(),
            null, // simulated execution
            new GuardrailPipeline(List.of(), List.of()),
            null,
            null,
            null,
            ragService
        );
    }

    @Test
    void processAgentPromptInjectsRagContextWhenRelevantDocsExist() {
        // Ingest domain context into RAG
        ragService.ingest("kb/k8s-crd", "Kubernetes CRDs allow extending the Kubernetes API with custom resources.");

        var agent = new AiAgent();
        agent.setMetadata(new ObjectMetaBuilder().withName("rag-agent").withNamespace("default").build());
        agent.setSpec(new AiAgentSpec(
            new ProviderRef("openai-provider", "default"),
            "gpt-4o",
            "You are a Kubernetes expert.",
            "Default prompt",
            List.of(), List.of(), List.of(), null, null, null, 1
        ));

        var provider = new LlmProvider();
        provider.setMetadata(new ObjectMetaBuilder().withName("openai-provider").withNamespace("default").build());
        provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", null, "gpt-4o", 0.7, 2048, 0.0, 0.0, List.of()));

        AgentResponse response = agentExecutionService.processAgentPrompt(agent, provider, "Explain custom resources in Kubernetes API");

        assertNotNull(response);
        assertTrue(response.systemPrompt().contains("Relevant Document Context (RAG):"));
        assertTrue(response.systemPrompt().contains("kb/k8s-crd"));
        assertTrue(response.systemPrompt().contains("Kubernetes CRDs allow extending"));
    }
}
