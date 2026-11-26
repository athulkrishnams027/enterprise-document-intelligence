package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.exception.AiProviderException;
import com.athul.documentintelligence.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private final AiProperties properties = new AiProperties(
            "ollama", "ollama", 768,
            new AiProperties.ChunkingProperties(800, 150),
            new AiProperties.RagProperties(6, 0.55, 12000, 0.2, 6)
    );

    @Test
    void embedsValidTextWithMatchingDimensions() {
        EmbeddingService service = new EmbeddingService(embeddingModel, properties);
        float[] vector = new float[768];
        vector[0] = 0.5f;
        when(embeddingModel.embed("annual leave")).thenReturn(vector);

        float[] result = service.embedText("annual leave");
        assertThat(result).hasSize(768);
        assertThat(result[0]).isEqualTo(0.5f);
    }

    @Test
    void rejectsEmptyInputAndDimensionMismatchAndProviderFailure() {
        EmbeddingService service = new EmbeddingService(embeddingModel, properties);

        assertThatThrownBy(() -> service.embedText("   "))
                .isInstanceOf(BadRequestException.class);

        when(embeddingModel.embed("bad dim")).thenReturn(new float[1536]);
        assertThatThrownBy(() -> service.embedText("bad dim"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("dimension mismatch");

        when(embeddingModel.embed("provider down")).thenThrow(new RuntimeException("Connection refused"));
        assertThatThrownBy(() -> service.embedText("provider down"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Embedding generation failed");
    }
}
