package com.athul.documentintelligence.service;

import com.athul.documentintelligence.dto.response.BatchUploadResponse;
import com.athul.documentintelligence.dto.response.DocumentDetailResponse;
import com.athul.documentintelligence.dto.response.DocumentResponse;
import com.athul.documentintelligence.dto.response.DocumentStatusResponse;
import com.athul.documentintelligence.dto.response.PagedResponse;
import com.athul.documentintelligence.entity.DocumentMetadata;
import com.athul.documentintelligence.entity.DocumentProcessingLog;
import com.athul.documentintelligence.enums.DocumentStatus;
import com.athul.documentintelligence.exception.BadRequestException;
import com.athul.documentintelligence.exception.DomainException;
import com.athul.documentintelligence.exception.DuplicateResourceException;
import com.athul.documentintelligence.exception.ResourceNotFoundException;
import com.athul.documentintelligence.mapper.DocumentMapper;
import com.athul.documentintelligence.repository.DocumentProcessingLogRepository;
import com.athul.documentintelligence.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingLogRepository processingLogRepository;
    private final DocumentValidationService validationService;
    private final FileStorageService fileStorageService;
    private final DocumentMapper documentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<VectorLifecyclePort> vectorLifecycleProvider;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentProcessingLogRepository processingLogRepository,
            DocumentValidationService validationService,
            FileStorageService fileStorageService,
            DocumentMapper documentMapper,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<VectorLifecyclePort> vectorLifecycleProvider
    ) {
        this.documentRepository = documentRepository;
        this.processingLogRepository = processingLogRepository;
        this.validationService = validationService;
        this.fileStorageService = fileStorageService;
        this.documentMapper = documentMapper;
        this.eventPublisher = eventPublisher;
        this.vectorLifecycleProvider = vectorLifecycleProvider;
    }

    @Transactional
    public DocumentResponse uploadDocument(UUID ownerId, MultipartFile file) {
        DocumentValidationService.ValidatedDocument validated = validationService.validate(file);

        if (documentRepository.existsByOwnerIdAndFileChecksum(ownerId, validated.checksum())) {
            throw new DuplicateResourceException(
                    "Duplicate document detected: '" + validated.originalFilename() + "' has already been uploaded"
            );
        }

        String storedKey = fileStorageService.store(
                ownerId, validated.documentType().getExtension(), validated.content()
        );

        try {
            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setOwnerId(ownerId);
            metadata.setOriginalFilename(validated.originalFilename());
            metadata.setStoredFilename(storedKey);
            metadata.setMimeType(validated.mimeType());
            metadata.setFileSize(validated.fileSize());
            metadata.setFileChecksum(validated.checksum());
            metadata.setStatus(DocumentStatus.UPLOADED);

            DocumentMetadata saved = documentRepository.save(metadata);
            recordLog(saved.getId(), "UPLOAD", "COMPLETED",
                    "Stored document (" + validated.fileSize() + " bytes)", 0L);

            log.info("Uploaded document id={} ownerId={} filename='{}' sizeBytes={}",
                    saved.getId(), ownerId, saved.getOriginalFilename(), saved.getFileSize());

            eventPublisher.publishEvent(new DocumentIngestionTrigger.DocumentReadyEvent(saved.getId()));
            return documentMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            fileStorageService.deleteQuietly(storedKey);
            throw ex;
        }
    }

    public BatchUploadResponse uploadMultiple(UUID ownerId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("NO_FILES_PROVIDED", "At least one file must be provided");
        }
        List<DocumentResponse> uploaded = new ArrayList<>();
        List<BatchUploadResponse.BatchUploadFailure> failures = new ArrayList<>();

        for (MultipartFile file : files) {
            String name = file != null && file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "unknown";
            try {
                uploaded.add(uploadDocument(ownerId, file));
            } catch (DomainException ex) {
                failures.add(new BatchUploadResponse.BatchUploadFailure(name, ex.getErrorCode(), ex.getMessage()));
            } catch (Exception ex) {
                log.error("Batch upload item failed filename='{}': {}", name, ex.getMessage());
                failures.add(new BatchUploadResponse.BatchUploadFailure(
                        name, "UPLOAD_FAILED", "Failed to store uploaded file"));
            }
        }
        return new BatchUploadResponse(uploaded, failures);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> listUserDocuments(
            UUID ownerId,
            DocumentStatus status,
            String search,
            Pageable pageable
    ) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Page<DocumentMetadata> page = documentRepository.searchUserDocuments(
                ownerId, status, normalizedSearch, pageable
        );
        return PagedResponse.from(page, documentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocumentDetails(UUID documentId, UUID ownerId) {
        DocumentMetadata doc = findOwnedDocumentOrThrow(documentId, ownerId);
        List<DocumentProcessingLog> logs = processingLogRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        return documentMapper.toDetailResponse(doc, logs);
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getDocumentStatus(UUID documentId, UUID ownerId) {
        DocumentMetadata doc = findOwnedDocumentOrThrow(documentId, ownerId);
        return documentMapper.toStatusResponse(doc);
    }

    @Transactional
    public DocumentStatusResponse reprocessDocument(UUID documentId, UUID ownerId) {
        DocumentMetadata doc = findOwnedDocumentOrThrow(documentId, ownerId);
        if (doc.getStatus() == DocumentStatus.PROCESSING || doc.getStatus() == DocumentStatus.DELETING) {
            throw new DomainException(HttpStatus.CONFLICT, "DOCUMENT_BUSY",
                    "Document is currently " + doc.getStatus().name().toLowerCase() + " and cannot be reprocessed");
        }
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setProcessingError(null);
        DocumentMetadata saved = documentRepository.save(doc);
        recordLog(saved.getId(), "REPROCESS", "REQUESTED", "Queued document for reprocessing", 0L);

        eventPublisher.publishEvent(new DocumentIngestionTrigger.DocumentReadyEvent(saved.getId()));
        log.info("Queued document id={} ownerId={} for reprocessing", saved.getId(), ownerId);
        return documentMapper.toStatusResponse(saved);
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID ownerId) {
        DocumentMetadata doc = findOwnedDocumentOrThrow(documentId, ownerId);
        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new DomainException(HttpStatus.CONFLICT, "DOCUMENT_PROCESSING",
                    "Cannot delete document while background processing is in progress");
        }
        doc.setStatus(DocumentStatus.DELETING);
        documentRepository.saveAndFlush(doc);

        VectorLifecyclePort vectorPort = vectorLifecycleProvider.getIfAvailable();
        if (vectorPort != null) {
            vectorPort.deleteByDocumentId(documentId);
        }

        String storedKey = doc.getStoredFilename();
        documentRepository.delete(doc);
        fileStorageService.deleteQuietly(storedKey);
        log.info("Deleted document id={} ownerId={}", documentId, ownerId);
    }

    private DocumentMetadata findOwnedDocumentOrThrow(UUID documentId, UUID ownerId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .filter(doc -> doc.getStatus() != DocumentStatus.DELETING)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private void recordLog(UUID documentId, String stage, String status, String message, Long durationMs) {
        DocumentProcessingLog logEntry = new DocumentProcessingLog();
        logEntry.setDocumentId(documentId);
        logEntry.setStage(stage);
        logEntry.setStatus(status);
        logEntry.setMessage(message);
        logEntry.setDurationMs(durationMs);
        processingLogRepository.save(logEntry);
    }
}
