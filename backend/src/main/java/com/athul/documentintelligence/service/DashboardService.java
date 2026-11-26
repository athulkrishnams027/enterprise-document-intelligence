package com.athul.documentintelligence.service;

import com.athul.documentintelligence.dto.response.DocumentResponse;
import com.athul.documentintelligence.dto.response.UserDashboardResponse;
import com.athul.documentintelligence.enums.DocumentStatus;
import com.athul.documentintelligence.mapper.DocumentMapper;
import com.athul.documentintelligence.repository.ConversationRepository;
import com.athul.documentintelligence.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final DocumentMapper documentMapper;

    @Transactional(readOnly = true)
    public UserDashboardResponse getUserDashboard(UUID userId) {
        long total = documentRepository.countByOwnerIdAndStatusNot(userId, DocumentStatus.DELETING);
        long completed = documentRepository.countByOwnerIdAndStatus(userId, DocumentStatus.COMPLETED);
        long processing = documentRepository.countByOwnerIdAndStatus(userId, DocumentStatus.PROCESSING)
                + documentRepository.countByOwnerIdAndStatus(userId, DocumentStatus.UPLOADED);
        long failed = documentRepository.countByOwnerIdAndStatus(userId, DocumentStatus.FAILED);
        long conversations = conversationRepository.countByUserId(userId);

        List<DocumentResponse> recentDocs = documentRepository
                .findTop5ByOwnerIdAndStatusNotOrderByUploadedAtDesc(userId, DocumentStatus.DELETING)
                .stream()
                .map(documentMapper::toResponse)
                .toList();

        return new UserDashboardResponse(total, completed, processing, failed, conversations, recentDocs);
    }
}
