package com.tuluat.engine.rag;

/**
 * Chunking configuration.
 *
 * @param chunkSize target chunk size in characters
 * @param overlap   characters shared between consecutive chunks
 */
public record ChunkConfig(int chunkSize, int overlap) {

    public static ChunkConfig defaults() {
        return new ChunkConfig(1200, 150);
    }

    public ChunkConfig {
        if (chunkSize <= 0) {
            chunkSize = 1200;
        }
        if (overlap < 0 || overlap >= chunkSize) {
            overlap = Math.max(0, chunkSize / 8);
        }
    }
}
