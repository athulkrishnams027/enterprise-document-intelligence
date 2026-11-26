package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.service.DocumentIngestionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class RagAndConversationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    private static final AtomicInteger CHAT_CALL_COUNT = new AtomicInteger(0);

    @TestConfiguration
    static class TestAiConfiguration {
        @Bean
        @Primary
        EmbeddingModel ragTestEmbeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    List<Embedding> out = new ArrayList<>();
                    for (int i = 0; i < request.getInstructions().size(); i++) {
                        out.add(new Embedding(embed(request.getInstructions().get(i)), i));
                    }
                    return new EmbeddingResponse(out);
                }

                @Override
                public float[] embed(Document document) {
                    return embed(document.getText());
                }

                @Override
                public float[] embed(String text) {
                    float[] vec = new float[768];
                    String lower = text == null ? "" : text.toLowerCase();
                    if (lower.contains("leave") || lower.contains("vacation") || lower.contains("days")) {
                        vec[0] = 0.98f;
                        vec[1] = 0.05f;
                    } else if (lower.contains("quantum") || lower.contains("astronomy")) {
                        vec[0] = -0.95f;
                        vec[1] = 0.05f;
                        vec[50] = 0.95f;
                    } else {
                        vec[0] = 0.1f;
                        vec[1] = 0.9f;
                    }
                    for (int i = 2; i < 768; i++) {
                        if (i != 50) {
                            vec[i] = 0.001f;
                        }
                    }
                    return vec;
                }
            };
        }

        @Bean
        @Primary
        ChatModel ragTestChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    CHAT_CALL_COUNT.incrementAndGet();
                    String contents = prompt.getContents();
                    String reply = contents.contains("24 days")
                            ? "Employees receive 24 days of annual leave per calendar year [Source 1]."
                            : "Based on the supplied excerpts, here is the relevant policy detail [Source 1].";

                    ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                            .model("qwen2.5:7b")
                            .usage(new DefaultUsage(120, 24, 144))
                            .build();
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))), metadata);
                }
            };
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DocumentIngestionService ingestionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CHAT_CALL_COUNT.set(0);
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void endToEndRagPipelineWithCitationsHistoryAndIsolation() throws Exception {
        String aliceToken = registerAndGetToken("Alice Rag", "alice-rag@docintel.io");
        String bobToken = registerAndGetToken("Bob Rag", "bob-rag@docintel.io");

        byte[] pdfBytes = createSimplePdf("Employees receive 24 days of annual leave per calendar year.");
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "Employee_Handbook.pdf", "application/pdf", pdfBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(pdf)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isCreated())
                .andReturn();

        UUID docId = UUID.fromString(extractField(uploadResult.getResponse().getContentAsString(), "id"));

        // Wait for async background ingestion triggered after upload commit to complete
        awaitDocumentCompleted(docId, aliceToken);

        mockMvc.perform(get("/api/v1/documents/" + docId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalChunks").value(1));

        // 1. Ask grounded question
        MvcResult askResult = mockMvc.perform(post("/api/v1/rag/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the annual leave policy?\",\"documentId\":\"" + docId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.answer").value("Employees receive 24 days of annual leave per calendar year [Source 1]."))
                .andExpect(jsonPath("$.sources[0].documentId").value(docId.toString()))
                .andExpect(jsonPath("$.sources[0].documentName").value("Employee_Handbook.pdf"))
                .andExpect(jsonPath("$.sources[0].pageNumber").value(1))
                .andExpect(jsonPath("$.sources[0].chunkId").value("chunk-0"))
                .andExpect(jsonPath("$.tokenUsage.totalTokens").value(144))
                .andReturn();

        String conversationId = extractField(askResult.getResponse().getContentAsString(), "conversationId");
        int callsAfterGrounded = CHAT_CALL_COUNT.get();
        assertThat(callsAfterGrounded).isEqualTo(1);

        // 2. Follow-up question in same conversation
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many days?\",\"similarityThreshold\":0.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.grounded").value(true));

        // 3. Unrelated question with no relevant chunks returns insufficient info WITHOUT calling LLM
        int callsBeforeNoContext = CHAT_CALL_COUNT.get();
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Explain quantum astronomy and astrophysics black hole singularities in deep space\",\"similarityThreshold\":0.8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.sources").isEmpty());
        assertThat(CHAT_CALL_COUNT.get()).isEqualTo(callsBeforeNoContext);

        // 4. Messages endpoint returns persisted history with JSONB citations
        mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));

        // 5. Rename conversation
        mockMvc.perform(patch("/api/v1/conversations/" + conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Annual Leave Discussion\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Annual Leave Discussion"));

        // 6. Bob cannot access Alice's conversation or ask against Alice's document
        mockMvc.perform(get("/api/v1/conversations/" + conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/rag/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is annual leave?\",\"documentId\":\"" + docId + "\"}"))
                .andExpect(status().isNotFound());

        // 7. User dashboard summary reflects Alice's document and conversation
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedDocuments").value(1))
                .andExpect(jsonPath("$.totalConversations").value(1));

        // 8. Delete conversation and document
        mockMvc.perform(delete("/api/v1/conversations/" + conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());
    }

    private void awaitDocumentCompleted(UUID docId, String token) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/v1/documents/" + docId + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = res.getResponse().getContentAsString();
            if (body.contains("\"COMPLETED\"")) {
                return;
            }
            if (body.contains("\"FAILED\"")) {
                throw new AssertionError("Async ingestion failed: " + body);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for document " + docId + " to reach COMPLETED");
    }

    private String registerAndGetToken(String name, String email) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"StrongPassword123!\"}"
                .formatted(name, email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractField(result.getResponse().getContentAsString(), "accessToken");
    }

    private String extractField(String json, String fieldName) {
        Matcher m = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private byte[] createSimplePdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
