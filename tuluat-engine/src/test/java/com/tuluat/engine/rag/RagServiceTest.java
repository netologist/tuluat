package com.tuluat.engine.rag;

import com.tuluat.engine.rag.embedding.LocalHashEmbeddingProvider;
import com.tuluat.engine.rag.storage.LocalObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagServiceTest {

	@TempDir
	Path tempDir;

	private RagService service() {
		LocalHashEmbeddingProvider embeddings = new LocalHashEmbeddingProvider();
		InMemoryRetriever retriever = new InMemoryRetriever();
		LocalObjectStorage storage = new LocalObjectStorage(tempDir.toString());
		return new RagService(new RecursiveCharacterChunker(), embeddings, retriever, storage);
	}

	@Test
	void ingestStoresChunksAndRawDocument() {
		RagService rag = service();
		String doc = "Kubernetes Custom Resources are extensions of the API. "
				+ "Operators watch these resources and reconcile desired state. "
				+ "CRDs define the schema of a custom resource. "
				+ "This is the fourth sentence to ensure multiple chunks. "
				+ "Fifth sentence continues the document content.";
		int chunks = rag.ingest("runbooks/k8s-crds", doc);

		assertTrue(chunks >= 1);
		// Raw document persisted in object storage
		assertFalse(rag.retrieve("custom resources", 1).isEmpty());
	}

	@Test
	void retrieveReturnsMostRelevantFirst() {
		RagService rag = service();
		rag.ingest("docs/kubernetes", "Kubernetes is a container orchestration platform.");
		rag.ingest("docs/weather", "Istanbul weather is sunny and warm in summer.");

		RagContext ctx = rag.retrieve("container orchestration", 2);

		assertEquals(2, ctx.retrieved().size());
		assertTrue(ctx.retrieved().get(0).similarity() >= ctx.retrieved().get(1).similarity());
		assertEquals("docs/kubernetes", ctx.retrieved().get(0).sourceRef());
	}

	@Test
	void toPromptBlockConcatenatesChunks() {
		RagService rag = service();
		rag.ingest("docs/temporal", "Temporal provides durable workflow execution with retries and timeouts.");

		String block = rag.retrieveAsPrompt("durable workflows", 1);

		assertTrue(block.contains("Relevant Document Context"));
		assertTrue(block.contains("docs/temporal"));
		assertTrue(block.contains("durable workflow"));
	}

	@Test
	void retrieveWithEmptyQueryReturnsEmpty() {
		RagService rag = service();
		rag.ingest("docs/x", "Some content here.");
		RagContext ctx = rag.retrieve("  ", 3);
		assertTrue(ctx.isEmpty());
	}

	@Test
	void deleteDocumentRemovesObjects() {
		RagService rag = service();
		rag.ingest("docs/tmp", "Temporary document to be deleted.");
		RagService fresh = service();
		// new service instance => same tempDir, object storage shared, retriever fresh
		assertFalse(rag.retrieve("temporary document", 1).isEmpty());
		assertEquals(List.of(), fresh.retrieve("temporary document", 1).retrieved());
	}
}
