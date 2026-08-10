package com.tuluat.engine.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * pgvector-backed retriever (ADR 008): cosine similarity via {@code <=>} on the
 * {@code rag_chunks.embedding} column. Active when
 * {@code tuluat.rag.retriever=pgvector}. Fallback: {@link InMemoryRetriever}.
 */
@Component
@ConditionalOnProperty(name = "tuluat.rag.retriever", havingValue = "pgvector")
public class PgVectorRetriever implements Retriever {

	private static final Logger log = LoggerFactory.getLogger(PgVectorRetriever.class);

	private final JdbcTemplate jdbcTemplate;

	public PgVectorRetriever(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		log.info("PgVectorRetriever active");
	}

	@Override
	public List<RetrievedChunk> retrieve(float[] queryEmbedding, int topK) {
		String vectorLiteral = toVectorLiteral(queryEmbedding);
		return jdbcTemplate
				.query("""
						SELECT chunk_index, content, source_ref, 1 - (embedding <=> ?::vector) AS similarity
						FROM rag_chunks
						ORDER BY embedding <=> ?::vector
						LIMIT ?
						""",
						(rs, rowNum) -> new RetrievedChunk(rs.getInt("chunk_index"), rs.getString("content"),
								rs.getString("source_ref"), rs.getDouble("similarity")),
						vectorLiteral, vectorLiteral, topK);
	}

	@Override
	public void storeChunk(TextChunk chunk, float[] embedding, String docId) {
		jdbcTemplate.update("""
				INSERT INTO rag_chunks (doc_id, source_ref, chunk_index, content, embedding)
				VALUES (?, ?, ?, ?, ?::vector)
				""", docId, chunk.sourceRef(), chunk.chunkIndex(), chunk.content(), toVectorLiteral(embedding));
	}

	private static String toVectorLiteral(float[] v) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < v.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(Float.toString(v[i]));
		}
		return sb.append(']').toString();
	}
}
