package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.StorageProperties;
import com.athul.documentintelligence.enums.SupportedDocumentType;
import com.athul.documentintelligence.exception.BadRequestException;
import com.athul.documentintelligence.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates uploaded documents using filename sanitization, size limits,
 * magic-byte inspection, and Apache Tika MIME detection.
 */
@Service
@RequiredArgsConstructor
public class DocumentValidationService {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    public ValidatedDocument validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new BadRequestException("EMPTY_DOCUMENT", "Uploaded document must not be empty");
        }
        if (file.getSize() > storageProperties.maxFileSizeBytes()) {
            throw new DomainException(HttpStatus.valueOf(413), "FILE_TOO_LARGE",
                    "Uploaded file exceeds maximum allowed size of " + storageProperties.maxFileSizeBytes() + " bytes");
        }

        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(sanitizedName);
        SupportedDocumentType docType = SupportedDocumentType.fromExtension(extension)
                .orElseThrow(() -> new BadRequestException(
                        "UNSUPPORTED_FILE_FORMAT",
                        "Unsupported file extension '." + extension + "'. Allowed formats: PDF, DOCX, TXT"
                ));

        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw new BadRequestException("EMPTY_DOCUMENT", "Uploaded document contains no bytes");
        }

        validateContentSignature(docType, content, sanitizedName);
        String checksum = sha256Hex(content);

        return new ValidatedDocument(
                sanitizedName,
                docType,
                docType.getCanonicalMimeType(),
                content.length,
                checksum,
                content
        );
    }

    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("INVALID_FILENAME", "Filename must not be blank");
        }
        String normalized = originalFilename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        baseName = baseName.trim();

        if (baseName.isEmpty() || baseName.contains("..") || baseName.indexOf('\0') >= 0) {
            throw new BadRequestException("INVALID_FILENAME", "Filename contains invalid path characters");
        }
        String cleaned = baseName.replaceAll("[\\p{Cntrl}]", "");
        if (cleaned.isEmpty()) {
            throw new BadRequestException("INVALID_FILENAME", "Filename is invalid after sanitization");
        }
        return cleaned;
    }

    private String extractExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx == filename.length() - 1) {
            throw new BadRequestException("UNSUPPORTED_FILE_FORMAT", "Document must include a supported file extension");
        }
        return filename.substring(dotIdx + 1).toLowerCase();
    }

    private byte[] readBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return is.readAllBytes();
        } catch (IOException ex) {
            throw new BadRequestException("UNREADABLE_FILE", "Failed to read uploaded file content");
        }
    }

    private void validateContentSignature(SupportedDocumentType docType, byte[] content, String filename) {
        String detectedMime = tika.detect(content, filename);
        switch (docType) {
            case PDF -> {
                if (!startsWith(content, PDF_MAGIC) || !docType.getAcceptedDetectedMimeTypes().contains(detectedMime)) {
                    throw new BadRequestException("INVALID_PDF",
                            "File content does not match a valid PDF document (detected: " + detectedMime + ")");
                }
            }
            case DOCX -> {
                boolean zipHeader = startsWith(content, ZIP_MAGIC);
                boolean mimeOk = docType.getAcceptedDetectedMimeTypes().contains(detectedMime)
                        || "application/zip".equals(detectedMime);
                if (!zipHeader || !mimeOk) {
                    throw new BadRequestException("INVALID_DOCX",
                            "File content does not match a valid DOCX document (detected: " + detectedMime + ")");
                }
            }
            case TXT -> {
                if (containsNullByte(content) || !isValidUtf8OrText(content)) {
                    throw new BadRequestException("INVALID_TXT",
                            "TXT file must contain valid UTF-8 plain text");
                }
                if (detectedMime != null && !detectedMime.startsWith("text/")) {
                    throw new BadRequestException("INVALID_TXT",
                            "File content does not match plain text (detected: " + detectedMime + ")");
                }
            }
        }
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsNullByte(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidUtf8OrText(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    public record ValidatedDocument(
            String originalFilename,
            SupportedDocumentType documentType,
            String mimeType,
            long fileSize,
            String checksum,
            byte[] content
    ) {
    }
}
