package com.athul.documentintelligence.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Set;

/**
 * Filters Spring AI model auto-configurations dynamically based on configured providers
 * (app.ai.chat-provider / spring.ai.model.chat and app.ai.embedding-provider / spring.ai.model.embedding).
 * This allows Ollama, OpenAI, and Google Gemini starters to coexist on the classpath without requiring
 * credentials for inactive providers or causing NoUniqueBeanDefinitionException.
 */
public class AiProviderAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final Set<String> ALWAYS_EXCLUDED = Set.of(
            "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
            "org.springframework.ai.model.google.genai.autoconfigure.image.GoogleGenAiImageConnectionAutoConfiguration",
            "org.springframework.ai.model.google.genai.autoconfigure.image.GoogleGenAiImageAutoConfiguration"
    );

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata metadata) {
        String chatProvider = resolveProvider("app.ai.chat-provider", "spring.ai.model.chat");
        String embeddingProvider = resolveProvider("app.ai.embedding-provider", "spring.ai.model.embedding");

        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String candidate = autoConfigurationClasses[i];
            if (candidate == null) {
                matches[i] = true;
                continue;
            }
            matches[i] = isAllowed(candidate, chatProvider, embeddingProvider);
        }
        return matches;
    }

    private String resolveProvider(String primaryKey, String fallbackKey) {
        if (environment == null) {
            return "ollama";
        }
        String value = environment.getProperty(primaryKey);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(fallbackKey, "ollama");
        }
        String normalized = value.trim().toLowerCase();
        return "gemini".equals(normalized) ? "google-genai" : normalized;
    }

    private boolean isAllowed(String className, String chatProvider, String embeddingProvider) {
        if (ALWAYS_EXCLUDED.contains(className)) {
            return false;
        }
        return switch (className) {
            case "org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration" ->
                    "ollama".equals(chatProvider) || "ollama".equals(embeddingProvider);
            case "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration" ->
                    "ollama".equals(chatProvider);
            case "org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration" ->
                    "ollama".equals(embeddingProvider);
            case "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration" ->
                    "openai".equals(chatProvider);
            case "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration" ->
                    "openai".equals(embeddingProvider);
            case "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration" ->
                    "google-genai".equals(chatProvider);
            case "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration",
                 "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration" ->
                    "google-genai".equals(embeddingProvider);
            default -> true;
        };
    }
}
