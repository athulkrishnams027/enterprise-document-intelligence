package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.dto.request.CreateConversationRequest;
import com.athul.documentintelligence.dto.request.UpdateConversationRequest;
import com.athul.documentintelligence.dto.response.ChatMessageResponse;
import com.athul.documentintelligence.dto.response.ConversationResponse;
import com.athul.documentintelligence.dto.response.PagedResponse;
import com.athul.documentintelligence.security.SecurityUtils;
import com.athul.documentintelligence.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody(required = false) CreateConversationRequest request
    ) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createConversation(userId, request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ConversationResponse>> list(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(conversationService.listConversations(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> get(@PathVariable UUID id) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(conversationService.getConversation(id, userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ConversationResponse> rename(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationRequest request
    ) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(conversationService.renameConversation(id, userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        conversationService.deleteConversation(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<PagedResponse<ChatMessageResponse>> messages(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(conversationService.getMessages(id, userId, pageable));
    }
}
