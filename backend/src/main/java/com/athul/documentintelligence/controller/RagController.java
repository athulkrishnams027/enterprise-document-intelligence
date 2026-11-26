package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.dto.request.RagAskRequest;
import com.athul.documentintelligence.dto.request.RagQuestionRequest;
import com.athul.documentintelligence.dto.response.RagAnswerResponse;
import com.athul.documentintelligence.security.SecurityUtils;
import com.athul.documentintelligence.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/conversations/{id}/chat")
    public ResponseEntity<RagAnswerResponse> chatInConversation(
            @PathVariable UUID id,
            @Valid @RequestBody RagQuestionRequest request
    ) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(ragService.askInConversation(id, userId, request));
    }

    @PostMapping("/rag/ask")
    public ResponseEntity<RagAnswerResponse> ask(@Valid @RequestBody RagAskRequest request) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(ragService.ask(userId, request));
    }
}
