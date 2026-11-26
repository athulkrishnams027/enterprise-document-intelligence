package com.athul.documentintelligence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/**
 * Provider-agnostic AI wiring using Spring AI ChatClient and EmbeddingModel abstractions.
 * Active providers are selected via externalized configuration (spring.ai.model.chat / embedding).
 */
@Slf4j
@Configuration
public class AiConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatModel activeChatModel, AiProperties aiProperties) {
        log.info("Initializing Spring AI ChatClient with configured provider='{}' and implementation={}",
                aiProperties.chatProvider(), activeChatModel.getClass().getSimpleName());
        return ChatClient.builder(activeChatModel).build();
    }

    /**
     * Exposes provider metadata for observability and admin diagnostics without coupling
     * business services to concrete provider classes.
     */
    @Bean
    @ConditionalOnMissingBean(name = "aiProviderDiagnostics")
    public Map<String, Object> aiProviderDiagnostics(
            ChatModel activeChatModel,
            EmbeddingModel activeEmbeddingModel,
            AiProperties aiProperties
    ) {
        log.info("Active EmbeddingModel implementation={} (expectedDimensions={})",
                activeEmbeddingModel.getClass().getSimpleName(),
                aiProperties.expectedEmbeddingDimensions());
        return Map.of(
                "chatProvider", aiProperties.chatProvider(),
                "chatModelBean", activeChatModel.getClass().getSimpleName(),
                "embeddingProvider", aiProperties.embeddingProvider(),
                "embeddingModelBean", activeEmbeddingModel.getClass().getSimpleName(),
                "expectedEmbeddingDimensions", aiProperties.expectedEmbeddingDimensions()
        );
    }
}
