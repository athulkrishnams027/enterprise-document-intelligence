package com.athul.documentintelligence.service;

import com.athul.documentintelligence.dto.response.SourceCitationResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds verified source citation DTOs strictly from vector chunks supplied to the LLM.
 * Distinguishes absent page numbers (DOCX/TXT -> null) from page zero.
 */
@Service
public class CitationService {

    private static final int MAX_EXCERPT_LENGTH = 360;

    public List<SourceCitationResponse> buildCitations(List<Document> suppliedDocuments) {
        if (suppliedDocuments == null || suppliedDocuments.isEmpty()) {
            return List.of();
        }
        List<SourceCitationResponse> citations = new ArrayList<>(suppliedDocuments.size());
        for (Document doc : suppliedDocuments) {
            Map<String, Object> meta = doc.getMetadata();
            UUID documentId = parseUuid(meta.get(VectorSearchService.META_DOCUMENT_ID));
            String documentName = stringOrDefault(meta.get(VectorSearchService.META_FILENAME), "Unknown Document");
            Integer pageNumber = parsePositivePageNumber(meta.get(VectorSearchService.META_PAGE_NUMBER));
            String sectionTitle = nullableString(meta.get(VectorSearchService.META_SECTION_TITLE));
            String chunkId = stringOrDefault(meta.get(VectorSearchService.META_CHUNK_ID), doc.getId());
            Double score = roundScore(doc.getScore());
            String excerpt = buildExcerpt(doc.getText());

            citations.add(new SourceCitationResponse(
                    documentId,
                    documentName,
                    pageNumber,
                    sectionTitle,
                    chunkId,
                    score,
                    excerpt
            ));
        }
        return citations;
    }

    private UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Integer parsePositivePageNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number num) {
            int val = num.intValue();
            return val > 0 ? val : null;
        }
        try {
            int val = Integer.parseInt(raw.toString().trim());
            return val > 0 ? val : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringOrDefault(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private String nullableString(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Double roundScore(Double rawScore) {
        if (rawScore == null || rawScore.isNaN() || rawScore.isInfinite()) {
            return null;
        }
        return Math.round(rawScore * 10000.0) / 10000.0;
    }

    private String buildExcerpt(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= MAX_EXCERPT_LENGTH) {
            return compact;
        }
        return compact.substring(0, MAX_EXCERPT_LENGTH - 3).trim() + "...";
    }
}
