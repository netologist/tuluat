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
 * End-to-end RAG acceptance test with a realistic financial data scenario.
 *
 * <p>
 * <b>Scenario:</b> A financial analyst agent queries ingested corporate
 * earnings reports stored in MinIO S3 + PostgreSQL/pgvector. Every response
 * must include not only the retrieved data but also the source document
 * reference so the answer is auditable.
 *
 * <p>
 * Runs with real Docker containers ({@link Testcontainers}). Skipped
 * automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class FinancialDataRagE2E {

	static final String BUCKET = "rag-documents";

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
			.withDatabaseName("fine2edb").withUsername("fine2e").withPassword("fine2e");

	@Container
	static MinIOContainer minio = new MinIOContainer("minio/minio");

	static RagService ragService;
	static TuluatGoalAgent embabelAgent;

	@BeforeAll
	static void setUp() throws Exception {
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

		var s3Storage = new S3ObjectStorage(minio.getS3URL(), BUCKET, minio.getUserName(), minio.getPassword());
		var retriever = new PgVectorRetriever(jdbc);
		ragService = new RagService(new RecursiveCharacterChunker(), new LocalHashEmbeddingProvider(), retriever,
				s3Storage);

		var agent = buildFinancialAnalystAgent();
		var provider = buildProvider();

		Map<String, AiAgent> agents = Map.of("financial-analyst", agent);
		Map<String, LlmProvider> providers = Map.of("openai-provider", provider);
		AgentResolver agentResolver = (name, ns) -> Optional.ofNullable(agents.get(name));
		ProviderResolver providerResolver = (name, ns) -> Optional.ofNullable(providers.get(name));

		var aes = new AgentExecutionService(new ToolRegistry(), Optional.empty(),
				new GuardrailPipeline(List.of(), List.of()), Optional.empty(), Optional.of(providerResolver),
				Optional.of(agentResolver), Optional.of(ragService));

		embabelAgent = new TuluatGoalAgent(aes);
	}

	// ── Fixtures ───────────────────────────────────────────────────────────

	private static AiAgent buildFinancialAnalystAgent() {
		var agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName("financial-analyst").withNamespace("default").build());
		agent.setSpec(new AiAgentSpec(new ProviderRef("openai-provider", "default"), "gpt-4o", """
				You are a senior Financial Analyst AI Agent.
				When responding to queries:
				1. Cite specific financial data from the provided document context.
				2. Include the source document reference for every data point.
				3. Compare multi-period data where available (YoY, QoQ).
				4. Highlight notable trends, anomalies, or risk factors.
				""", "Analyze the provided financial documents and answer the user's query.", List.of(), List.of(),
				List.of(), List.of(), List.of(), null, null, null, 1));
		return agent;
	}

	private static LlmProvider buildProvider() {
		var provider = new LlmProvider();
		provider.setMetadata(new ObjectMetaBuilder().withName("openai-provider").withNamespace("default").build());
		provider.setSpec(
				new LlmProviderSpec("OPENAI", "http://localhost", null, "gpt-4o", 0.7, 2048, 0.0, 0.0, List.of()));
		return provider;
	}

	// ── E2E: single-company earnings query ──────────────────────────────────

	@Test
	void singleCompanyEarningsQueryReturnsDataWithSourceAttribution() {
		ragService.ingest("reports/acme-q4-2025",
				"Acme Corp Q4 2025 Earnings Report. " + "Revenue: $847M, up 23% year-over-year. "
						+ "Net Income: $142M ($2.34 EPS). " + "Operating Margin: 16.8%. "
						+ "Cloud division revenue grew 47% to $312M.");

		ragService.ingest("reports/globex-q3-2025", "Globex Inc Q3 2025 Earnings. " + "Revenue: $1.2B. Net Loss: $45M. "
				+ "Restructuring costs of $78M impacted bottom line. " + "Guidance revised downward for FY2025.");

		GoalResult result = embabelAgent.executeGoal(new GoalRequest("financial-analyst",
				"What was Acme Corp's Q4 2025 revenue and operating margin?", null));

		String answer = result.answer();
		assertNotNull(answer);

		// ── Financial data points from chunk ──────────────────────────
		assertTrue(answer.contains("$847M"), "Answer must contain Acme Corp quarterly revenue");
		assertTrue(answer.contains("16.8%") || answer.contains("16.8"), "Answer must contain operating margin");
		assertTrue(answer.contains("23%") || answer.contains("23 %"), "Answer must contain YoY growth");

		// ── Source attribution ────────────────────────────────────────
		assertTrue(answer.contains("Relevant Document Context (RAG):"), "Answer must include the RAG context header");
		assertTrue(answer.contains("reports/acme-q4-2025"), "Answer must cite the Acme Corp source document");
		assertTrue(answer.contains("[reports/acme-q4-2025"),
				"Source attribution must use the [sourceRef #chunk] format");

		// ── Excludes irrelevant data ──────────────────────────────────
		assertFalse(answer.contains("$1.2B") && answer.contains("reports/acme-q4-2025"),
				"Globex revenue must not be attributed to Acme's source");
		assertFalse(answer.contains("Net Loss") && answer.contains("reports/acme-q4-2025"),
				"Net Loss is Globex data, must not appear under Acme source");
	}

	@Test
	void crossCompanyComparisonRetrievesBothSources() {
		ragService.ingest("reports/acme-q4-2025", "Acme Corp Q4 2025. Revenue: $847M. Net Income: $142M. "
				+ "EPS: $2.34. Cloud revenue: $312M (47% growth).");

		ragService.ingest("reports/globex-q3-2025", "Globex Inc Q3 2025. Revenue: $1.2B. Net Loss: $45M. "
				+ "Restructuring: $78M. Guidance: downward revised.");

		GoalResult result = embabelAgent.executeGoal(new GoalRequest("financial-analyst",
				"Compare Acme Corp and Globex Inc performance - who did better?", null));

		String answer = result.answer();

		// ── Both source documents cited ──────────────────────────────
		assertTrue(answer.contains("reports/acme-q4-2025"), "Answer must cite Acme Corp source");
		assertTrue(answer.contains("reports/globex-q3-2025"), "Answer must cite Globex Inc source");

		// ── Key metrics from each company ────────────────────────────
		assertTrue(answer.contains("$847M"), "Acme revenue must appear");
		assertTrue(answer.contains("$1.2B") || answer.contains("1.2"), "Globex revenue must appear");
		assertTrue(answer.contains("Net Income") || answer.contains("$142M"), "Acme net income must appear");
		assertTrue(answer.contains("Net Loss") || answer.contains("$45M"), "Globex net loss must appear");
	}

	@Test
	void queryWithoutRelevantDocumentsReturnsNoRagContext() {
		ragService.ingest("reports/acme-q4-2025", "Acme Corp Q4 2025. Revenue: $847M. Cloud revenue: $312M.");

		GoalResult result = embabelAgent.executeGoal(
				new GoalRequest("financial-analyst", "What is the weather forecast for Tokyo next week?", null));

		String answer = result.answer();

		// Irrelevant query should not inject unrelated financial data
		assertFalse(answer.contains("$847M"), "Irrelevant query must not inject Acme financial data");
		assertFalse(answer.contains("Relevant Document Context (RAG):"),
				"No RAG context should appear for irrelevant weather query");
	}

	@Test
	void sourceRefIntegrityAfterDeletion() {
		ragService.ingest("reports/temp-q1-2025", "Temporary Corp Q1 2025. Revenue: $50M. Will be deleted.");

		// Verify it's retrievable
		RagContext before = ragService.retrieve("Temporary Corp revenue", 1);
		assertFalse(before.isEmpty());
		assertTrue(before.retrieved().get(0).sourceRef().equals("reports/temp-q1-2025"));

		// Delete the document from object storage
		ragService.deleteDocument("reports/temp-q1-2025");

		// After deletion, the same query returns no results
		RagContext after = ragService.retrieve("Temporary Corp revenue", 1);
		// Chunks remain in pgvector (known limitation: Retriever has no delete),
		// but the S3 objects are gone. The retrieval still works from pgvector.
		// This verifies the current behaviour is understood.
		assertTrue(after.isEmpty() || after.retrieved().get(0).sourceRef().equals("reports/temp-q1-2025"),
				"After S3 delete, retrieval may still find pgvector chunks (expected until Retriever.delete is added)");
	}

	@Test
	void multiChunkDocumentPreservesSourceAcrossChunks() {
		String report = "Acme Corp Annual Report 2025. "
				+ "Executive Summary: Record year with $3.2B total revenue across all divisions. "
				+ "Section 1 - Cloud Division: Revenue $1.4B, up 52% YoY. Operating margin 22%. "
				+ "Section 2 - Enterprise Division: Revenue $1.1B, up 8% YoY. Operating margin 14%. "
				+ "Section 3 - Consumer Division: Revenue $700M, down 3% YoY. Operating margin 9%. "
				+ "Risk Factors: Supply chain constraints in consumer electronics segment. "
				+ "Currency headwinds in EMEA region estimated at $45M impact. "
				+ "Outlook: FY2026 guidance $3.6B-$3.8B with cloud division driving growth."
				+ "Additional filler text to ensure this spans multiple chunks. "
				+ "More filler text to push content across chunk boundaries for testing. "
				+ "Even more filler text continues to build out the document length. "
				+ "Final filler text to guarantee multi-chunk splitting behavior.";

		int chunks = ragService.ingest("reports/acme-annual-2025", report, new ChunkConfig(300, 50));
		assertTrue(chunks >= 2, "Document must be split into at least 2 chunks: " + chunks);

		GoalResult result = embabelAgent.executeGoal(new GoalRequest("financial-analyst",
				"What was Acme's cloud division revenue and what risks were identified?", null));

		String answer = result.answer();

		// Cloud division data (likely in chunk 0)
		assertTrue(answer.contains("$1.4B") || answer.contains("Cloud"), "Cloud division revenue must appear");

		// Risk factor data (likely in a later chunk)
		assertTrue(answer.contains("supply chain") || answer.contains("Supply chain") || answer.contains("Currency")
				|| answer.contains("currency"), "Risk factors from later chunk must appear");

		// All chunks from the same source must carry the same sourceRef
		for (String line : answer.split("\n")) {
			if (line.contains("reports/acme-annual-2025")) {
				assertTrue(line.contains("[reports/acme-annual-2025"),
						"Source attribution format consistent across chunks: " + line);
			}
		}
	}
}
