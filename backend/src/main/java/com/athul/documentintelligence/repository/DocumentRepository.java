package com.athul.documentintelligence.repository;

import com.athul.documentintelligence.entity.DocumentMetadata;
import com.athul.documentintelligence.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentMetadata, UUID> {

    Optional<DocumentMetadata> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndFileChecksum(UUID ownerId, String fileChecksum);

    @Query("""
            SELECT d FROM DocumentMetadata d
            WHERE d.ownerId = :ownerId
              AND d.status <> com.athul.documentintelligence.enums.DocumentStatus.DELETING
              AND (:status IS NULL OR d.status = :status)
              AND (:search IS NULL OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<DocumentMetadata> searchUserDocuments(
            @Param("ownerId") UUID ownerId,
            @Param("status") DocumentStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    long countByOwnerIdAndStatusNot(UUID ownerId, DocumentStatus excludedStatus);

    long countByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);

    long countByStatus(DocumentStatus status);

    List<DocumentMetadata> findTop5ByOwnerIdAndStatusNotOrderByUploadedAtDesc(
            UUID ownerId,
            DocumentStatus excludedStatus
    );
}
