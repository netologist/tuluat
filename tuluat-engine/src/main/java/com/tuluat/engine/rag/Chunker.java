package com.tuluat.engine.rag;

import java.util.List;

/**
 * Splits a source document into retrieval-ready chunks.
 */
public interface Chunker {

	List<TextChunk> chunk(String content, String sourceRef, ChunkConfig config);
}
