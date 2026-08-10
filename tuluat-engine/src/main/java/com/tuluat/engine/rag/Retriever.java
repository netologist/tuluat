package com.tuluat.engine.rag;

import java.util.List;

/**
 * Retrieval SPI (ADR 008): returns the top-K most relevant chunks for a query
 * embedding. Implementations: {@code PgVectorRetriever} (production) and
 * {@code InMemoryRetriever} (unit tests / fallback).
 */
public interface Retriever {

	/**
	 * Retrieve chunks most similar to the given query embedding.
	 *
	 * @param queryEmbedding
	 *            query vector (must match provider dimension)
	 * @param topK
	 *            max number of chunks to return
	 * @return chunks ordered by descending similarity
	 */
	List<RetrievedChunk> retrieve(float[] queryEmbedding, int topK);

	/**
	 * Persist an embedded chunk for future retrieval.
	 */
	void storeChunk(TextChunk chunk, float[] embedding, String docId);
}
