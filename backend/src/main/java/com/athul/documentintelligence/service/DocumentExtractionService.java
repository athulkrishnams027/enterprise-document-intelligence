package com.athul.documentintelligence.service;

import com.athul.documentintelligence.enums.SupportedDocumentType;
import com.athul.documentintelligence.exception.BadRequestException;
import com.athul.documentintelligence.model.ExtractedSection;
import com.athul.documentintelligence.model.ExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts normalized text and structural provenance metadata (page numbers for PDFs,
 * logical sections/paragraphs for DOCX and TXT) without silently discarding metadata.
 */
@Slf4j
@Service
public class DocumentExtractionService {

    public ExtractionResult extract(SupportedDocumentType type, byte[] content, String filename) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("EMPTY_DOCUMENT", "Cannot extract text from an empty document");
        }
        return switch (type) {
            case PDF -> extractPdf(content, filename);
            case DOCX -> extractDocx(content, filename);
            case TXT -> extractTxt(content, filename);
        };
    }

    private ExtractionResult extractPdf(byte[] content, String filename) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new BadRequestException("PASSWORD_PROTECTED_PDF",
                        "Password-protected or encrypted PDFs cannot be processed");
            }
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new BadRequestException("EMPTY_DOCUMENT", "PDF contains zero pages");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<ExtractedSection> sections = new ArrayList<>();
            int nonEmptyPages = 0;

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String rawText = stripper.getText(document);
                String normalized = normalizeText(rawText);
                if (!normalized.isBlank()) {
                    nonEmptyPages++;
                    sections.add(new ExtractedSection(page, "Page " + page, page, normalized));
                }
            }

            if (sections.isEmpty()) {
                throw new BadRequestException("EMPTY_EXTRACTED_TEXT",
                        "PDF contains no extractable text (it may be scanned images without OCR)");
            }

            String summary = String.format("Extracted %d non-empty page(s) out of %d total pages from PDF '%s'",
                    nonEmptyPages, totalPages, filename);
            return new ExtractionResult(totalPages, sections, summary);
        } catch (InvalidPasswordException ex) {
            throw new BadRequestException("PASSWORD_PROTECTED_PDF",
                    "PDF is password-protected and cannot be extracted");
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("Failed to parse PDF '{}': {}", filename, ex.getMessage());
            throw new BadRequestException("INVALID_PDF", "Unable to parse PDF file; file may be corrupted");
        }
    }

    private ExtractionResult extractDocx(byte[] content, String filename) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(content);
             XWPFDocument docx = new XWPFDocument(bais)) {

            List<ExtractedSection> sections = new ArrayList<>();
            String currentHeading = "Document Body";
            StringBuilder buffer = new StringBuilder();
            int sectionOrder = 0;

            for (IBodyElement element : docx.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = normalizeText(paragraph.getText());
                    if (text.isBlank()) {
                        continue;
                    }
                    String style = paragraph.getStyle();
                    boolean isHeading = style != null && style.toLowerCase().contains("heading");
                    if (isHeading) {
                        if (!buffer.isEmpty()) {
                            sections.add(new ExtractedSection(null, currentHeading, ++sectionOrder, buffer.toString().trim()));
                            buffer.setLength(0);
                        }
                        currentHeading = text.length() > 120 ? text.substring(0, 120) : text;
                    } else {
                        if (!buffer.isEmpty()) {
                            buffer.append("\n\n");
                        }
                        buffer.append(text);
                    }
                } else if (element instanceof XWPFTable table) {
                    String tableText = extractTableText(table);
                    if (!tableText.isBlank()) {
                        if (!buffer.isEmpty()) {
                            buffer.append("\n\n");
                        }
                        buffer.append(tableText);
                    }
                }
            }

            if (!buffer.isEmpty()) {
                sections.add(new ExtractedSection(null, currentHeading, ++sectionOrder, buffer.toString().trim()));
            }

            if (sections.isEmpty()) {
                throw new BadRequestException("EMPTY_EXTRACTED_TEXT",
                        "DOCX file contains no extractable text");
            }

            String summary = String.format("Extracted %d logical section(s) from DOCX '%s'",
                    sections.size(), filename);
            return new ExtractionResult(null, sections, summary);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to parse DOCX '{}': {}", filename, ex.getMessage());
            throw new BadRequestException("INVALID_DOCX", "Unable to parse DOCX file; file may be invalid or corrupted");
        }
    }

    private ExtractionResult extractTxt(byte[] content, String filename) {
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new BadRequestException("TEXT_ENCODING_ERROR",
                    "TXT file is not valid UTF-8 encoded text");
        }

        String normalized = normalizeText(decoded);
        if (normalized.isBlank()) {
            throw new BadRequestException("EMPTY_EXTRACTED_TEXT", "TXT file contains only whitespace");
        }

        List<ExtractedSection> sections = List.of(
                new ExtractedSection(null, "Plain Text", 1, normalized)
        );
        String summary = String.format("Extracted %d characters from TXT '%s'", normalized.length(), filename);
        return new ExtractionResult(null, sections, summary);
    }

    private String extractTableText(XWPFTable table) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            String rowText = row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .map(this::normalizeText)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" | "));
            if (!rowText.isBlank()) {
                rows.add(rowText);
            }
        }
        return String.join("\n", rows);
    }

    private String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" +\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
