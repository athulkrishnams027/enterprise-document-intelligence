package com.athul.documentintelligence.service;

import java.util.UUID;

/**
 * Background ingestion processor contract (implemented by extraction -> chunking -> embedding -> pgvector pipeline).
 */
public interface DocumentIngestionProcessor {

    void processDocument(UUID documentId);
}
