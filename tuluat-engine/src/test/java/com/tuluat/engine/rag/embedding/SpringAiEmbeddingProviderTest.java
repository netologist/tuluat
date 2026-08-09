package com.tuluat.engine.rag.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiEmbeddingProviderTest {

    @Test
    void embedDelegatesToSpringAiEmbeddingModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        float[] expectedVector = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        Embedding embedding = new Embedding(expectedVector, 0);
        EmbeddingResponse response = new EmbeddingResponse(List.of(embedding));

        when(model.call(any(EmbeddingRequest.class))).thenReturn(response);

        SpringAiEmbeddingProvider provider = new SpringAiEmbeddingProvider(model, 1536);

        assertEquals(1536, provider.dimension());
        float[] result = provider.embed("test document");
        assertArrayEquals(expectedVector, result);
    }
}
