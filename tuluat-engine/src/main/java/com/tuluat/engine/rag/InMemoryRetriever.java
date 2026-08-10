package com.tuluat.engine.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory retriever: brute-force cosine similarity over embedded chunks (ADR
 * 008). Used in unit tests and as the default when pgvector is not configured.
 * Active when {@code tuluat.rag.retriever=memory} (default). Chunks are stored
 * with their pre-computed embeddings.
 */
@Component
@ConditionalOnProperty(name = "tuluat.rag.retriever", havingValue = "memory", matchIfMissing = true)
@Slf4j
public class InMemoryRetriever implements Retriever {
private record Entry(TextChunk chunk, float[] embedding) {
	}

	private final List<Entry> entries = new CopyOnWriteArrayList<>();

	@Override
	public List<RetrievedChunk> retrieve(float[] queryEmbedding, int topK) {
		List<RetrievedChunk> results = new ArrayList<>();
		for (Entry entry : entries) {
			double sim = cosine(queryEmbedding, entry.embedding());
			results.add(new RetrievedChunk(entry.chunk().chunkIndex(), entry.chunk().content(),
					entry.chunk().sourceRef(), sim));
		}
		results.sort(Comparator.comparingDouble(RetrievedChunk::similarity).reversed());
		return results.stream().limit(topK).toList();
	}

	@Override
	public void storeChunk(TextChunk chunk, float[] embedding, String docId) {
		entries.add(new Entry(chunk, embedding));
		log.debug("InMemoryRetriever stored chunk {} of {}", chunk.chunkIndex(), chunk.sourceRef());
	}

	private static double cosine(float[] a, float[] b) {
		if (a.length == 0 || a.length != b.length) {
			return 0.0;
		}
		double dot = 0.0, na = 0.0, nb = 0.0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		if (na == 0.0 || nb == 0.0) {
			return 0.0;
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}
}
