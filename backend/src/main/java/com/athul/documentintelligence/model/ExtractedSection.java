package com.athul.documentintelligence.model;

/**
 * Represents a logically extracted unit (e.g., a PDF page or a DOCX/TXT section)
 * preserving provenance metadata for downstream chunking and citations.
 */
public record ExtractedSection(
        Integer pageNumber,
        String sectionTitle,
        int sectionOrder,
        String text
) {
}
