package com.athul.documentintelligence.repository;

import com.athul.documentintelligence.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
