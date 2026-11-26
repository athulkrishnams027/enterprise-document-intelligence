package com.athul.documentintelligence.model;

import java.util.UUID;

/**
 * A self-contained, ordered chunk of extracted text carrying all metadata needed
 * for vector indexing, permission filtering, and source citations.
 */
public record DocumentChunk(
        UUID vectorRecordId,
        String chunkId,
        UUID documentId,
        UUID ownerId,
        String originalFilename,
        Integer pageNumber,
        String sectionTitle,
        int chunkIndex,
        int estimatedTokens,
        String contentHash,
        String content
) {
}
