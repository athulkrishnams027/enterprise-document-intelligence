package com.athul.documentintelligence.mapper;

import com.athul.documentintelligence.dto.response.DocumentDetailResponse;
import com.athul.documentintelligence.dto.response.DocumentProcessingLogResponse;
import com.athul.documentintelligence.dto.response.DocumentResponse;
import com.athul.documentintelligence.dto.response.DocumentStatusResponse;
import com.athul.documentintelligence.entity.DocumentMetadata;
import com.athul.documentintelligence.entity.DocumentProcessingLog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(DocumentMetadata doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getOriginalFilename(),
                doc.getMimeType(),
                doc.getFileSize(),
                doc.getStatus(),
                doc.getProcessingError(),
                doc.getTotalPages(),
                doc.getTotalChunks(),
                doc.getUploadedAt(),
                doc.getLastProcessedAt()
        );
    }

    public DocumentStatusResponse toStatusResponse(DocumentMetadata doc) {
        return new DocumentStatusResponse(
                doc.getId(),
                doc.getStatus(),
                doc.getProcessingError(),
                doc.getTotalPages(),
                doc.getTotalChunks(),
                doc.getLastProcessedAt()
        );
    }

    public DocumentDetailResponse toDetailResponse(DocumentMetadata doc, List<DocumentProcessingLog> logs) {
        List<DocumentProcessingLogResponse> logResponses = logs.stream()
                .map(l -> new DocumentProcessingLogResponse(
                        l.getId(),
                        l.getStage(),
                        l.getStatus(),
                        l.getMessage(),
                        l.getDurationMs(),
                        l.getCreatedAt()
                ))
                .toList();

        return new DocumentDetailResponse(
                doc.getId(),
                doc.getOwnerId(),
                doc.getOriginalFilename(),
                doc.getMimeType(),
                doc.getFileSize(),
                doc.getFileChecksum(),
                doc.getStatus(),
                doc.getProcessingError(),
                doc.getTotalPages(),
                doc.getTotalChunks(),
                doc.getExtractionSummary(),
                doc.getUploadedAt(),
                doc.getLastProcessedAt(),
                doc.getUpdatedAt(),
                logResponses
        );
    }
}
