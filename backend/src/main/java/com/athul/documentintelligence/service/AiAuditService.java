package com.athul.documentintelligence.service;

import com.athul.documentintelligence.entity.AiAuditLog;
import com.athul.documentintelligence.repository.AiAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {

    private final AiAuditRepository aiAuditRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID userId,
            UUID conversationId,
            UUID documentId,
            String operation,
            String provider,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            long durationMs,
            String status,
            String errorMessage
    ) {
        try {
            AiAuditLog entry = new AiAuditLog();
            entry.setUserId(userId);
            entry.setConversationId(conversationId);
            entry.setDocumentId(documentId);
            entry.setOperation(operation);
            entry.setProvider(provider);
            entry.setModel(model);
            entry.setPromptTokens(promptTokens);
            entry.setCompletionTokens(completionTokens);
            entry.setTotalTokens(totalTokens);
            entry.setDurationMs(durationMs);
            entry.setStatus(status);
            entry.setErrorMessage(errorMessage);
            aiAuditRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to persist AI audit log operation={}: {}", operation, ex.getMessage());
        }
    }
}
