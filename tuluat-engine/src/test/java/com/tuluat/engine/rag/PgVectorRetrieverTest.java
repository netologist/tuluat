package com.tuluat.engine.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorRetrieverTest {

	private JdbcTemplate jdbcTemplate;
	private PgVectorRetriever retriever;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		retriever = new PgVectorRetriever(jdbcTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void retrieveQueriesJdbcTemplateWithVectorLiteral() {
		RetrievedChunk chunk = new RetrievedChunk(0, "Kubernetes CRD content", "docs/k8s", 0.95);
		when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), any(), any()))
				.thenReturn(List.of(chunk));

		float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
		List<RetrievedChunk> result = retriever.retrieve(embedding, 5);

		assertEquals(1, result.size());
		assertEquals("docs/k8s", result.get(0).sourceRef());
		assertEquals(0.95, result.get(0).similarity());

		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq("[0.1,0.2,0.3]"), eq("[0.1,0.2,0.3]"),
				eq(5));
		assertTrue(sqlCaptor.getValue().contains("<=>"));
	}

	@Test
	void storeChunkInsertsIntoRagChunks() {
		TextChunk chunk = new TextChunk(1, "Chunk text content", "runbook/v1");
		float[] embedding = new float[]{0.5f, 0.5f};

		retriever.storeChunk(chunk, embedding, "doc-123");

		verify(jdbcTemplate).update(any(String.class), eq("doc-123"), eq("runbook/v1"), eq(1), eq("Chunk text content"),
				eq("[0.5,0.5]"));
	}
}
