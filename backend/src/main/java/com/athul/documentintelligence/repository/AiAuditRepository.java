package com.athul.documentintelligence.repository;

import com.athul.documentintelligence.entity.AiAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiAuditRepository extends JpaRepository<AiAuditLog, java.util.UUID> {

    Page<AiAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AiAuditLog a")
    long sumTotalTokens();
}
