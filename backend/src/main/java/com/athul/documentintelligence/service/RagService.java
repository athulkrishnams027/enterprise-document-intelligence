package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.dto.request.RagAskRequest;
import com.athul.documentintelligence.dto.request.RagQuestionRequest;
import com.athul.documentintelligence.dto.response.RagAnswerResponse;
import com.athul.documentintelligence.dto.response.SourceCitationResponse;
import com.athul.documentintelligence.dto.response.TokenUsageResponse;
import com.athul.documentintelligence.entity.ChatMessage;
import com.athul.documentintelligence.entity.Conversation;
import com.athul.documentintelligence.exception.AiProviderException;
import com.athul.documentintelligence.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Core Retrieval-Augmented Generation (RAG) orchestration pipeline:
 * 1. Validate conversation and document ownership
 * 2. Retrieve bounded conversation history for follow-ups
 * 3. Execute database-level authorized pgvector similarity search
 * 4. Guard against empty retrieval without hallucinating
 * 5. Build grounded prompt within context budget
 * 6. Invoke provider-agnostic Spring AI ChatClient
 * 7. Build verified citations strictly from supplied chunks
 * 8. Persist messages and AI audit logs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final String INSUFFICIENT_INFO_ANSWER =
            "I could not find relevant information in your uploaded documents to answer this question. "
                    + "Please ensure your documents have completed processing or try rephrasing your question.";

    private final ChatClient chatClient;
    private final VectorSearchService vectorSearchService;
    private final RagPromptBuilder promptBuilder;
    private final CitationService citationService;
    private final ConversationService conversationService;
    private final DocumentRepository documentRepository;
    private final AiAuditService aiAuditService;
    private final AiProperties aiProperties;

    public RagAnswerResponse askInConversation(UUID conversationId, UUID userId, RagQuestionRequest request) {
        Conversation conversation = conversationService.requireOwnedConversation(conversationId, userId);
        UUID scopedDocumentId = request.documentId() != null ? request.documentId() : conversation.getDocumentId();
        return executeRag(
                userId,
                conversation,
                scopedDocumentId,
                request.question(),
                request.topK(),
                request.similarityThreshold(),
                request.temperature()
        );
    }

    public RagAnswerResponse ask(UUID userId, RagAskRequest request) {
        Conversation conversation;
        if (request.conversationId() != null) {
            conversation = conversationService.requireOwnedConversation(request.conversationId(), userId);
        } else {
            conversation = conversationService.createAutoConversation(userId, request.documentId(), request.question());
        }
        UUID scopedDocumentId = request.documentId() != null ? request.documentId() : conversation.getDocumentId();
        return executeRag(
                userId,
                conversation,
                scopedDocumentId,
                request.question(),
                request.topK(),
                request.similarityThreshold(),
                request.temperature()
        );
    }

    private RagAnswerResponse executeRag(
            UUID userId,
            Conversation conversation,
            UUID scopedDocumentId,
            String rawQuestion,
            Integer topK,
            Double similarityThreshold,
            Double temperature
    ) {
        long startTime = System.currentTimeMillis();
        String question = rawQuestion.trim();

        if (scopedDocumentId != null) {
            conversationService.findAccessibleDocumentOrThrow(scopedDocumentId, userId);
        }

        List<ChatMessage> recentHistory = conversationService.getRecentControlledHistory(conversation.getId());
        conversationService.saveUserMessage(conversation, question);

        String retrievalQuery = promptBuilder.buildContextualRetrievalQuery(recentHistory, question);
        List<Document> retrieved = vectorSearchService.similaritySearch(
                retrievalQuery, userId, scopedDocumentId, topK, similarityThreshold
        );

        if (retrieved.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            ChatMessage assistantMsg = conversationService.saveAssistantMessage(
                    conversation, INSUFFICIENT_INFO_ANSWER, List.of(), elapsed, null
            );
            aiAuditService.record(
                    userId, conversation.getId(), scopedDocumentId,
                    "RAG_CHAT", aiProperties.chatProvider(), "no-retrieval-hit",
                    0, 0, 0, elapsed, "NO_CONTEXT", null
            );
            return new RagAnswerResponse(
                    conversation.getId(), assistantMsg.getId(), INSUFFICIENT_INFO_ANSWER,
                    List.of(), elapsed, null, false
            );
        }

        RagPromptBuilder.ContextSelection selection = promptBuilder.selectContextWithinBudget(retrieved);
        String userPrompt = promptBuilder.buildUserPrompt(question, selection.formattedContext(), recentHistory);
        double effectiveTemp = temperature != null ? temperature : aiProperties.rag().temperature();

        long llmStart = System.currentTimeMillis();
        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(promptBuilder.getSystemPrompt())
                    .user(userPrompt)
                    .options(ChatOptions.builder().temperature(effectiveTemp))
                    .call()
                    .chatResponse();

            long llmDuration = System.currentTimeMillis() - llmStart;
            String answer = extractAnswerText(chatResponse);
            TokenUsageResponse tokenUsage = extractTokenUsage(chatResponse);
            String modelName = extractModelName(chatResponse);

            List<SourceCitationResponse> citations = citationService.buildCitations(selection.suppliedDocuments());
            long totalDuration = System.currentTimeMillis() - startTime;

            ChatMessage assistantMsg = conversationService.saveAssistantMessage(
                    conversation, answer, citations, totalDuration, tokenUsage
            );

            aiAuditService.record(
                    userId,
                    conversation.getId(),
                    scopedDocumentId,
                    "RAG_CHAT",
                    aiProperties.chatProvider(),
                    modelName,
                    tokenUsage != null ? tokenUsage.promptTokens() : null,
                    tokenUsage != null ? tokenUsage.completionTokens() : null,
                    tokenUsage != null ? tokenUsage.totalTokens() : null,
                    llmDuration,
                    "SUCCESS",
                    null
            );

            log.info("Completed RAG answer conversationId={} userId={} sources={} durationMs={}",
                    conversation.getId(), userId, citations.size(), totalDuration);

            return new RagAnswerResponse(
                    conversation.getId(),
                    assistantMsg.getId(),
                    answer,
                    citations,
                    totalDuration,
                    tokenUsage,
                    true
            );
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("LLM generation failed conversationId={} provider='{}': {}",
                    conversation.getId(), aiProperties.chatProvider(), ex.getMessage());
            aiAuditService.record(
                    userId, conversation.getId(), scopedDocumentId,
                    "RAG_CHAT", aiProperties.chatProvider(), aiProperties.chatProvider(),
                    null, null, null, elapsed, "FAILED",
                    ex.getMessage() != null && ex.getMessage().length() > 500
                            ? ex.getMessage().substring(0, 500) : ex.getMessage()
            );
            throw new AiProviderException(
                    "AI_PROVIDER_ERROR",
                    "AI provider '" + aiProperties.chatProvider() + "' failed to generate a response. "
                            + "Please verify the AI service is running and try again.",
                    ex
            );
        }
    }

    private String extractAnswerText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return INSUFFICIENT_INFO_ANSWER;
        }
        String text = response.getResult().getOutput().getText();
        return (text == null || text.isBlank()) ? INSUFFICIENT_INFO_ANSWER : text.trim();
    }

    private TokenUsageResponse extractTokenUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage.getPromptTokens() == null && usage.getCompletionTokens() == null && usage.getTotalTokens() == null) {
            return null;
        }
        return new TokenUsageResponse(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private String extractModelName(ChatResponse response) {
        if (response != null && response.getMetadata() != null && response.getMetadata().getModel() != null) {
            return response.getMetadata().getModel();
        }
        return aiProperties.chatProvider();
    }
}
