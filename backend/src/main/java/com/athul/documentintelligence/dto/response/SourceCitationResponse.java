package com.athul.documentintelligence.dto.response;

import java.util.UUID;

public record SourceCitationResponse(
        UUID documentId,
        String documentName,
        Integer pageNumber,
        String sectionTitle,
        String chunkId,
        Double relevanceScore,
        String excerpt
) {
}
