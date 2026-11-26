package com.athul.documentintelligence.service;

import com.athul.documentintelligence.dto.response.SourceCitationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationServiceTest {

    private final CitationService citationService = new CitationService();

    @Test
    void buildsCitationsDistinguishingMissingPageFromPageZero() {
        UUID pdfDocId = UUID.randomUUID();
        UUID txtDocId = UUID.randomUUID();

        Document pdfDoc = Document.builder()
                .id(UUID.randomUUID().toString())
                .text("Employees receive 24 days of annual leave.")
                .metadata(Map.of(
                        "documentId", pdfDocId.toString(),
                        "originalFilename", "Employee_Handbook.pdf",
                        "pageNumber", 12,
                        "chunkId", "chunk-120"
                ))
                .score(0.91234)
                .build();

        Document txtDoc = Document.builder()
                .id(UUID.randomUUID().toString())
                .text("Plain text onboarding guide.")
                .metadata(Map.of(
                        "documentId", txtDocId.toString(),
                        "originalFilename", "onboarding.txt",
                        "sectionTitle", "Plain Text",
                        "chunkId", "chunk-0"
                ))
                .score(0.82)
                .build();

        List<SourceCitationResponse> citations = citationService.buildCitations(List.of(pdfDoc, txtDoc));
        assertThat(citations).hasSize(2);

        assertThat(citations.get(0).documentId()).isEqualTo(pdfDocId);
        assertThat(citations.get(0).documentName()).isEqualTo("Employee_Handbook.pdf");
        assertThat(citations.get(0).pageNumber()).isEqualTo(12);
        assertThat(citations.get(0).relevanceScore()).isEqualTo(0.9123);

        assertThat(citations.get(1).documentId()).isEqualTo(txtDocId);
        assertThat(citations.get(1).pageNumber()).isNull();
        assertThat(citations.get(1).sectionTitle()).isEqualTo("Plain Text");
    }
}
