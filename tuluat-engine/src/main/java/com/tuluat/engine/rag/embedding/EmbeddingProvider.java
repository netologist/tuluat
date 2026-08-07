package com.tuluat.engine.rag.embedding;

/**
 * Embedding provider SPI (ADR 008). Implementations must produce fixed-length
 * vectors matching the pgvector column dimension (1536).
 */
public interface EmbeddingProvider {

    /**
     * @return vector dimension (must match DB column, e.g. 1536)
     */
    int dimension();

    /**
     * Embed a text into a normalized vector.
     */
    float[] embed(String text);
}
