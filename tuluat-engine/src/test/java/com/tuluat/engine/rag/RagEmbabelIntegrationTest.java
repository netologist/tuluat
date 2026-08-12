package com.tuluat.engine.rag;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResolver;
import com.tuluat.engine.embabel.GoalRequest;
import com.tuluat.engine.embabel.GoalResult;
import com.tuluat.engine.embabel.TuluatGoalAgent;
import com.tuluat.engine.gateway.ProviderResolver;
import com.tuluat.engine.rag.embedding.LocalHashEmbeddingProvider;
import com.tuluat.engine.rag.storage.S3ObjectStorage;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.guardrails.GuardrailPipeline;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying the full RAG pipeline backed by real MinIO S3
 * object storage and PostgreSQL + pgvector — driven through the Embabel
 * {@link TuluatGoalAgent} so that the RAG context reaches the agent execution
 * path end-to-end.
 *
 * <p>
 * Requires Docker. When Docker is unavailable the class is skipped
 * automatically ({@link Testcontainers#disabledWithoutDocker()}).
 */
@Testcontainers(disabledWithoutDocker = true)
class RagEmbabelIntegrationTest {

	static final String BUCKET = "rag-documents";

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
			.withDatabaseName("ragitdb").withUsername("ragit").withPassword("ragit");

	@Container
	static MinIOContainer minio = new MinIOContainer("minio/minio");

	static RagService ragService;
	static S3ObjectStorage s3Storage;
	static TuluatGoalAgent embabelAgent;

	@BeforeAll
	static void setUp() throws Exception {
		// ── PostgreSQL + pgvector ──────────────────────────────────────
		var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		var jdbc = new JdbcTemplate(ds);

		jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");

		String migration = new ClassPathResource("db/migration/V3__rag_chunks.sql")
				.getContentAsString(StandardCharsets.UTF_8);
		for (String stmt : migration.split(";")) {
			String trimmed = stmt.trim();
			if (!trimmed.isBlank()) {
				jdbc.execute(trimmed);
			}
		}

		// ── MinIO S3 ───────────────────────────────────────────────────
		s3Storage = new S3ObjectStorage(minio.getS3URL(), BUCKET, minio.getUserName(), minio.getPassword());

		// ── RAG pipeline ───────────────────────────────────────────────
		var retriever = new PgVectorRetriever(jdbc);
		ragService = new RagService(new RecursiveCharacterChunker(), new LocalHashEmbeddingProvider(), retriever,
				s3Storage);

		// ── Agent services (simulated LLM — no real API key) ────────
		var provider = new LlmProvider();
		provider.setMetadata(new ObjectMetaBuilder().withName("openai-provider").withNamespace("default").build());
		provider.setSpec(
				new LlmProviderSpec("OPENAI", "http://localhost", null, "gpt-4o", 0.7, 2048, 0.0, 0.0, List.of()));

		var agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName("rag-agent").withNamespace("default").build());
		agent.setSpec(
				new AiAgentSpec(new ProviderRef("openai-provider", "default"), "gpt-4o", "You are a Kubernetes expert.",
						"Default prompt", List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, 1));

		Map<String, AiAgent> agents = Map.of("rag-agent", agent);
		Map<String, LlmProvider> providers = Map.of("openai-provider", provider);
		AgentResolver agentResolver = (name, ns) -> Optional.ofNullable(agents.get(name));
		ProviderResolver providerResolver = (name, ns) -> Optional.ofNullable(providers.get(name));

		var aes = new AgentExecutionService(new ToolRegistry(), Optional.empty(),
				new GuardrailPipeline(List.of(), List.of()), Optional.empty(), Optional.of(providerResolver),
				Optional.of(agentResolver), Optional.of(ragService));

		embabelAgent = new TuluatGoalAgent(aes);
	}

	// ── S3 roundtrip ─────────────────────────────────────────────────────

	@Test
	void s3ObjectStoragePutAndGetRoundtrip() {
		String key = "documents/kb/roundtrip-test.txt";
		String content = "Kubernetes CRDs allow extending the Kubernetes API with custom resources.";

		s3Storage.put(key, content.getBytes(StandardCharsets.UTF_8), "text/plain");
		var stored = s3Storage.get(key);

		assertTrue(stored.isPresent(), "S3 get must return the stored object");
		assertEquals(content, new String(stored.get().content(), StandardCharsets.UTF_8));
	}

	@Test
	void s3ObjectStorageListAndDelete() {
		String prefix = "documents/kb/listdelete/";
		s3Storage.put(prefix + "one.txt", "one".getBytes(StandardCharsets.UTF_8), "text/plain");
		s3Storage.put(prefix + "two.txt", "two".getBytes(StandardCharsets.UTF_8), "text/plain");

		List<String> keys = s3Storage.list(prefix);
		assertEquals(2, keys.size(), "Must list both objects under the prefix");

		keys.forEach(s3Storage::delete);
		assertTrue(s3Storage.list(prefix).isEmpty(), "All objects must be deleted");
	}

	// ── Retrieval ranking ─────────────────────────────────────────────────

	@Test
	void retrievalReturnsMostRelevantFirst() {
		ragService.ingest("docs/kubernetes", "Kubernetes is a container orchestration platform.");
		ragService.ingest("docs/weather", "Istanbul weather is sunny and warm in summer.");

		RagContext ctx = ragService.retrieve("container orchestration", 2);

		assertEquals(2, ctx.retrieved().size());
		assertTrue(ctx.retrieved().get(0).similarity() >= ctx.retrieved().get(1).similarity(),
				"Most relevant chunk must rank first");
		assertEquals("docs/kubernetes", ctx.retrieved().get(0).sourceRef());
	}

	// ── Embabel goal-agent RAG roundtrip ──────────────────────────────────

	@Test
	void embabelGoalAgentInjectsRagContextForRelevantQuery() {
		ragService.ingest("kb/k8s-crd", "Kubernetes CRDs allow extending the Kubernetes API with custom resources.");

		GoalResult result = embabelAgent
				.executeGoal(new GoalRequest("rag-agent", "Explain custom resources in Kubernetes API", null));

		assertNotNull(result);
		assertNotNull(result.answer());
		// Simulated LLM echoes the system prompt, which must contain the
		// RAG context block injected by invokeResolvedAgent (ADR 008 / Embabel path).
		assertTrue(result.answer().contains("Relevant Document Context (RAG):"),
				"Answer must contain the RAG context header");
		assertTrue(result.answer().contains("kb/k8s-crd"), "Answer must reference the ingested source ref");
		assertTrue(result.answer().contains("Kubernetes CRDs allow extending"),
				"Answer must contain the ingested document content");
	}

	@Test
	void ragContextMissingForUnrelatedQuery() {
		ragService.ingest("docs/weather", "Istanbul weather is sunny and warm in summer.");
		ragService.ingest("kb/kubernetes",
				"Kubernetes is a container orchestration platform. " + "It manages deployments, services, and ingress. "
						+ "This is filler to ensure a second chunk. " + "More filler text for chunking purposes.");

		GoalResult result = embabelAgent
				.executeGoal(new GoalRequest("rag-agent", "What is the weather in Istanbul?", null));

		assertNotNull(result.answer());
		// Weather query should retrieve weather doc, not Kubernetes doc
		assertTrue(result.answer().contains("docs/weather") || result.answer().contains("sunny"),
				"Weather-related query must retrieve weather doc context");
	}

	@Test
	void promptBlockContainsAllChunksForShortDocument() {
		ragService.ingest("runbooks/on-call", "Production incident: restart the pod with kubectl rollout restart.");

		GoalResult result = embabelAgent.executeGoal(new GoalRequest("rag-agent", "How do I restart a pod?", null));

		String block = result.answer();
		assertTrue(block.contains("Relevant Document Context (RAG):"));
		assertTrue(block.contains("runbooks/on-call"));
		assertTrue(block.contains("kubectl rollout restart"));
	}
}
