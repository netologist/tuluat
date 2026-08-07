package com.tuluat.engine.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production embedding provider wrapping Spring AI {@link EmbeddingModel}
 * (OpenAI / Ollama embedding starters). Active only when
 * {@code tuluat.rag.embedding=spring-ai}; otherwise {@link LocalHashEmbeddingProvider}
 * is used.
 */
@Component
@ConditionalOnProperty(name = "tuluat.rag.embedding", havingValue = "spring-ai")
public class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingProvider.class);

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("SpringAiEmbeddingProvider active (model: {})", embeddingModel.getClass().getSimpleName());
    }

    @Override
    public int dimension() {
        return LocalHashEmbeddingProvider.DIMENSION;
    }

    @Override
    public float[] embed(String text) {
        var response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }
}
