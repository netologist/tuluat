package com.tuluat.engine.rag;

import com.tuluat.engine.rag.embedding.EmbeddingProvider;
import com.tuluat.engine.rag.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * RAG orchestrator (ADR 008): ingest (chunk → embed → store) and retrieve
 * (embed query → top-K similarity → {@link RagContext}).
 */
@Service
public class RagService {

	private static final Logger log = LoggerFactory.getLogger(RagService.class);

	private final Chunker chunker;
	private final EmbeddingProvider embeddingProvider;
	private final Retriever retriever;
	private final ObjectStorage objectStorage;

	public RagService(Chunker chunker, EmbeddingProvider embeddingProvider, Retriever retriever,
			ObjectStorage objectStorage) {
		this.chunker = chunker;
		this.embeddingProvider = embeddingProvider;
		this.retriever = retriever;
		this.objectStorage = objectStorage;
	}

	/**
	 * Ingest a source document: store the raw document in object storage and
	 * persist embedded chunks for retrieval.
	 *
	 * @param sourceRef
	 *            document identifier (e.g. "runbooks/incident-42")
	 * @param content
	 *            document text
	 * @return number of chunks stored
	 */
	public int ingest(String sourceRef, String content) {
		return ingest(sourceRef, content, ChunkConfig.defaults());
	}

	/**
	 * Ingest with a custom chunk configuration.
	 */
	public int ingest(String sourceRef, String content, ChunkConfig config) {
		if (content == null || content.isBlank()) {
			log.warn("RAG ingest skipped: empty content for {}", sourceRef);
			return 0;
		}
		String docId = UUID.randomUUID().toString();
		String objectKey = "documents/" + sourceRef + "/" + docId + ".txt";
		objectStorage.put(objectKey, content.getBytes(StandardCharsets.UTF_8), "text/plain");

		List<TextChunk> chunks = chunker.chunk(content, sourceRef, config);
		for (TextChunk chunk : chunks) {
			float[] embedding = embeddingProvider.embed(chunk.content());
			retriever.storeChunk(chunk, embedding, docId);
		}
		log.info("RAG ingested [{}]: {} chunks, object {}", sourceRef, chunks.size(), objectKey);
		return chunks.size();
	}

	/**
	 * Retrieve the top-K most relevant chunks for a query.
	 */
	public RagContext retrieve(String query, int topK) {
		if (query == null || query.isBlank()) {
			return new RagContext(query, List.of());
		}
		float[] queryEmbedding = embeddingProvider.embed(query);
		List<RetrievedChunk> chunks = retriever.retrieve(queryEmbedding, Math.max(1, topK));
		log.info("RAG retrieve [{}]: {} chunk(s)", query, chunks.size());
		return new RagContext(query, chunks);
	}

	/**
	 * Delete all chunks and stored objects for a source document.
	 */
	public void deleteDocument(String sourceRef) {
		List<String> keys = objectStorage.list("documents/" + sourceRef);
		keys.forEach(objectStorage::delete);
		log.info("RAG deleted document [{}]: {} object(s)", sourceRef, keys.size());
	}

	/**
	 * Concatenated retrieval context ready to merge into a system prompt.
	 */
	public String retrieveAsPrompt(String query, int topK) {
		return retrieve(query, topK).toPromptBlock();
	}
}
