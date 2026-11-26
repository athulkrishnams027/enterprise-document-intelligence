package com.athul.documentintelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration properties for AI providers, chunking, and RAG retrieval.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String chatProvider,
        String embeddingProvider,
        int expectedEmbeddingDimensions,
        ChunkingProperties chunking,
        RagProperties rag
) {
    public record ChunkingProperties(
            int targetTokens,
            int overlapTokens
    ) {
    }

    public record RagProperties(
            int topK,
            double similarityThreshold,
            int maxContextChars,
            double temperature,
            int maxHistoryTurns
    ) {
    }
}
