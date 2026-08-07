package com.tuluat.engine.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecursiveCharacterChunkerTest {

    private final RecursiveCharacterChunker chunker = new RecursiveCharacterChunker();

    @Test
    void leavesShortTextAsSingleChunk() {
        List<TextChunk> chunks = chunker.chunk("Short document.", "doc-1", new ChunkConfig(1200, 150));
        assertEquals(1, chunks.size());
        assertEquals("Short document.", chunks.get(0).content());
        assertEquals(0, chunks.get(0).chunkIndex());
    }

    @Test
    void splitsLongTextOnBoundaries() {
        String text = "Paragraph one.\n\nParagraph two.\n\nParagraph three.\n\nParagraph four.\n\nParagraph five.";
        List<TextChunk> chunks = chunker.chunk(text, "doc-2", new ChunkConfig(40, 0));
        assertTrue(chunks.size() >= 3);
        // Each chunk must be non-blank and belong to the source
        chunks.forEach(c -> {
            assertFalse(c.content().isBlank());
            assertEquals("doc-2", c.sourceRef());
        });
    }

    @Test
    void hardSplitsWhenNoBoundariesExist() {
        String text = "a".repeat(300);
        List<TextChunk> chunks = chunker.chunk(text, "doc-3", new ChunkConfig(100, 0));
        assertEquals(3, chunks.size());
        assertTrue(chunks.stream().allMatch(c -> c.content().length() <= 100));
    }

    @Test
    void emptyContentYieldsNoChunks() {
        assertTrue(chunker.chunk("  ", "doc-4", ChunkConfig.defaults()).isEmpty());
        assertTrue(chunker.chunk(null, "doc-4", ChunkConfig.defaults()).isEmpty());
    }

    @Test
    void overlapPreservesContextBetweenChunks() {
        String text = "word ".repeat(200);
        List<TextChunk> chunks = chunker.chunk(text, "doc-5", new ChunkConfig(200, 50));
        assertTrue(chunks.size() >= 2);
    }
}
