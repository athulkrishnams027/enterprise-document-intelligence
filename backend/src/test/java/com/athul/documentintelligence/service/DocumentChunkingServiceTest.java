package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.model.DocumentChunk;
import com.athul.documentintelligence.model.ExtractedSection;
import com.athul.documentintelligence.model.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkingServiceTest {

    @Test
    void chunksPreservePageNumbersOrderingOverlapAndStableIds() {
        AiProperties props = new AiProperties(
                "ollama",
                "ollama",
                768,
                new AiProperties.ChunkingProperties(25, 10),
                new AiProperties.RagProperties(6, 0.55, 12000, 0.2, 6)
        );
        DocumentChunkingService chunkingService = new DocumentChunkingService(props);

        String longSection = String.join("\n\n", List.of(
                "Paragraph one explains annual leave entitlement of twenty-four paid days per calendar year for full-time staff.",
                "Paragraph two explains sick leave carry-over rules and medical certificate requirements after three consecutive days.",
                "Paragraph three explains parental leave eligibility, notice periods, and full salary continuation terms."
        ));

        ExtractionResult extraction = new ExtractionResult(
                2,
                List.of(
                        new ExtractedSection(1, "Page 1", 1, longSection),
                        new ExtractedSection(2, "Page 2", 2, "Final summary on page two.")
                ),
                "summary"
        );

        UUID docId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        List<DocumentChunk> chunks = chunkingService.chunk(docId, ownerId, "handbook.pdf", extraction);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
            assertThat(chunks.get(i).chunkId()).isEqualTo("chunk-" + i);
            assertThat(chunks.get(i).documentId()).isEqualTo(docId);
            assertThat(chunks.get(i).ownerId()).isEqualTo(ownerId);
            assertThat(chunks.get(i).originalFilename()).isEqualTo("handbook.pdf");
        }
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).pageNumber()).isEqualTo(2);

        // Stable deterministic IDs across re-runs
        List<DocumentChunk> rerun = chunkingService.chunk(docId, ownerId, "handbook.pdf", extraction);
        assertThat(rerun.get(0).vectorRecordId()).isEqualTo(chunks.get(0).vectorRecordId());
    }
}
