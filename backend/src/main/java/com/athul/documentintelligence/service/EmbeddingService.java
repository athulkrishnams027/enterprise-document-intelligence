package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.exception.AiProviderException;
import com.athul.documentintelligence.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Wraps Spring AI's provider-agnostic EmbeddingModel with dimension validation,
 * timing observability, and clean error translation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final AiProperties aiProperties;

    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("EMPTY_EMBEDDING_INPUT", "Cannot generate embedding for empty text");
        }
        long start = System.currentTimeMillis();
        try {
            float[] vector = embeddingModel.embed(text);
            validateDimensions(vector);
            log.debug("Generated embedding dimensions={} durationMs={}", vector.length, System.currentTimeMillis() - start);
            return vector;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Embedding provider failure provider='{}': {}", aiProperties.embeddingProvider(), ex.getMessage());
            throw new AiProviderException(
                    "EMBEDDING_PROVIDER_ERROR",
                    "Embedding generation failed with provider '" + aiProperties.embeddingProvider() + "'",
                    ex
            );
        }
    }

    public void validateDimensions(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new AiProviderException("INVALID_EMBEDDING", "Embedding provider returned an empty vector");
        }
        int expected = aiProperties.expectedEmbeddingDimensions();
        if (expected > 0 && vector.length != expected) {
            throw new AiProviderException(
                    "EMBEDDING_DIMENSION_MISMATCH",
                    "Embedding dimension mismatch: expected " + expected + " but got " + vector.length
                            + ". Verify vector store dimensions and embedding model compatibility."
            );
        }
    }

    public int getExpectedDimensions() {
        return aiProperties.expectedEmbeddingDimensions();
    }

    public String getActiveProvider() {
        return aiProperties.embeddingProvider();
    }
}
