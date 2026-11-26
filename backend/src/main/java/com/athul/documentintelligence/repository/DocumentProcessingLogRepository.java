package com.athul.documentintelligence.repository;

import com.athul.documentintelligence.entity.DocumentProcessingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentProcessingLogRepository extends JpaRepository<DocumentProcessingLog, UUID> {

    List<DocumentProcessingLog> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
