package com.tuluat.engine.rag;

/**
 * A chunk of a source document produced by a {@link Chunker}.
 *
 * @param chunkIndex
 *            zero-based position within the source document
 * @param content
 *            chunk text
 * @param sourceRef
 *            originating document reference
 */
public record TextChunk(int chunkIndex, String content, String sourceRef) {
}
