package com.athul.documentintelligence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Dispatches document ingestion asynchronously only after the metadata transaction commits,
 * preventing race conditions and keeping HTTP request threads non-blocking.
 */
@Slf4j
@Component
public class DocumentIngestionTrigger {

    private final ObjectProvider<DocumentIngestionProcessor> processorProvider;

    public DocumentIngestionTrigger(ObjectProvider<DocumentIngestionProcessor> processorProvider) {
        this.processorProvider = processorProvider;
    }

    @Async("documentProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentReadyForProcessing(DocumentReadyEvent event) {
        DocumentIngestionProcessor processor = processorProvider.getIfAvailable();
        if (processor == null) {
            log.debug("No DocumentIngestionProcessor registered yet; skipping async ingestion for documentId={}",
                    event.documentId());
            return;
        }
        processor.processDocument(event.documentId());
    }

    public record DocumentReadyEvent(UUID documentId) {
    }
}
