package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.StorageProperties;
import com.athul.documentintelligence.enums.SupportedDocumentType;
import com.athul.documentintelligence.exception.BadRequestException;
import com.athul.documentintelligence.exception.DomainException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentValidationServiceTest {

    private final DocumentValidationService validationService =
            new DocumentValidationService(new StorageProperties("./target/test-storage", 1024 * 1024));

    @Test
    void validatesRealTxtPdfAndDocxDocuments() throws Exception {
        MockMultipartFile txt = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "Enterprise policy overview.\nAnnual leave is 24 days.".getBytes(StandardCharsets.UTF_8)
        );
        var txtResult = validationService.validate(txt);
        assertThat(txtResult.documentType()).isEqualTo(SupportedDocumentType.TXT);
        assertThat(txtResult.checksum()).hasSize(64);

        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            pdfBytes = out.toByteArray();
        }
        MockMultipartFile pdf = new MockMultipartFile("file", "handbook.pdf", "application/pdf", pdfBytes);
        assertThat(validationService.validate(pdf).documentType()).isEqualTo(SupportedDocumentType.PDF);

        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Sample DOCX content");
            doc.write(out);
            docxBytes = out.toByteArray();
        }
        MockMultipartFile docx = new MockMultipartFile(
                "file", "spec.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );
        assertThat(validationService.validate(docx).documentType()).isEqualTo(SupportedDocumentType.DOCX);
    }

    @Test
    void rejectsSpoofedPdfExtensionAndUnsupportedTypes() {
        MockMultipartFile spoofedPdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf",
                "Not actually a PDF".getBytes(StandardCharsets.UTF_8)
        );
        assertThatThrownBy(() -> validationService.validate(spoofedPdf))
                .isInstanceOf(BadRequestException.class);

        MockMultipartFile exe = new MockMultipartFile(
                "file", "script.sh", "text/plain", "#!/bin/sh".getBytes(StandardCharsets.UTF_8)
        );
        assertThatThrownBy(() -> validationService.validate(exe))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> validationService.validate(empty))
                .isInstanceOf(BadRequestException.class);

        byte[] oversized = new byte[2 * 1024 * 1024];
        MockMultipartFile tooBig = new MockMultipartFile("file", "big.txt", "text/plain", oversized);
        assertThatThrownBy(() -> validationService.validate(tooBig))
                .isInstanceOf(DomainException.class);
    }
}
