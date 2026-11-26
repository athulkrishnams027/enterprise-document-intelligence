package com.athul.documentintelligence.service;

import com.athul.documentintelligence.model.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PgVectorStoreIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @TestConfiguration
    static class DeterministicEmbeddingConfig {
        @Bean
        @Primary
        EmbeddingModel deterministicTestEmbeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    List<Embedding> embeddings = new ArrayList<>();
                    List<String> instructions = request.getInstructions();
                    for (int i = 0; i < instructions.size(); i++) {
                        embeddings.add(new Embedding(embed(instructions.get(i)), i));
                    }
                    return new EmbeddingResponse(embeddings);
                }

                @Override
                public float[] embed(Document document) {
                    return embed(document.getText());
                }

                @Override
                public float[] embed(String text) {
                    float[] vec = new float[768];
                    String lower = text == null ? "" : text.toLowerCase();
                    // Simple deterministic topic axes for testing real pgvector cosine filtering
                    vec[0] = lower.contains("leave") || lower.contains("vacation") ? 0.95f : 0.05f;
                    vec[1] = lower.contains("security") || lower.contains("jwt") ? 0.95f : 0.05f;
                    vec[2] = lower.contains("expense") || lower.contains("travel") ? 0.95f : 0.05f;
                    for (int i = 3; i < 768; i++) {
                        vec[i] = 0.001f;
                    }
                    return vec;
                }
            };
        }
    }

    @Autowired
    private VectorSearchService vectorSearchService;

    @Test
    void insertsSearchesFiltersByOwnerAndDocumentAndDeletesCleanly() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID aliceDoc1 = UUID.randomUUID();
        UUID aliceDoc2 = UUID.randomUUID();
        UUID bobDoc = UUID.randomUUID();

        DocumentChunk aliceLeaveChunk = new DocumentChunk(
                UUID.nameUUIDFromBytes((aliceDoc1 + ":0").getBytes(StandardCharsets.UTF_8)),
                "chunk-0", aliceDoc1, aliceId, "Employee_Handbook.pdf", 12, "Page 12",
                0, 20, "hash-a1", "Employees receive 24 days of annual leave per calendar year."
        );
        DocumentChunk aliceExpenseChunk = new DocumentChunk(
                UUID.nameUUIDFromBytes((aliceDoc2 + ":0").getBytes(StandardCharsets.UTF_8)),
                "chunk-0", aliceDoc2, aliceId, "Travel_Policy.pdf", 3, "Page 3",
                0, 18, "hash-a2", "Travel expense claims must be submitted within 14 days."
        );
        DocumentChunk bobLeaveChunk = new DocumentChunk(
                UUID.nameUUIDFromBytes((bobDoc + ":0").getBytes(StandardCharsets.UTF_8)),
                "chunk-0", bobDoc, bobId, "Bob_Private_Leave.pdf", 1, "Page 1",
                0, 20, "hash-b1", "Bob confidential leave policy: 30 days annual leave."
        );

        vectorSearchService.indexChunks(List.of(aliceLeaveChunk, aliceExpenseChunk, bobLeaveChunk));

        // 1. Alice searches for leave -> only Alice's chunk is returned, never Bob's
        List<Document> aliceResults = vectorSearchService.similaritySearch(
                "What is the annual leave policy?", aliceId, null, 5, 0.5
        );
        assertThat(aliceResults).isNotEmpty();
        assertThat(aliceResults)
                .allSatisfy(doc -> assertThat(doc.getMetadata().get("ownerId")).isEqualTo(aliceId.toString()));
        assertThat(aliceResults.get(0).getMetadata().get("originalFilename")).isEqualTo("Employee_Handbook.pdf");
        assertThat(aliceResults.get(0).getMetadata().get("pageNumber")).isEqualTo(12);

        // 2. Document-specific filter restricts to aliceDoc2 only
        List<Document> docScoped = vectorSearchService.similaritySearch(
                "annual leave and expense", aliceId, aliceDoc2, 5, 0.2
        );
        assertThat(docScoped).hasSize(1);
        assertThat(docScoped.get(0).getMetadata().get("documentId")).isEqualTo(aliceDoc2.toString());

        // 3. Delete aliceDoc1 vectors -> leaves aliceDoc2 and bobDoc intact
        vectorSearchService.deleteByDocumentId(aliceDoc1);

        List<Document> afterDelete = vectorSearchService.similaritySearch(
                "annual leave", aliceId, aliceDoc1, 5, 0.1
        );
        assertThat(afterDelete).isEmpty();

        List<Document> bobResults = vectorSearchService.similaritySearch(
                "annual leave", bobId, null, 5, 0.5
        );
        assertThat(bobResults).hasSize(1);
        assertThat(bobResults.get(0).getMetadata().get("documentId")).isEqualTo(bobDoc.toString());
    }
}
