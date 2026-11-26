package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.entity.Role;
import com.athul.documentintelligence.entity.User;
import com.athul.documentintelligence.enums.RoleName;
import com.athul.documentintelligence.repository.RoleRepository;
import com.athul.documentintelligence.repository.UserRepository;
import com.athul.documentintelligence.service.DocumentIngestionService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class EnterpriseHardeningIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    private static final AtomicBoolean SIMULATE_CHAT_FAILURE = new AtomicBoolean(false);

    @TestConfiguration
    static class HardeningTestAiConfig {
        @Bean
        @Primary
        EmbeddingModel hardeningEmbeddingModel() {
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
                    vec[0] = lower.contains("security") || lower.contains("rotation") ? 0.96f : 0.05f;
                    vec[1] = lower.contains("onboarding") || lower.contains("laptop") ? 0.96f : 0.05f;
                    for (int i = 2; i < 768; i++) {
                        vec[i] = 0.001f;
                    }
                    return vec;
                }
            };
        }

        @Bean
        @Primary
        ChatModel hardeningChatModel() {
            return (Prompt prompt) -> {
                if (SIMULATE_CHAT_FAILURE.get()) {
                    throw new RuntimeException("Simulated Ollama timeout");
                }
                ChatResponseMetadata meta = ChatResponseMetadata.builder()
                        .model("qwen2.5:7b")
                        .usage(new DefaultUsage(80, 20, 100))
                        .build();
                return new ChatResponse(
                        List.of(new Generation(new AssistantMessage("Verified policy response [Source 1]."))),
                        meta
                );
            };
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DocumentIngestionService ingestionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SIMULATE_CHAT_FAILURE.set(false);
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void verifiesDocxTxtRagCitationsProviderFailureAndAdminEndpoints() throws Exception {
        String userToken = registerAndGetToken("Doc User", "docx-txt-user@docintel.io");

        // 1. DOCX ingestion and RAG with null pageNumber (not 0)
        byte[] docxBytes = createDocx("Security Guidelines", "API keys must undergo mandatory rotation every 90 days.");
        MockMultipartFile docxFile = new MockMultipartFile(
                "file", "security_policy.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );
        MvcResult docxUpload = mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(docxFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andReturn();
        UUID docxId = UUID.fromString(extractField(docxUpload.getResponse().getContentAsString(), "id"));
        awaitDocumentCompleted(docxId, userToken);

        mockMvc.perform(post("/api/v1/rag/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the API key rotation schedule?\",\"documentId\":\"" + docxId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources[0].documentName").value("security_policy.docx"))
                .andExpect(jsonPath("$.sources[0].pageNumber").value(nullValue()))
                .andExpect(jsonPath("$.sources[0].sectionTitle").value("Security Guidelines"));

        // 2. TXT ingestion and RAG with null pageNumber
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "onboarding.txt", "text/plain",
                "New hires receive their corporate laptop on day one of onboarding.".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult txtUpload = mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(txtFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andReturn();
        UUID txtId = UUID.fromString(extractField(txtUpload.getResponse().getContentAsString(), "id"));
        awaitDocumentCompleted(txtId, userToken);

        mockMvc.perform(post("/api/v1/rag/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"When do new hires receive a laptop?\",\"documentId\":\"" + txtId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources[0].documentName").value("onboarding.txt"))
                .andExpect(jsonPath("$.sources[0].pageNumber").value(nullValue()));

        // 3. Simulated AI provider failure returns 503 with safe error code & no stack trace
        SIMULATE_CHAT_FAILURE.set(true);
        mockMvc.perform(post("/api/v1/rag/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"When do new hires receive a laptop?\",\"documentId\":\"" + txtId + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
        SIMULATE_CHAT_FAILURE.set(false);

        // 4. Seed ADMIN and verify admin endpoints + actuator protection
        String adminToken = createAdminAndLogin("admin-sec@docintel.io", "AdminSecret123!");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAiRequests").isNumber())
                .andExpect(jsonPath("$.failedAiRequests").value(1));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/admin/ai-usage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
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

    private String createAdminAndLogin(String email, String rawPassword) throws Exception {
        if (!userRepository.existsByEmailIgnoreCase(email)) {
            Role adminRole = roleRepository.findByName(RoleName.ADMIN.name()).orElseThrow();
            Role userRole = roleRepository.findByName(RoleName.USER.name()).orElseThrow();
            User admin = new User();
            admin.setEmail(email);
            admin.setFullName("Admin User");
            admin.setPasswordHash(passwordEncoder.encode(rawPassword));
            admin.setEnabled(true);
            admin.setRoles(Set.of(adminRole, userRole));
            userRepository.save(admin);
        }
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private String extractField(String json, String fieldName) {
        Matcher m = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private byte[] createDocx(String headingText, String bodyText) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph h = doc.createParagraph();
            h.setStyle("Heading1");
            h.createRun().setText(headingText);
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(bodyText);
            doc.write(out);
            return out.toByteArray();
        }
    }
}
