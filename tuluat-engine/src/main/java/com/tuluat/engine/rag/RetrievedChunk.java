package com.tuluat.engine.rag;

/**
 * A chunk retrieved for a query.
 *
 * @param chunkIndex   position in the source document
 * @param content      chunk text
 * @param sourceRef    originating document reference
 * @param similarity   cosine similarity score (0..1, higher = more relevant)
 */
public record RetrievedChunk(
    int chunkIndex,
    String content,
    String sourceRef,
    double similarity
) {
}
