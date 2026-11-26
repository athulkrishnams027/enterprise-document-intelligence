package com.athul.documentintelligence.service;

import com.athul.documentintelligence.dto.response.AdminDashboardResponse;
import com.athul.documentintelligence.dto.response.AiAuditLogResponse;
import com.athul.documentintelligence.dto.response.PagedResponse;
import com.athul.documentintelligence.dto.response.UserProfileResponse;
import com.athul.documentintelligence.enums.DocumentStatus;
import com.athul.documentintelligence.mapper.UserMapper;
import com.athul.documentintelligence.repository.AiAuditRepository;
import com.athul.documentintelligence.repository.ConversationRepository;
import com.athul.documentintelligence.repository.DocumentRepository;
import com.athul.documentintelligence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final AiAuditRepository aiAuditRepository;
    private final UserMapper userMapper;
    private final Map<String, Object> aiProviderDiagnostics;

    public AdminService(
            UserRepository userRepository,
            DocumentRepository documentRepository,
            ConversationRepository conversationRepository,
            AiAuditRepository aiAuditRepository,
            UserMapper userMapper,
            @Qualifier("aiProviderDiagnostics") Map<String, Object> aiProviderDiagnostics
    ) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.conversationRepository = conversationRepository;
        this.aiAuditRepository = aiAuditRepository;
        this.userMapper = userMapper;
        this.aiProviderDiagnostics = aiProviderDiagnostics;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getSystemDashboard() {
        return new AdminDashboardResponse(
                userRepository.count(),
                documentRepository.count(),
                documentRepository.countByStatus(DocumentStatus.COMPLETED),
                documentRepository.countByStatus(DocumentStatus.PROCESSING)
                        + documentRepository.countByStatus(DocumentStatus.UPLOADED),
                documentRepository.countByStatus(DocumentStatus.FAILED),
                conversationRepository.count(),
                aiAuditRepository.count(),
                aiAuditRepository.countByStatus("FAILED"),
                aiAuditRepository.sumTotalTokens(),
                aiProviderDiagnostics
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserProfileResponse> listUsers(Pageable pageable) {
        return PagedResponse.from(userRepository.findAll(pageable), userMapper::toProfileResponse);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AiAuditLogResponse> listAiUsage(Pageable pageable) {
        return PagedResponse.from(aiAuditRepository.findAllByOrderByCreatedAtDesc(pageable), log ->
                new AiAuditLogResponse(
                        log.getId(),
                        log.getUserId(),
                        log.getConversationId(),
                        log.getDocumentId(),
                        log.getOperation(),
                        log.getProvider(),
                        log.getModel(),
                        log.getPromptTokens(),
                        log.getCompletionTokens(),
                        log.getTotalTokens(),
                        log.getDurationMs(),
                        log.getStatus(),
                        log.getErrorMessage(),
                        log.getCreatedAt()
                )
        );
    }
}
