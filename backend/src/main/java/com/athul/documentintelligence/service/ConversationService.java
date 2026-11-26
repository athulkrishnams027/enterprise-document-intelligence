package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.dto.request.CreateConversationRequest;
import com.athul.documentintelligence.dto.request.UpdateConversationRequest;
import com.athul.documentintelligence.dto.response.ChatMessageResponse;
import com.athul.documentintelligence.dto.response.ConversationResponse;
import com.athul.documentintelligence.dto.response.PagedResponse;
import com.athul.documentintelligence.dto.response.SourceCitationResponse;
import com.athul.documentintelligence.dto.response.TokenUsageResponse;
import com.athul.documentintelligence.entity.ChatMessage;
import com.athul.documentintelligence.entity.Conversation;
import com.athul.documentintelligence.entity.DocumentMetadata;
import com.athul.documentintelligence.enums.DocumentStatus;
import com.athul.documentintelligence.enums.MessageRole;
import com.athul.documentintelligence.exception.ResourceNotFoundException;
import com.athul.documentintelligence.mapper.ConversationMapper;
import com.athul.documentintelligence.repository.ChatMessageRepository;
import com.athul.documentintelligence.repository.ConversationRepository;
import com.athul.documentintelligence.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final ConversationMapper conversationMapper;
    private final AiProperties aiProperties;

    @Transactional
    public ConversationResponse createConversation(UUID userId, CreateConversationRequest request) {
        String documentName = null;
        UUID documentId = request != null ? request.documentId() : null;
        if (documentId != null) {
            DocumentMetadata doc = findAccessibleDocumentOrThrow(documentId, userId);
            documentName = doc.getOriginalFilename();
        }

        String rawTitle = request != null ? request.title() : null;
        String title = (rawTitle == null || rawTitle.isBlank())
                ? (documentName != null ? "Chat: " + documentName : "New Document Q&A")
                : rawTitle.trim();

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setDocumentId(documentId);
        conversation.setTitle(title.length() > 255 ? title.substring(0, 255) : title);

        Conversation saved = conversationRepository.save(conversation);
        return conversationMapper.toResponse(saved, documentName);
    }

    @Transactional
    public Conversation createAutoConversation(UUID userId, UUID documentId, String firstQuestion) {
        if (documentId != null) {
            findAccessibleDocumentOrThrow(documentId, userId);
        }
        String clean = firstQuestion.replaceAll("\\s+", " ").trim();
        String title = clean.length() > 80 ? clean.substring(0, 77) + "..." : clean;

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setDocumentId(documentId);
        conversation.setTitle(title.isEmpty() ? "Document Q&A" : title);
        return conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ConversationResponse> listConversations(UUID userId, Pageable pageable) {
        Page<Conversation> page = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
        Set<UUID> docIds = page.getContent().stream()
                .map(Conversation::getDocumentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<UUID, String> docNames = docIds.isEmpty()
                ? Map.of()
                : documentRepository.findAllById(docIds).stream()
                        .collect(Collectors.toMap(DocumentMetadata::getId, DocumentMetadata::getOriginalFilename));

        return PagedResponse.from(page, conv -> conversationMapper.toResponse(
                conv, conv.getDocumentId() != null ? docNames.get(conv.getDocumentId()) : null
        ));
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, UUID userId) {
        Conversation conversation = requireOwnedConversation(conversationId, userId);
        String docName = resolveDocumentName(conversation.getDocumentId(), userId);
        return conversationMapper.toResponse(conversation, docName);
    }

    @Transactional
    public ConversationResponse renameConversation(
            UUID conversationId,
            UUID userId,
            UpdateConversationRequest request
    ) {
        Conversation conversation = requireOwnedConversation(conversationId, userId);
        conversation.setTitle(request.title().trim());
        Conversation saved = conversationRepository.save(conversation);
        String docName = resolveDocumentName(saved.getDocumentId(), userId);
        return conversationMapper.toResponse(saved, docName);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        Conversation conversation = requireOwnedConversation(conversationId, userId);
        conversationRepository.delete(conversation);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ChatMessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable) {
        requireOwnedConversation(conversationId, userId);
        Page<ChatMessage> page = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
        return PagedResponse.from(page, conversationMapper::toMessageResponse);
    }

    @Transactional(readOnly = true)
    public Conversation requireOwnedConversation(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentControlledHistory(UUID conversationId) {
        int maxMessages = Math.max(2, aiProperties.rag().maxHistoryTurns() * 2);
        List<ChatMessage> latestDesc = chatMessageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        List<ChatMessage> bounded = latestDesc.size() > maxMessages
                ? new ArrayList<>(latestDesc.subList(0, maxMessages))
                : new ArrayList<>(latestDesc);
        Collections.reverse(bounded);
        return bounded;
    }

    @Transactional
    public ChatMessage saveUserMessage(Conversation conversation, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setRole(MessageRole.USER);
        message.setContent(content);
        ChatMessage saved = chatMessageRepository.save(message);
        conversationRepository.save(conversation);
        return saved;
    }

    @Transactional
    public ChatMessage saveAssistantMessage(
            Conversation conversation,
            String answer,
            List<SourceCitationResponse> citations,
            long processingTimeMs,
            TokenUsageResponse tokenUsage
    ) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(answer);
        message.setCitations(citations);
        message.setProcessingTimeMs(processingTimeMs);
        message.setTokenUsage(tokenUsage);
        ChatMessage saved = chatMessageRepository.save(message);
        conversationRepository.save(conversation);
        return saved;
    }

    @Transactional(readOnly = true)
    public DocumentMetadata findAccessibleDocumentOrThrow(UUID documentId, UUID userId) {
        return documentRepository.findByIdAndOwnerId(documentId, userId)
                .filter(doc -> doc.getStatus() != DocumentStatus.DELETING)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private String resolveDocumentName(UUID documentId, UUID userId) {
        if (documentId == null) {
            return null;
        }
        return documentRepository.findByIdAndOwnerId(documentId, userId)
                .map(DocumentMetadata::getOriginalFilename)
                .orElse(null);
    }
}
