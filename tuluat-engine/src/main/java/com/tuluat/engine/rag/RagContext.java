package com.tuluat.engine.rag;

import java.util.List;

/**
 * Retrieval context for a query (ADR 008). Agents merge the concatenated chunk
 * texts into their system prompt as grounding context.
 *
 * @param query        the original query
 * @param retrieved    top-K chunks ordered by descending similarity
 */
public record RagContext(
    String query,
    List<RetrievedChunk> retrieved
) {

    public boolean isEmpty() {
        return retrieved == null || retrieved.isEmpty();
    }

    public String toPromptBlock() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\nRelevant Document Context (RAG):\n");
        for (RetrievedChunk chunk : retrieved) {
            sb.append(String.format("- [%s #%d (sim %.2f)]: %s%n",
                chunk.sourceRef(), chunk.chunkIndex(), chunk.similarity(), chunk.content()));
        }
        return sb.toString();
    }
}
