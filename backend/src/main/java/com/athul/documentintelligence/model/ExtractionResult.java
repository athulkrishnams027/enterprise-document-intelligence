package com.athul.documentintelligence.model;

import java.util.List;

/**
 * Result of extracting textual content and structural metadata from a stored document.
 */
public record ExtractionResult(
        Integer totalPages,
        List<ExtractedSection> sections,
        String summary
) {
}
