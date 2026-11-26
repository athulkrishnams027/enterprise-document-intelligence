package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.service.DocumentIngestionProcessor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class DocumentControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private DocumentIngestionProcessor documentIngestionProcessor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void documentUploadListDetailsDeleteAndUserIsolation() throws Exception {
        String userAToken = registerAndGetToken("Alice Owner", "alice-docs@docintel.io");
        String userBToken = registerAndGetToken("Bob Intruder", "bob-docs@docintel.io");

        byte[] pdfBytes = createSimplePdf("Employees receive 24 days of annual leave.");
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "Employee_Handbook.pdf", "application/pdf", pdfBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(pdfFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("Employee_Handbook.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andReturn();

        String docId = extractField(uploadResult.getResponse().getContentAsString(), "id");
        assertThat(docId).isNotBlank();

        // Duplicate upload by same user is rejected with 409
        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(pdfFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        // Different user can upload identical content independently
        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(pdfFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isCreated());

        // User A lists their documents with search filter
        mockMvc.perform(get("/api/v1/documents")
                        .param("search", "Handbook")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(docId));

        // User A can view document details
        mockMvc.perform(get("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingLogs").isArray());

        // User B CANNOT access or delete User A's document
        mockMvc.perform(get("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // Multi-upload with one valid TXT and one invalid PDF
        MockMultipartFile validTxt = new MockMultipartFile(
                "files", "guide.txt", "text/plain",
                "Plain text knowledge base".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile invalidPdf = new MockMultipartFile(
                "files", "bad.pdf", "application/pdf",
                "fake pdf".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/documents/upload-multiple")
                        .file(validTxt)
                        .file(invalidPdf)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploaded.length()").value(1))
                .andExpect(jsonPath("$.failures.length()").value(1));

        // User A deletes their document
        mockMvc.perform(delete("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetToken(String name, String email) throws Exception {
        String body = """
                {"fullName":"%s","email":"%s","password":"StrongPassword123!"}
                """.formatted(name, email);
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
