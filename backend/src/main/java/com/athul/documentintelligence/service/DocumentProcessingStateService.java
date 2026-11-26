package com.athul.documentintelligence.service;

import com.athul.documentintelligence.entity.DocumentMetadata;
import com.athul.documentintelligence.entity.DocumentProcessingLog;
import com.athul.documentintelligence.enums.DocumentStatus;
import com.athul.documentintelligence.repository.DocumentProcessingLogRepository;
import com.athul.documentintelligence.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages atomic transactional state transitions and processing audit logs for document ingestion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingStateService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingLogRepository processingLogRepository;

    @Transactional
    public DocumentMetadata claimForProcessing(UUID documentId) {
        DocumentMetadata doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("Document not found for ingestion documentId={}", documentId);
            return null;
        }
        if (doc.getStatus() == DocumentStatus.PROCESSING || doc.getStatus() == DocumentStatus.DELETING) {
            log.info("Skipping duplicate/concurrent ingestion documentId={} status={}", documentId, doc.getStatus());
            return null;
        }
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setProcessingError(null);
        return documentRepository.save(doc);
    }

    @Transactional
    public void markCompleted(UUID documentId, Integer totalPages, int totalChunks, String summary) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.COMPLETED);
            doc.setProcessingError(null);
            doc.setTotalPages(totalPages);
            doc.setTotalChunks(totalChunks);
            doc.setExtractionSummary(summary != null && summary.length() > 1000 ? summary.substring(0, 1000) : summary);
            doc.setLastProcessedAt(Instant.now());
            documentRepository.save(doc);
        });
    }

    @Transactional
    public void markFailed(UUID documentId, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setProcessingError(errorMessage);
            doc.setLastProcessedAt(Instant.now());
            documentRepository.save(doc);
        });
    }

    @Transactional
    public void saveLog(UUID documentId, String stage, String status, String message, long durationMs) {
        DocumentProcessingLog entry = new DocumentProcessingLog();
        entry.setDocumentId(documentId);
        entry.setStage(stage);
        entry.setStatus(status);
        entry.setMessage(message);
        entry.setDurationMs(durationMs);
        processingLogRepository.save(entry);
    }
}
