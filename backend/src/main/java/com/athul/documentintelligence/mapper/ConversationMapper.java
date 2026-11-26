package com.athul.documentintelligence.mapper;

import com.athul.documentintelligence.dto.response.ChatMessageResponse;
import com.athul.documentintelligence.dto.response.ConversationResponse;
import com.athul.documentintelligence.entity.ChatMessage;
import com.athul.documentintelligence.entity.Conversation;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class ConversationMapper {

    public ConversationResponse toResponse(Conversation conversation, String documentName) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getDocumentId(),
                documentName,
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    public ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getCitations() != null ? message.getCitations() : Collections.emptyList(),
                message.getProcessingTimeMs(),
                message.getTokenUsage(),
                message.getCreatedAt()
        );
    }
}
