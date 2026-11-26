package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.entity.DocumentMetadata;
import com.athul.documentintelligence.enums.SupportedDocumentType;
import com.athul.documentintelligence.model.DocumentChunk;
import com.athul.documentintelligence.model.ExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the background ingestion pipeline:
 * claim document -> extract text -> chunk -> remove stale vectors -> embed & index in pgvector -> mark COMPLETED.
 * Never marks a document COMPLETED unless vector storage succeeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService implements DocumentIngestionProcessor {

    private final DocumentProcessingStateService stateService;
    private final FileStorageService fileStorageService;
    private final DocumentExtractionService extractionService;
    private final DocumentChunkingService chunkingService;
    private final VectorSearchService vectorSearchService;
    private final AiAuditService aiAuditService;
    private final AiProperties aiProperties;

    @Override
    public void processDocument(UUID documentId) {
        DocumentMetadata doc = stateService.claimForProcessing(documentId);
        if (doc == null) {
            return;
        }

        long pipelineStart = System.currentTimeMillis();
        try {
            byte[] bytes = fileStorageService.readBytes(doc.getStoredFilename());
            SupportedDocumentType docType = resolveType(doc.getOriginalFilename());

            long extractStart = System.currentTimeMillis();
            ExtractionResult extraction = extractionService.extract(docType, bytes, doc.getOriginalFilename());
            long extractMs = System.currentTimeMillis() - extractStart;
            stateService.saveLog(documentId, "EXTRACTION", "COMPLETED", extraction.summary(), extractMs);

            long chunkStart = System.currentTimeMillis();
            List<DocumentChunk> rawChunks = chunkingService.chunk(
                    doc.getId(), doc.getOwnerId(), doc.getOriginalFilename(), extraction
            );
            List<DocumentChunk> deduplicatedChunks = deduplicateChunks(rawChunks);
            long chunkMs = System.currentTimeMillis() - chunkStart;
            stateService.saveLog(documentId, "CHUNKING", "COMPLETED",
                    "Created " + deduplicatedChunks.size() + " chunks (raw=" + rawChunks.size() + ")", chunkMs);

            // Clean any prior embeddings before re-indexing so reprocessing leaves no orphaned chunks
            vectorSearchService.deleteByDocumentId(doc.getId());

            long embedStart = System.currentTimeMillis();
            vectorSearchService.indexChunks(deduplicatedChunks);
            long embedMs = System.currentTimeMillis() - embedStart;
            stateService.saveLog(documentId, "EMBEDDING_INDEXING", "COMPLETED",
                    "Embedded and stored " + deduplicatedChunks.size() + " vectors in pgvector", embedMs);

            int totalTokens = deduplicatedChunks.stream().mapToInt(DocumentChunk::estimatedTokens).sum();
            aiAuditService.record(
                    doc.getOwnerId(),
                    null,
                    doc.getId(),
                    "DOCUMENT_EMBEDDING",
                    aiProperties.embeddingProvider(),
                    aiProperties.embeddingProvider(),
                    totalTokens,
                    0,
                    totalTokens,
                    embedMs,
                    "SUCCESS",
                    null
            );

            stateService.markCompleted(doc.getId(), extraction.totalPages(), deduplicatedChunks.size(), extraction.summary());
            log.info("Completed ingestion documentId={} chunks={} totalMs={}",
                    documentId, deduplicatedChunks.size(), System.currentTimeMillis() - pipelineStart);
        } catch (Exception ex) {
            log.error("Ingestion failed for documentId={}: {}", documentId, ex.getMessage(), ex);
            cleanupPartialVectorsQuietly(documentId);
            String safeMessage = sanitizeErrorMessage(ex.getMessage());
            stateService.saveLog(documentId, "INGESTION", "FAILED", safeMessage, System.currentTimeMillis() - pipelineStart);
            aiAuditService.record(
                    doc.getOwnerId(),
                    null,
                    doc.getId(),
                    "DOCUMENT_EMBEDDING",
                    aiProperties.embeddingProvider(),
                    aiProperties.embeddingProvider(),
                    null,
                    null,
                    null,
                    System.currentTimeMillis() - pipelineStart,
                    "FAILED",
                    safeMessage
            );
            stateService.markFailed(documentId, safeMessage);
        }
    }

    private List<DocumentChunk> deduplicateChunks(List<DocumentChunk> chunks) {
        Set<String> seenHashes = new HashSet<>();
        List<DocumentChunk> unique = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            if (seenHashes.add(chunk.contentHash())) {
                unique.add(chunk);
            }
        }
        return unique;
    }

    private SupportedDocumentType resolveType(String filename) {
        int idx = filename.lastIndexOf('.');
        String ext = idx >= 0 ? filename.substring(idx + 1) : "";
        return SupportedDocumentType.fromExtension(ext)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported extension for stored file: " + filename));
    }

    private void cleanupPartialVectorsQuietly(UUID documentId) {
        try {
            vectorSearchService.deleteByDocumentId(documentId);
        } catch (Exception cleanupEx) {
            log.warn("Failed partial vector cleanup for documentId={}: {}", documentId, cleanupEx.getMessage());
        }
    }

    private String sanitizeErrorMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Document processing failed due to an unexpected error";
        }
        return raw.length() > 500 ? raw.substring(0, 500) : raw;
    }
}
