package com.athul.documentintelligence.service;

import com.athul.documentintelligence.enums.SupportedDocumentType;
import com.athul.documentintelligence.exception.BadRequestException;
import com.athul.documentintelligence.model.ExtractionResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentExtractionServiceTest {

    private final DocumentExtractionService extractionService = new DocumentExtractionService();

    @Test
    void extractsPdfWithPageBoundariesAndSkipsEmptyPages() throws Exception {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage p1 = new PDPage();
            PDPage p2Empty = new PDPage();
            PDPage p3 = new PDPage();
            doc.addPage(p1);
            doc.addPage(p2Empty);
            doc.addPage(p3);

            writeText(doc, p1, "First page leave policy.");
            writeText(doc, p3, "Third page expense reimbursement.");
            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        ExtractionResult result = extractionService.extract(SupportedDocumentType.PDF, pdfBytes, "policy.pdf");
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.sections()).hasSize(2);
        assertThat(result.sections().get(0).pageNumber()).isEqualTo(1);
        assertThat(result.sections().get(0).text()).contains("First page leave policy");
        assertThat(result.sections().get(1).pageNumber()).isEqualTo(3);
        assertThat(result.sections().get(1).text()).contains("Third page expense reimbursement");
    }

    @Test
    void rejectsEncryptedAndCorruptPdfGracefully() throws Exception {
        byte[] encryptedPdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            writeText(doc, page, "Secret content");
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            doc.protect(policy);
            doc.save(out);
            encryptedPdf = out.toByteArray();
        }

        assertThatThrownBy(() -> extractionService.extract(SupportedDocumentType.PDF, encryptedPdf, "secret.pdf"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password");

        assertThatThrownBy(() -> extractionService.extract(
                SupportedDocumentType.PDF, "%PDF-corrupt".getBytes(StandardCharsets.UTF_8), "broken.pdf"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void extractsDocxWithSectionsAndNullPageNumber() throws Exception {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph heading = doc.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Remote Work Policy");

            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("Employees may work remotely up to three days per week.");
            doc.write(out);
            docxBytes = out.toByteArray();
        }

        ExtractionResult result = extractionService.extract(SupportedDocumentType.DOCX, docxBytes, "remote.docx");
        assertThat(result.totalPages()).isNull();
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().get(0).pageNumber()).isNull();
        assertThat(result.sections().get(0).sectionTitle()).isEqualTo("Remote Work Policy");
        assertThat(result.sections().get(0).text()).contains("three days per week");
    }

    @Test
    void extractsUtf8TxtAndHandlesInvalidEncoding() {
        byte[] valid = "Line 1: Onboarding checklist.\n\nLine 2: Security training.".getBytes(StandardCharsets.UTF_8);
        ExtractionResult result = extractionService.extract(SupportedDocumentType.TXT, valid, "onboarding.txt");
        assertThat(result.totalPages()).isNull();
        assertThat(result.sections().get(0).pageNumber()).isNull();
        assertThat(result.sections().get(0).text()).contains("Onboarding checklist");

        byte[] invalidUtf8 = new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x80};
        assertThatThrownBy(() -> extractionService.extract(SupportedDocumentType.TXT, invalidUtf8, "bad.txt"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("UTF-8");
    }

    private void writeText(PDDocument doc, PDPage page, String text) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            cs.newLineAtOffset(50, 700);
            cs.showText(text);
            cs.endText();
        }
    }
}
